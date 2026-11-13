#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <TinyGPSPlus.h>
#include "config.h"
#include "mq135_sensor.h"
#include "alarm_controller.h"

// Khởi tạo các cảm biến và ngoại vi
MQ135Sensor mq135(PIN_MQ135_ANALOG, PIN_MQ135_DIGITAL);
AlarmController alarmCtrl(PIN_BUZZER);
TinyGPSPlus gps;
HardwareSerial gpsSerial(2);
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

// Trạng thái hệ thống
SystemState currentState = STATE_WARMING_UP;
unsigned long lastSerialPrint = 0;
unsigned long lastTelemetryPublish = 0;
unsigned long lastMqttAttempt = 0;
unsigned long lastWifiAttempt = 0;
unsigned long lastWifiGeoAttempt = 0;
const unsigned long SERIAL_PRINT_INTERVAL = 1000;

// Vị trí định vị qua Wi-Fi (indoor fallback)
double wifiLat = DEFAULT_WIFI_LAT;
double wifiLng = DEFAULT_WIFI_LNG;
bool hasWifiGeoLocation = false;

// Trạng thái công tắc (KW11 / SOS) và debounce
bool lastSwitchState = false;
unsigned long lastDebounceTime = 0;
const unsigned long DEBOUNCE_DELAY = 50;

// Hàng đợi lưu trữ cảnh báo khi mất mạng
struct PendingEvent {
    char type[20];
    char message[64];
    double lat;
    double lng;
    char source[10];
    bool hasLocation;
    uint32_t timestamp;
};

PendingEvent eventQueue[EVENT_QUEUE_CAPACITY];
uint8_t queueHead = 0;
uint8_t queueSize = 0;

// ==========================================
// ĐỊNH VỊ: ƯU TIÊN GPS NEO-6M, DỰ PHÒNG WI-FI
// ==========================================

void readGps() {
    while (gpsSerial.available() > 0) {
        gps.encode(gpsSerial.read());
    }
}

bool isOutdoorGpsValid() {
    return gps.location.isValid() && 
           (gps.location.age() < 10000) && 
           (gps.location.lat() != 0.0);
}

void getCurrentLocation(double &lat, double &lng, String &source, bool &isFix) {
    if (isOutdoorGpsValid()) {
        lat = gps.location.lat();
        lng = gps.location.lng();
        source = "gps";   // Ngoài trời: ưu tiên GPS NEO-6M
        isFix = true;
    } else {
        lat = wifiLat;
        lng = wifiLng;
        source = "wifi";  // Trong nhà / không bắt được GPS: dùng vị trí Wi-Fi
        isFix = false;
    }
}

void fetchWifiGeolocation() {
    if (WiFi.status() != WL_CONNECTED) return;
    
    // Thử truy vấn tọa độ IP nếu có internet
    HTTPClient http;
    http.setTimeout(3000);
    if (http.begin("http://ip-api.com/json/?fields=status,lat,lon")) {
        int httpCode = http.GET();
        if (httpCode == HTTP_CODE_OK) {
            String payload = http.getString();
            StaticJsonDocument<256> doc;
            DeserializationError err = deserializeJson(doc, payload);
            if (!err && doc["status"] == "success") {
                wifiLat = doc["lat"].as<double>();
                wifiLng = doc["lon"].as<double>();
                hasWifiGeoLocation = true;
                Serial.printf("[GEO] Lấy tọa độ dựa theo Wi-Fi thành công: lat=%.6f, lng=%.6f\n", wifiLat, wifiLng);
            }
        }
        http.end();
    }
}

// ==========================================
// QUẢN LÝ SỰ KIỆN & MQTT
// ==========================================

