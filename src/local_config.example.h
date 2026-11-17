#pragma once
// Copy to src/local_config.h; never commit real credentials.
#define HELMET_WIFI_SSID "YOUR_WIFI_SSID"
#define HELMET_WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
#define HELMET_MQTT_HOST "192.168.1.100"
#define HELMET_MQTT_PORT 1883
#define HELMET_MQTT_USER ""
#define HELMET_MQTT_PASSWORD ""
#define HELMET_DEVICE_ID "HELMET_001"
#define HELMET_ENABLE_GAS 1
#define HELMET_ENABLE_GPS 1
#define HELMET_ENABLE_IP_GEO 1
#define HELMET_ENABLE_WEAR 1
// Optional dedicated SOS button GPIO32 to GND; GPIO27 always acknowledges/mutes.
#define HELMET_ENABLE_SOS 0
#define HELMET_BUZZER_PASSIVE 1
// gas-detection pinout; override to match existing wiring if needed.
#define HELMET_WEAR_PIN 14
#define HELMET_GPS_RX 26
#define HELMET_GPS_TX 25
#define HELMET_SOS_PIN 32
#define HELMET_MQ135_DIVIDER_GAIN (34.0f / 22.0f)
// #define HELMET_GAS_DIGITAL_PIN 35
