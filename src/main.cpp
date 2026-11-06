#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <TinyGPSPlus.h>
#include "config.h"
#include "mq135_sensor.h"
#include "alarm_controller.h"

MQ135Sensor mq135(PIN_MQ135_ANALOG, PIN_MQ135_DIGITAL);
AlarmController alarmCtrl(PIN_BUZZER);
TinyGPSPlus gps;
HardwareSerial gpsSerial(2);
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

SystemState currentState = STATE_WARMING_UP;
unsigned long lastSerialPrint = 0;
unsigned long lastTelemetryPublish = 0;
unsigned long lastMqttAttempt = 0;
unsigned long lastWifiAttempt = 0;
const unsigned long SERIAL_PRINT_INTERVAL = 1000;

struct PendingEvent {
    char type[16];
    double lat;
    double lng;
    bool hasLocation;
    uint32_t timestamp;
};

PendingEvent eventQueue[EVENT_QUEUE_CAPACITY];
uint8_t queueHead = 0;
uint8_t queueSize = 0;

void printVisualBar(float adcValue, float maxADC = 4095.0f, uint8_t barWidth = 30) {
    uint8_t filled = (uint8_t)((adcValue / maxADC) * barWidth);
    if (filled > barWidth) filled = barWidth;

    Serial.print("[");
    for (uint8_t i = 0; i < barWidth; i++) {
        if (i < filled) {
            Serial.print(i > (barWidth * 0.6) ? "!" : "#");
        } else {
            Serial.print("-");
        }
    }
    Serial.print("]");
}

const char* getStateString(SystemState state) {
    switch (state) {
        case STATE_WARMING_UP: return "ĐANG KHỞI ĐỘNG (SẤY SENSOR)";
        case STATE_SAFE:       return "AN TOÀN (SAFE)";
        case STATE_WARNING:    return "CẢNH BÁO (WARNING)";
        case STATE_DANGER:     return "NGUY HIỂM (DANGER)";
        default:               return "UNKNOWN";
    }
}

void readGps() {
    while (gpsSerial.available() > 0) {
        gps.encode(gpsSerial.read());
    }
}

template <typename Document>
void addLocation(Document& document) {
    if (gps.location.isValid() && gps.location.age() < 10000) {
        document["lat"] = gps.location.lat();
        document["lng"] = gps.location.lng();
    }
}

bool publishEvent(const PendingEvent& event) {
    if (!mqttClient.connected()) return false;

    StaticJsonDocument<192> document;
    document["type"] = event.type;
    document["ts"] = event.timestamp;
    if (event.hasLocation) {
        document["lat"] = event.lat;
        document["lng"] = event.lng;
    } else {
        addLocation(document);
    }

    String payload;
    serializeJson(document, payload);
    return mqttClient.publish("helmet/" DEVICE_ID "/event", payload.c_str());
}

void enqueueEvent(const char* type) {
    PendingEvent event;
    strncpy(event.type, type, sizeof(event.type) - 1);
    event.type[sizeof(event.type) - 1] = '\0';
    event.hasLocation = gps.location.isValid() && gps.location.age() < 10000;
    event.lat = event.hasLocation ? gps.location.lat() : 0.0;
    event.lng = event.hasLocation ? gps.location.lng() : 0.0;
    event.timestamp = millis() / 1000UL;

    if (queueSize == EVENT_QUEUE_CAPACITY) {
        // Không loại bỏ DANGER đang chờ để ưu tiên các cảnh báo an toàn.
        if (strcmp(event.type, "GAS_DANGER") == 0) {
            for (uint8_t i = 0; i < queueSize; i++) {
                uint8_t index = (queueHead + i) % EVENT_QUEUE_CAPACITY;
                if (strcmp(eventQueue[index].type, "GAS_DANGER") != 0) {
                    eventQueue[index] = event;
                    return;
                }
            }
        }
        return;
    }

    eventQueue[(queueHead + queueSize) % EVENT_QUEUE_CAPACITY] = event;
    queueSize++;
}

void flushEventQueue() {
    while (queueSize > 0) {
        if (!publishEvent(eventQueue[queueHead])) return;
        queueHead = (queueHead + 1) % EVENT_QUEUE_CAPACITY;
        queueSize--;
    }
}

void publishTelemetry() {
    if (!mqttClient.connected()) return;

    StaticJsonDocument<192> document;
    document["gas"] = mq135.getFilteredADC();
    document["worn"] = true;
    document["fall"] = false;
    document["ts"] = millis() / 1000UL;
    addLocation(document);

    String payload;
    serializeJson(document, payload);
    mqttClient.publish("helmet/" DEVICE_ID "/telemetry", payload.c_str());
}