bool publishAlertEvent(const char* type, const char* msgText) {
    if (!mqttClient.connected()) return false;

    double lat, lng;
    String source;
    bool isFix;
    getCurrentLocation(lat, lng, source, isFix);

    StaticJsonDocument<384> doc;
    doc["type"] = type;
    doc["alert_type"] = type;
    doc["message"] = msgText;
    doc["value"] = mq135.getPpm();
    doc["gas"] = mq135.getPpm();
    doc["lat"] = lat;
    doc["lng"] = lng;
    doc["location_source"] = source;
    doc["ts"] = millis() / 1000UL;

    JsonObject gpsObj = doc.createNestedObject("gps");
    gpsObj["fix"] = isFix;
    gpsObj["source"] = source;
    gpsObj["latitude"] = lat;
    gpsObj["longitude"] = lng;
    gpsObj["satellites"] = isOutdoorGpsValid() ? gps.satellites.value() : 0;
    gpsObj["age_ms"] = isOutdoorGpsValid() ? gps.location.age() : 0;

    String payload;
    serializeJson(doc, payload);

    bool pub1 = mqttClient.publish(TOPIC_EVENT, payload.c_str(), true);
    bool pub2 = mqttClient.publish(TOPIC_ALERT, payload.c_str(), true);
    return pub1 || pub2;
}

void enqueueEvent(const char* type, const char* msgText) {
    PendingEvent event;
    strncpy(event.type, type, sizeof(event.type) - 1);
    event.type[sizeof(event.type) - 1] = '\0';
    strncpy(event.message, msgText, sizeof(event.message) - 1);
    event.message[sizeof(event.message) - 1] = '\0';

    double lat, lng;
    String source;
    bool isFix;
    getCurrentLocation(lat, lng, source, isFix);
    event.lat = lat;
    event.lng = lng;
    strncpy(event.source, source.c_str(), sizeof(event.source) - 1);
    event.source[sizeof(event.source) - 1] = '\0';
    event.hasLocation = true;
    event.timestamp = millis() / 1000UL;

    if (queueSize < EVENT_QUEUE_CAPACITY) {
        eventQueue[(queueHead + queueSize) % EVENT_QUEUE_CAPACITY] = event;
        queueSize++;
    }
}

void flushEventQueue() {
    while (queueSize > 0) {
        PendingEvent &ev = eventQueue[queueHead];
        if (!publishAlertEvent(ev.type, ev.message)) return;
        queueHead = (queueHead + 1) % EVENT_QUEUE_CAPACITY;
        queueSize--;
    }
}

void publishTelemetry() {
    if (!mqttClient.connected()) return;

    double lat, lng;
    String source;
    bool isFix;
    getCurrentLocation(lat, lng, source, isFix);

    StaticJsonDocument<384> doc;
    doc["gas"] = mq135.getPpm();
    doc["adc"] = (int)mq135.getFilteredADC();
    doc["voltage"] = mq135.getVoltage();
    doc["worn"] = true;
    doc["fall"] = false;
    doc["lat"] = lat;
    doc["lng"] = lng;
    doc["location_source"] = source;
    doc["ts"] = millis() / 1000UL;

    JsonObject gpsObj = doc.createNestedObject("gps");
    gpsObj["fix"] = isFix;
    gpsObj["source"] = source;
    gpsObj["latitude"] = lat;
    gpsObj["longitude"] = lng;
    gpsObj["satellites"] = isOutdoorGpsValid() ? gps.satellites.value() : 0;
    gpsObj["age_ms"] = isOutdoorGpsValid() ? gps.location.age() : 0;

    JsonObject sensorsObj = doc.createNestedObject("sensors");
    sensorsObj["gas_ppm"] = mq135.getPpm();
    sensorsObj["adc"] = (int)mq135.getFilteredADC();
    sensorsObj["voltage"] = mq135.getVoltage();

    String payload;
    serializeJson(doc, payload);
    mqttClient.publish(TOPIC_TELEMETRY, payload.c_str());
}

