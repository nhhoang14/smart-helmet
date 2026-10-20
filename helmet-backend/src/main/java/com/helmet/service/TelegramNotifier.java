package com.helmet.service;

import com.helmet.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class TelegramNotifier {
    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    @Value("${helmet.telegram.bot-token:}")
    private String botToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean sendAlert(String chatId, Alert alert) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            return false;
        }

        try {
            String emoji = switch (alert.getType()) {
                case GAS_DANGER -> "☣️🚨 [NGUY HIỂM KHÍ ĐỘC]";
                case FALL -> "⚠️🆘 [PHÁT HIỆN TÉ NGÃ]";
                case SOS -> "🆘🆘 [TÍN HIỆU CẤP CỨU SOS]";
                case GAS_WARNING -> "⚠️ [CẢNH BÁO KHÍ]";
                case OFFLINE -> "🔌 [MẤT KẾT NỐI]";
            };

            StringBuilder sb = new StringBuilder();
            sb.append(emoji).append("\n");
            sb.append("• Thiết bị: ").append(alert.getDeviceId()).append("\n");
            sb.append("• Thông điệp: ").append(alert.getMessage()).append("\n");
            if (alert.getGasPpm() != null) {
                sb.append("• Nồng độ khí: ").append(String.format("%.1f", alert.getGasPpm())).append(" ppm\n");
            }
            if (alert.getLat() != null && alert.getLng() != null) {
                sb.append("• Vị trí: https://maps.google.com/?q=")
                  .append(alert.getLat()).append(",").append(alert.getLng()).append("\n");
            }
            sb.append("• Thời gian: ").append(alert.getCreatedAt()).append("\n");

            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            Map<String, String> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", sb.toString());

            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            log.info("Telegram notification sent for alert {}: {}", alert.getId(), response.getStatusCode());
            return true;
        } catch (Exception e) {
            log.warn("Failed to send Telegram alert: {}", e.getMessage());
            return false;
        }
    }
}