void connectNetwork() {
    if (WiFi.status() != WL_CONNECTED) {
        unsigned long now = millis();
        if (now - lastWifiAttempt < MQTT_RECONNECT_INTERVAL_MS) return;
        lastWifiAttempt = now;
        WiFi.mode(WIFI_STA);
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        return;
    }

    if (mqttClient.connected()) return;
    unsigned long now = millis();
    if (now - lastMqttAttempt < MQTT_RECONNECT_INTERVAL_MS) return;
    lastMqttAttempt = now;

    String clientId = String("helmet-") + DEVICE_ID + "-" +
                      String((uint32_t)ESP.getEfuseMac(), HEX);
    bool connected;
    if (strlen(MQTT_USERNAME) > 0) {
        connected = mqttClient.connect(clientId.c_str(),
                                       MQTT_USERNAME, MQTT_PASSWORD,
                                       "helmet/" DEVICE_ID "/status", 0, true,
                                       "{\"online\":false}");
    } else {
        connected = mqttClient.connect(clientId.c_str(),
                                       "helmet/" DEVICE_ID "/status", 0, true,
                                       "{\"online\":false}");
    }
    if (connected) {
        mqttClient.publish("helmet/" DEVICE_ID "/status", "{\"online\":true}", true);
        flushEventQueue();
    }
}

void setup() {
    Serial.begin(115200);
    delay(1000);

    Serial.println();
    Serial.println(F("=================================================="));
    Serial.println(F("   HỆ THỐNG CẢNH BÁO KHÍ GAS & KHÔNG KHÍ ESP32    "));
    Serial.println(F("              Cảm biến: MQ-135                    "));
    Serial.println(F("=================================================="));

    mq135.begin();
    alarmCtrl.begin();
    gpsSerial.begin(GPS_BAUD_RATE, SERIAL_8N1, PIN_GPS_RX, PIN_GPS_TX);
    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    mqttClient.setBufferSize(256);
    WiFi.setAutoReconnect(true);

    alarmCtrl.triggerBeep(80);
    delay(150);
    alarmCtrl.triggerBeep(80);

    Serial.println(F("[INFO] Bắt đầu quá trình sấy cảm biến (Warm-up)..."));
}

void loop() {
    readGps();
    mq135.update();
    alarmCtrl.update();
    connectNetwork();
    if (mqttClient.connected()) {
        mqttClient.loop();
        flushEventQueue();
    }

    SystemState previousState = currentState;

    if (!mq135.isWarmedUp()) {
        currentState = STATE_WARMING_UP;
    } else {
        float currentADC = mq135.getFilteredADC();
        bool digitalAlarm = mq135.isDigitalTriggered();

        if (currentADC >= ADC_THRESHOLD_DANGER || digitalAlarm) {
            currentState = STATE_DANGER;
        } else if (currentADC >= ADC_THRESHOLD_WARNING) {
            currentState = STATE_WARNING;
        } else if (currentADC <= (ADC_THRESHOLD_WARNING - ADC_HYSTERESIS)) {
            currentState = STATE_SAFE;
        }
    }

    if (currentState != previousState) {
        alarmCtrl.setState(currentState);
        if (currentState == STATE_DANGER) {
            PendingEvent dangerEvent;
            strncpy(dangerEvent.type, "GAS_DANGER", sizeof(dangerEvent.type) - 1);
            dangerEvent.type[sizeof(dangerEvent.type) - 1] = '\0';
            dangerEvent.hasLocation = gps.location.isValid() && gps.location.age() < 10000;
            dangerEvent.lat = dangerEvent.hasLocation ? gps.location.lat() : 0.0;
            dangerEvent.lng = dangerEvent.hasLocation ? gps.location.lng() : 0.0;
            dangerEvent.timestamp = millis() / 1000UL;
            if (!publishEvent(dangerEvent)) {
                enqueueEvent("GAS_DANGER");
            }
        }
    }

    unsigned long now = millis();
    if (now - lastTelemetryPublish >= TELEMETRY_INTERVAL_MS) {
        lastTelemetryPublish = now;
        publishTelemetry();
    }
    if (now - lastSerialPrint >= SERIAL_PRINT_INTERVAL) {
        lastSerialPrint = now;

        float filteredADC = mq135.getFilteredADC();
        float voltage = mq135.getVoltage();
        int rawADC = mq135.getRawADC();

        Serial.print(F("["));
        Serial.print(now / 1000);
        Serial.print(F("s] State: "));
        Serial.print(getStateString(currentState));

        if (!mq135.isWarmedUp()) {
            Serial.print(F(" | Còn sấy: "));
            Serial.print(mq135.getRemainingWarmupTime());
            Serial.println(F(" giây..."));
        } else {
            Serial.print(F(" | ADC Raw: "));
            Serial.print(rawADC);
            Serial.print(F(" | ADC Lọc: "));
            Serial.print((int)filteredADC);
            Serial.print(F(" | Vout: "));
            Serial.print(voltage, 2);
            Serial.print(F("V | D0: "));
            Serial.print(mq135.isDigitalTriggered() ? "TRIG" : "IDLE");
            Serial.print(F(" "));
            printVisualBar(filteredADC);
            Serial.println();
        }
    }
}