// ==========================================
// XỬ LÝ LỆNH TỪ WEB BACKEND (MQTT COMMAND)
// ==========================================

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
    StaticJsonDocument<256> doc;
    DeserializationError err = deserializeJson(doc, payload, length);
    if (err) return;

    Serial.print(F("[MQTT CMD] Nhận lệnh từ Backend: "));
    serializeJson(doc, Serial);
    Serial.println();

    if (doc.containsKey("buzzer")) {
        bool buzzerOn = doc["buzzer"].as<bool>();
        if (buzzerOn) {
            alarmCtrl.setState(STATE_DANGER);
            Serial.println(F("[CMD] Bật còi báo động theo lệnh Web."));
        } else {
            alarmCtrl.setState(STATE_SAFE);
            Serial.println(F("[CMD] Tắt còi theo lệnh Web."));
        }
    }
}

// ==========================================
// XỬ LÝ CÔNG TẮC HÀNH TRÌNH KW11 / NÚT SOS
// ==========================================

bool isSwitchPressedRaw() {
    // Chân công tắc nối GND, cấu hình INPUT_PULLUP (khi ấn là LOW)
    // Hỗ trợ cả GPIO4 (KW11-3Z-2) và GPIO27 (nút SOS)
    return (digitalRead(PIN_TOUCH_WORN) == LOW) || (digitalRead(PIN_SOS_BUTTON) == LOW);
}

void onSwitchPressed() {
    Serial.println();
    Serial.println(F("=================================================="));
    Serial.println(F("🔔 [SWITCH] CÔNG TẮC ĐƯỢC ẤN -> KÍCH HOẠT SOS!"));
    Serial.println(F("=================================================="));

    // 1. Phản hồi âm thanh còi (bíp xác nhận cho người đội mũ)
    alarmCtrl.triggerBeep(250);

    // 2. Lấy vị trí: ưu tiên GPS NEO-6M, nếu trong nhà thì lấy Wi-Fi
    double lat, lng;
    String source;
    bool isFix;
    getCurrentLocation(lat, lng, source, isFix);

    Serial.printf("[SWITCH] Tọa độ: lat=%.6f, lng=%.6f | Nguồn định vị: %s\n",
                  lat, lng, (source == "gps" ? "GPS NEO-6M (Ngoài trời)" : "Wi-Fi (Trong nhà)"));

    // 3. Gửi sự kiện SOS khẩn cấp về backend
    const char* alertMsg = "Nút khẩn cấp SOS trên mũ đã được bấm!";
    if (!publishAlertEvent("SOS", alertMsg)) {
        enqueueEvent("SOS", alertMsg);
    }

    // 4. Cập nhật ngay bản tin telemetry
    publishTelemetry();
}

void checkSwitch() {
    bool reading = isSwitchPressedRaw();
    unsigned long now = millis();

    if (reading != lastSwitchState) {
        if ((now - lastDebounceTime) > DEBOUNCE_DELAY) {
            lastSwitchState = reading;
            lastDebounceTime = now;

            if (reading) {
                // Nhấn công tắc
                onSwitchPressed();
            }
        }
    }
}

// ==========================================
// KẾT NỐI MẠNG & MQTT
// ==========================================

