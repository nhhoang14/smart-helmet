#include "helmet_services.h"
#include "config.h"
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <TinyGPSPlus.h>
#include <HTTPClient.h>
#include <time.h>

namespace {
portMUX_TYPE snapshotMux = portMUX_INITIALIZER_UNLOCKED;
HelmetSnapshot latest;
bool snapshotChanged = false;
QueueHandle_t events = nullptr, commands = nullptr;
struct Event {
    char type[20], message[128], id[40], relatedFallId[40];
    float value;
    uint32_t uptimeMs;
    double lat, lng;
    bool gpsFix, approximate;
    time_t timestamp;
};
uint32_t eventSequence = 0, bootId;
char lastFallId[40] = "", acknowledgedFallId[40] = "";
WiFiClient transport;
PubSubClient mqtt(transport);
TinyGPSPlus gps;
HardwareSerial gpsPort(2);
// GPS location is shared only as a POD snapshot, never the mutable parser.
bool locationFix = false;
bool locationApproximate = false;
uint32_t locationUpdatedAt = 0;
double locationLat = 0, locationLng = 0;
char telemetryTopic[96], alertTopic[96], statusTopic[96], commandTopic[96];
HelmetSnapshot snapshot() {
    portENTER_CRITICAL(&snapshotMux);
    HelmetSnapshot result = latest;
    portEXIT_CRITICAL(&snapshotMux);
    return result;
}
void location(JsonDocument& doc, bool fix, double lat, double lng, bool approximate = false) {
    const char* source = fix ? "gps" : approximate ? "ip" : "none";
    doc["location_source"] = source;
    doc["location_approximate"] = approximate;
    JsonObject position = doc.createNestedObject("gps");
    position["fix"] = fix;
    position["source"] = source;
    if (fix || approximate) {
        doc["lat"] = lat; doc["lng"] = lng;
        position["latitude"] = lat; position["longitude"] = lng;
    }
}
void onCommand(char*, byte* payload, unsigned int length) {
    StaticJsonDocument<512> doc;
    if (deserializeJson(doc, payload, length)) return;
    HelmetCommand command;
    if (doc.containsKey("buzzer")) {
        command.type = doc["buzzer"].as<bool>() ? HelmetCommandType::FIND_ON : HelmetCommandType::MUTE;
    } else {
        const char* action = doc["action"] | "";
        if (!strcmp(action, "FIND_HELMET")) {
            const char* state = doc["payload"]["state"] | "OFF";
            command.type = !strcmp(state, "ON") ? HelmetCommandType::FIND_ON : HelmetCommandType::FIND_OFF;
        } else if (!strcmp(action, "SET_THRESHOLD")) {
            command.type = HelmetCommandType::SET_THRESHOLDS;
            command.gasWarn = doc["payload"]["gas_warn"] | NAN;
            command.gasDanger = doc["payload"]["gas_danger"] | NAN;
            command.impactG = doc["payload"]["fall_sensitivity"] | NAN;
        } else if (!strcmp(action, "MUTE") || !strcmp(action, "ACKNOWLEDGE") || !strcmp(action, "ACK_ALERT")) command.type = HelmetCommandType::MUTE;
        else return;
    }
    if (xQueueSend(commands, &command, 0) != pdTRUE) Serial.println("MQTT: command queue full.");
}
bool publishTelemetry() {
    const HelmetSnapshot state = snapshot();
    StaticJsonDocument<1536> doc;
    doc["device_id"] = HELMET_DEVICE_ID;
    doc["firmware"] = "integrated-v2-gas";
    doc["fall_detection"] = "firmware_v2";
    doc["fall_state"] = state.fallState;
    doc["fall"] = state.fall;
    char ackId[40];
    portENTER_CRITICAL(&snapshotMux);
    strlcpy(ackId, acknowledgedFallId, sizeof(ackId));
    portEXIT_CRITICAL(&snapshotMux);
    if (ackId[0]) doc["acknowledged_fall_id"] = ackId;
    doc["worn_known"] = state.wornKnown;
    if (state.wornKnown) doc["worn"] = state.worn;
    doc["gas_ready"] = state.gasReady;
    doc["mpu_ok"] = state.sensorOk;
    doc["calibrated"] = state.calibrated;
    doc["buzzer_on"] = state.buzzerOn;
    doc["buzzer_state"] = state.buzzerOn ? "ON" : "OFF";
    doc["gas_warmup_remaining"] = state.gasWarmupSeconds;
    if (isfinite(state.gasAdc)) doc["adc"] = state.gasAdc;
    if (isfinite(state.gasVoltage)) doc["voltage"] = state.gasVoltage;
    doc["uptime_ms"] = millis();
    const time_t now = time(nullptr);
    if (now > 1700000000) doc["ts"] = static_cast<int64_t>(now);
    JsonObject sensors = doc.createNestedObject("sensors");
    if (state.gasReady && isfinite(state.gasPpm)) {
        doc["gas"] = state.gasPpm;
        sensors["gas_ppm"] = state.gasPpm;
        sensors["adc"] = state.gasAdc;
        sensors["voltage"] = state.gasVoltage;
    }
    if (state.sensorOk && isfinite(state.accelerationG)) sensors["acc_magnitude"] = state.accelerationG;
    if (isfinite(state.tiltDegrees)) sensors["tilt_degrees"] = state.tiltDegrees;
    bool fix, approximate; double lat, lng;
    portENTER_CRITICAL(&snapshotMux);
    fix = locationFix; approximate = locationApproximate; lat = locationLat; lng = locationLng;
    portEXIT_CRITICAL(&snapshotMux);
    location(doc, fix, lat, lng, approximate);
    char payload[1536];
    const size_t size = serializeJson(doc, payload, sizeof(payload));
    const bool sent = mqtt.publish(telemetryTopic, reinterpret_cast<uint8_t*>(payload), size, false);
    if (!sent) Serial.printf("MQTT: telemetry send failed for %s, reconnecting.\n", HELMET_DEVICE_ID);
    return sent;
}
void networkTask(void*) {
    if (HELMET_ENABLE_GPS) gpsPort.begin(GPS_BAUD_RATE, SERIAL_8N1, PIN_GPS_RX, PIN_GPS_TX);
    mqtt.setServer(HELMET_MQTT_HOST, HELMET_MQTT_PORT);
    mqtt.setCallback(onCommand);
    mqtt.setBufferSize(2048);
    mqtt.setSocketTimeout(1);
    mqtt.setKeepAlive(15);
    transport.setTimeout(1);
    // Start once at power-on. Retry later without restarting an in-progress
    // association every loop, and never wait for USB Serial or MQ-135 warmup.
    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);
    WiFi.setHostname(HELMET_DEVICE_ID);
    WiFi.setAutoReconnect(true);
    WiFi.setSleep(false);
    const bool wifiConfigured = strlen(HELMET_WIFI_SSID) != 0;
    const bool brokerConfigured = strlen(HELMET_MQTT_HOST) != 0;
    Serial.printf("NETWORK_BOOT: device=%s wifi=%s broker=%s:%d\n",
                  HELMET_DEVICE_ID, HELMET_WIFI_SSID, HELMET_MQTT_HOST, HELMET_MQTT_PORT);
    if (wifiConfigured) {
        Serial.printf("WIFI: starting connection to %s immediately.\n", HELMET_WIFI_SSID);
        WiFi.begin(HELMET_WIFI_SSID, HELMET_WIFI_PASSWORD);
    } else Serial.println("NETWORK_CONFIG_ERROR: Wi-Fi SSID is missing in src/local_config.h.");
    if (!brokerConfigured) Serial.println("NETWORK_CONFIG_ERROR: MQTT host is missing in src/local_config.h.");
    uint32_t lastWifi = millis(), lastMqtt = millis() - 5000, lastTelemetry = millis();
    uint32_t lastNetworkLog = millis() - 10000;
    uint32_t lastGeo = millis() - 600000;
    bool ipValid = false;
    double ipLat = 0, ipLng = 0;
    bool timeConfigured = false;
    int previousWifiStatus = -1;
    for (;;) {
        const uint32_t now = millis();
        const int wifiStatus = WiFi.status();
        if (wifiStatus != previousWifiStatus) {
            previousWifiStatus = wifiStatus;
            Serial.printf("WIFI: status=%d ip=%s\n", wifiStatus, WiFi.localIP().toString().c_str());
            if (wifiStatus == WL_CONNECTED) {
                // Attempt MQTT immediately after DHCP, including after Wi-Fi recovery.
                lastMqtt = now - 5000;
                Serial.printf("WIFI: connected to %s, IP=%s RSSI=%d dBm.\n",
                              HELMET_WIFI_SSID, WiFi.localIP().toString().c_str(), WiFi.RSSI());
            } else {
                transport.stop();
                if (wifiStatus == WL_NO_SSID_AVAIL) Serial.println("WIFI: network not found; check that the 2.4 GHz network is on.");
                if (wifiStatus == WL_CONNECT_FAILED) Serial.println("WIFI: connection failed; check the password in src/local_config.h.");
            }
        }
        if (now - lastNetworkLog >= 10000) {
            lastNetworkLog = now;
            Serial.printf("NETWORK: device=%s wifi=%s mqtt=%s broker=%s:%d\n", HELMET_DEVICE_ID,
                          wifiStatus == WL_CONNECTED ? "CONNECTED" : "CONNECTING",
                          mqtt.connected() ? "CONNECTED" : "DISCONNECTED", HELMET_MQTT_HOST, HELMET_MQTT_PORT);
        }
        if (HELMET_ENABLE_GPS) while (gpsPort.available()) gps.encode(gpsPort.read());
        const bool fix = HELMET_ENABLE_GPS && gps.location.isValid() && gps.location.age() < 10000;
        portENTER_CRITICAL(&snapshotMux);
        locationFix = fix;
        locationApproximate = !fix && ipValid;
        locationUpdatedAt = millis();
        if (fix) { locationLat = gps.location.lat(); locationLng = gps.location.lng(); }
        else if (ipValid) { locationLat = ipLat; locationLng = ipLng; }
        portEXIT_CRITICAL(&snapshotMux);
        if (wifiConfigured && wifiStatus != WL_CONNECTED && now - lastWifi >= 20000) {
            lastWifi = now;
            Serial.printf("WIFI: retrying %s (2.4 GHz required).\n", HELMET_WIFI_SSID);
            WiFi.disconnect(false, false);
            WiFi.begin(HELMET_WIFI_SSID, HELMET_WIFI_PASSWORD);
        }
        if (wifiStatus == WL_CONNECTED) {
            // Approximate public-IP location, as in the upstream project. Never a GPS fix.
            if (HELMET_ENABLE_IP_GEO && !fix && now - lastGeo >= 600000) {
                lastGeo = now;
                HTTPClient http;
                http.setConnectTimeout(1500);
                http.setTimeout(1500);
                if (http.begin("http://ip-api.com/json/?fields=status,lat,lon")) {
                    if (http.GET() == HTTP_CODE_OK) {
                        StaticJsonDocument<256> response;
                        if (!deserializeJson(response, http.getStream()) && response["status"] == "success") {
                            const double lat = response["lat"], lng = response["lon"];
                            if (isfinite(lat) && isfinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                                ipLat = lat; ipLng = lng; ipValid = true;
                            }
                        }
                    }
                    http.end();
                }
            }
            if (!timeConfigured) {
                configTime(0, 0, "pool.ntp.org", "time.google.com");
                timeConfigured = true;
            }
            if (brokerConfigured && !mqtt.connected() && now - lastMqtt >= 5000) {
                lastMqtt = now;
                Serial.printf("MQTT: connecting to %s:%d\n", HELMET_MQTT_HOST, HELMET_MQTT_PORT);
                char clientId[80];
                snprintf(clientId, sizeof(clientId), "%s-%08lx", HELMET_DEVICE_ID, static_cast<unsigned long>(bootId));
                if (mqtt.connect(clientId, HELMET_MQTT_USER, HELMET_MQTT_PASSWORD,
                                 statusTopic, 1, true, "{\"online\":false}")) {
                    mqtt.subscribe(commandTopic, 1);
                    mqtt.publish(statusTopic, "{\"online\":true}", true);
                    Serial.printf("MQTT: connected. device=%s telemetry=%s\n", HELMET_DEVICE_ID, telemetryTopic);
                    if (!publishTelemetry()) transport.stop();
                    lastTelemetry = now;
                } else Serial.printf("MQTT: connection failed rc=%d; retry in 5s. Check broker, port and firewall.\n", mqtt.state());
            }
            if (mqtt.connected()) {
                mqtt.loop();
                Event event;
                if (xQueuePeek(events, &event, 0) == pdTRUE) {
                    StaticJsonDocument<1024> doc;
                    doc["type"] = event.type; doc["message"] = event.message;
                    doc["event_id"] = event.id; doc["uptime_ms"] = event.uptimeMs;
                    if (event.relatedFallId[0]) doc["related_fall_id"] = event.relatedFallId;
                    if (event.timestamp > 1700000000) doc["ts"] = static_cast<int64_t>(event.timestamp);
                    if (isfinite(event.value)) doc["value"] = event.value;
                    location(doc, event.gpsFix, event.lat, event.lng, event.approximate);
                    char payload[1024];
                    size_t size = serializeJson(doc, payload, sizeof(payload));
                    // One topic, non-retained: no duplicate or stale retained alerts.
                    if (mqtt.publish(alertTopic, reinterpret_cast<uint8_t*>(payload), size, false)) xQueueReceive(events, &event, 0);
                }
                portENTER_CRITICAL(&snapshotMux);
                const bool changed = snapshotChanged;
                snapshotChanged = false;
                portEXIT_CRITICAL(&snapshotMux);
                if (changed || now - lastTelemetry >= TELEMETRY_INTERVAL_MS) {
                    lastTelemetry = now;
                    if (!publishTelemetry()) transport.stop();
                }
            }
        }
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}
}
void beginHelmetServices() {
    bootId = esp_random();
    events = xQueueCreate(EVENT_QUEUE_CAPACITY, sizeof(Event));
    commands = xQueueCreate(8, sizeof(HelmetCommand));
    if (!events || !commands) { Serial.println("SERVICES_FAULT: queue allocation failed."); return; }
    snprintf(telemetryTopic, sizeof(telemetryTopic), "helmet/device/%s/telemetry", HELMET_DEVICE_ID);
    snprintf(alertTopic, sizeof(alertTopic), "helmet/device/%s/alert", HELMET_DEVICE_ID);
    snprintf(statusTopic, sizeof(statusTopic), "helmet/device/%s/status", HELMET_DEVICE_ID);
    snprintf(commandTopic, sizeof(commandTopic), "helmet/device/%s/command", HELMET_DEVICE_ID);
    if (xTaskCreatePinnedToCore(networkTask, "helmet-network", 12288, nullptr, 1, nullptr, 0) != pdPASS)
        Serial.println("SERVICES_FAULT: network task allocation failed.");
    Serial.printf("SERVICES: gas=%d gps=%d wear=%d sos=%d network_configured=%d\n",
                  HELMET_ENABLE_GAS, HELMET_ENABLE_GPS, HELMET_ENABLE_WEAR, HELMET_ENABLE_SOS,
                  strlen(HELMET_WIFI_SSID) && strlen(HELMET_MQTT_HOST) ? 1 : 0);
}
void updateHelmetSnapshot(const HelmetSnapshot& state) {
    portENTER_CRITICAL(&snapshotMux);
    const bool changed = latest.wornKnown != state.wornKnown || latest.worn != state.worn ||
                         latest.fall != state.fall || strcmp(latest.fallState, state.fallState) ||
                         latest.buzzerOn != state.buzzerOn;
    latest = state;
    snapshotChanged |= changed;
    portEXIT_CRITICAL(&snapshotMux);
}
void queueHelmetEvent(const char* type, const char* message, float value) {
    Serial.printf("EVENT: %s %s\n", type, message);
    if (!events) return;
    Event event{};
    strlcpy(event.type, type, sizeof(event.type)); strlcpy(event.message, message, sizeof(event.message));
    snprintf(event.id, sizeof(event.id), "%08lx-%lu", static_cast<unsigned long>(bootId), static_cast<unsigned long>(++eventSequence));
    event.value = value; event.uptimeMs = millis(); event.timestamp = time(nullptr);
    portENTER_CRITICAL(&snapshotMux);
    if (!strcmp(type, "FALL")) strlcpy(lastFallId, event.id, sizeof(lastFallId));
    if (!strcmp(type, "FALL_ACK")) {
        strlcpy(event.relatedFallId, lastFallId, sizeof(event.relatedFallId));
        strlcpy(acknowledgedFallId, lastFallId, sizeof(acknowledgedFallId));
    }
    const bool freshLocation = millis() - locationUpdatedAt < 10000;
    event.gpsFix = locationFix && freshLocation;
    event.approximate = locationApproximate && freshLocation;
    event.lat = locationLat; event.lng = locationLng;
    portEXIT_CRITICAL(&snapshotMux);
    if (xQueueSend(events, &event, 0) != pdTRUE) Serial.println("EVENT_QUEUE_FULL: event dropped.");
}
bool takeHelmetCommand(HelmetCommand& command) {
    return commands && xQueueReceive(commands, &command, 0) == pdTRUE;
}