void connectNetwork() {
    unsigned long now = millis();

    // Kết nối Wi-Fi
    if (WiFi.status() != WL_CONNECTED) {
        if (now - lastWifiAttempt < MQTT_RECONNECT_INTERVAL_MS) return;
        lastWifiAttempt = now;
        Serial.printf("[WIFI] Đang kết nối tới %s...\n", WIFI_SSID);
        WiFi.mode(WIFI_STA);
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        return;
    }

    // Sau khi kết nối Wi-Fi, lấy tọa độ dựa theo Wi-Fi (nếu chưa có)
    if (!hasWifiGeoLocation || (now - lastWifiGeoAttempt > 600000UL)) {
        lastWifiGeoAttempt = now;
        fetchWifiGeolocation();
    }

    // Kết nối MQTT Broker
    if (mqttClient.connected()) return;
    if (now - lastMqttAttempt < MQTT_RECONNECT_INTERVAL_MS) return;
    lastMqttAttempt = now;

    String clientId = String("helmet-") + DEVICE_ID + "-" + String((uint32_t)ESP.getEfuseMac(), HEX);
    Serial.printf("[MQTT] Kết nối tới Broker %s:%d với ClientId: %s\n", MQTT_BROKER, MQTT_PORT, clientId.c_str());

    bool connected;
    if (strlen(MQTT_USERNAME) > 0) {
        connected = mqttClient.connect(clientId.c_str(), MQTT_USERNAME, MQTT_PASSWORD,
                                       TOPIC_STATUS, 0, true, "{\"online\":false}");
    } else {
        connected = mqttClient.connect(clientId.c_str(),
                                       TOPIC_STATUS, 0, true, "{\"online\":false}");
    }

    if (connected) {
        Serial.println(F("[MQTT] Kết nối thành công!"));
        mqttClient.publish(TOPIC_STATUS, "{\"online\":true}", true);
        mqttClient.subscribe(TOPIC_COMMAND);
        flushEventQueue();
        publishTelemetry();
    } else {
        Serial.printf("[MQTT] Kết nối thất bại, rc=%d. Thử lại sau...\n", mqttClient.state());
    }
}

// ==========================================
// SETUP & LOOP CHÍNH
// ==========================================

void setup() {
    Serial.begin(115200);
    delay(1000);

    Serial.println();
    Serial.println(F("=================================================="));
    Serial.println(F("         SMART HELMET - MŨ BẢO HỘ THÔNG MINH      "));
    Serial.println(F("  Khí độc MQ-135 | Còi cảnh báo | GPS & Định vị Wi-Fi "));
    Serial.println(F("=================================================="));

    // Cấu hình chân công tắc hành trình KW11 và nút SOS
    pinMode(PIN_TOUCH_WORN, INPUT_PULLUP);
    pinMode(PIN_SOS_BUTTON, INPUT_PULLUP);

    // Khởi tạo cảm biến và còi
    mq135.begin();
    alarmCtrl.begin();
    gpsSerial.begin(GPS_BAUD_RATE, SERIAL_8N1, PIN_GPS_RX, PIN_GPS_TX);

    // Cấu hình MQTT
    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    mqttClient.setCallback(onMqttMessage);
    mqttClient.setBufferSize(512);
    WiFi.setAutoReconnect(true);

    // Bíp test còi lúc khởi động
    alarmCtrl.triggerBeep(100);
    delay(150);
    alarmCtrl.triggerBeep(100);

    Serial.println(F("[INFO] Bắt đầu khởi động hệ thống và sấy cảm biến MQ-135..."));
}

void loop() {
    // 1. Đọc luồng GPS NMEA từ UART2
    readGps();

    // 2. Cập nhật cảm biến khí MQ-135 và còi
    mq135.update();
    alarmCtrl.update();

    // 3. Quét kiểm tra công tắc (KW11 / SOS)
    checkSwitch();

    // 4. Duy trì kết nối Wi-Fi và MQTT
    connectNetwork();
    if (mqttClient.connected()) {
        mqttClient.loop();
        flushEventQueue();
    }

    // 5. Đánh giá ngưỡng nồng độ khí độc & điều khiển còi
    SystemState previousState = currentState;

    if (!mq135.isWarmedUp()) {
        currentState = STATE_WARMING_UP;
    } else {
        float currentPpm = mq135.getPpm();
        float currentADC = mq135.getFilteredADC();
        bool digitalAlarm = mq135.isDigitalTriggered();

        if (currentPpm >= GAS_DANGER_PPM_DEFAULT || currentADC >= ADC_THRESHOLD_DANGER || digitalAlarm) {
            currentState = STATE_DANGER;
        } else if (currentPpm >= GAS_WARN_PPM_DEFAULT || currentADC >= ADC_THRESHOLD_WARNING) {
            currentState = STATE_WARNING;
        } else if (currentPpm <= (GAS_WARN_PPM_DEFAULT - GAS_HYSTERESIS_PPM) &&
                   currentADC <= (ADC_THRESHOLD_WARNING - ADC_HYSTERESIS)) {
            currentState = STATE_SAFE;
        }
    }

    // Nếu trạng thái cảnh báo thay đổi -> điều khiển còi và gửi bản tin
    if (currentState != previousState) {
        alarmCtrl.setState(currentState);

        if (currentState == STATE_DANGER) {
            Serial.println(F("[ALARM] NỒNG ĐỘ KHÍ VƯỢT NGƯỠNG NGUY HIỂM! BẬT CÒI BÁO ĐỘNG!"));
            const char* msg = "Cảnh báo nồng độ khí độc nguy hiểm phát hiện từ mũ!";
            if (!publishAlertEvent("GAS_DANGER", msg)) {
                enqueueEvent("GAS_DANGER", msg);
            }
        } else if (currentState == STATE_WARNING) {
            Serial.println(F("[WARN] Nồng độ khí ở mức cảnh báo!"));
            const char* msg = "Cảnh báo nồng độ khí độc tăng cao!";
            if (!publishAlertEvent("GAS_WARNING", msg)) {
                enqueueEvent("GAS_WARNING", msg);
            }
        } else if (currentState == STATE_SAFE) {
            Serial.println(F("[INFO] Nồng độ khí đã trở về an toàn. Tắt còi."));
        }
    }

    // 6. Định kỳ gửi Telemetry lên Backend (mỗi 3 giây)
    unsigned long now = millis();
    if (now - lastTelemetryPublish >= TELEMETRY_INTERVAL_MS) {
        lastTelemetryPublish = now;
        publishTelemetry();
    }

    // 7. Định kỳ in log trạng thái ra Serial Monitor (mỗi 1 giây)
    if (now - lastSerialPrint >= SERIAL_PRINT_INTERVAL) {
        lastSerialPrint = now;

        double lat, lng;
        String source;
        bool isFix;
        getCurrentLocation(lat, lng, source, isFix);

        Serial.print(F("["));
        Serial.print(now / 1000);
        Serial.print(F("s] Trạng thái: "));
        switch (currentState) {
            case STATE_WARMING_UP: Serial.print(F("ĐANG SẤY CẢM BIẾN")); break;
            case STATE_SAFE:       Serial.print(F("AN TOÀN")); break;
            case STATE_WARNING:    Serial.print(F("CẢNH BÁO")); break;
            case STATE_DANGER:     Serial.print(F("NGUY HIỂM (CÒI KÊU)")); break;
            default:               Serial.print(F("UNKNOWN")); break;
        }

        if (!mq135.isWarmedUp()) {
            Serial.print(F(" | Còn sấy: "));
            Serial.print(mq135.getRemainingWarmupTime());
            Serial.println(F("s..."));
        } else {
            Serial.print(F(" | Khí: "));
            Serial.print(mq135.getPpm(), 1);
            Serial.print(F(" ppm (ADC: "));
            Serial.print((int)mq135.getFilteredADC());
            Serial.print(F(", "));
            Serial.print(mq135.getVoltage(), 2);
            Serial.print(F("V) | Vị trí ("));
            Serial.print(source == "gps" ? F("GPS NEO-6M") : F("Wi-Fi"));
            Serial.print(F("): "));
            Serial.print(lat, 6);
            Serial.print(F(", "));
            Serial.print(lng, 6);
            if (source == "gps") {
                Serial.print(F(" ("));
                Serial.print(gps.satellites.value());
                Serial.print(F(" vệ tinh)"));
            }
            Serial.print(F(" | Công tắc: "));
            Serial.print(isSwitchPressedRaw() ? F("ẤN") : F("NHẢ"));
            Serial.println();
        }
    }
}
