package com.helmet.service;

import com.helmet.dto.LocationMessage;
import com.helmet.dto.WifiScanMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Định vị khu vực bằng Wi-Fi fingerprint (kNN), cùng thuật toán với train.py đã thử:
 *  - Bỏ BSSID "locally administered" (hotspot điện thoại, SSID ảo).
 *  - AP không thấy hoặc yếu hơn floor-dbm được coi là floor-dbm.
 *  - k láng giềng gần nhất, trọng số 1/khoảng cách.
 *  - Làm mượt theo đa số của smoothing-window lần quét gần nhất.
 *
 * File huấn luyện: CSV "ts,label,bssid,rssi" do tools/collect_wifi.py tạo.
 * label phải là mã khu vực trên sơ đồ dashboard (phong_301, hanh_lang).
 */
@Service
public class WifiFingerprintService {
    private static final Logger log = LoggerFactory.getLogger(WifiFingerprintService.class);

    @Value("${helmet.wifi-fingerprint.enabled:true}")
    private boolean enabled;

    @Value("${helmet.wifi-fingerprint.train-file:data/wifi_train.csv}")
    private String trainFile;

    @Value("${helmet.wifi-fingerprint.k:5}")
    private int k;

    @Value("${helmet.wifi-fingerprint.floor-dbm:-90}")
    private double floorDbm;

    @Value("${helmet.wifi-fingerprint.unknown-distance:0}")
    private double unknownDistance;

    @Value("${helmet.wifi-fingerprint.smoothing-window:3}")
    private int smoothingWindow;

    private final LocationService locationService;

    private volatile Model model = Model.EMPTY;
    private final Map<String, Deque<String>> recentZones = new ConcurrentHashMap<>();

    public WifiFingerprintService(LocationService locationService) {
        this.locationService = locationService;
    }

    private record Sample(String label, double[] vector) {}

    private record Model(List<String> bssids, Map<String, Integer> index, List<Sample> samples,
                         Map<String, Integer> scansPerLabel) {
        static final Model EMPTY = new Model(List.of(), Map.of(), List.of(), Map.of());
    }

    /** Kết quả phân loại một lần quét. */
    public record Result(String zone, String rawZone, double confidence, double nearestDistance, int knownAps) {}

    @PostConstruct
    public void init() {
        if (enabled) reload();
    }

    /** Đọc lại file huấn luyện (gọi sau khi thu thêm dữ liệu, không cần khởi động lại backend). */
    public synchronized Map<String, Object> reload() {
        Path path = Path.of(trainFile).toAbsolutePath();
        if (!Files.exists(path)) {
            model = Model.EMPTY;
            log.warn("[WiFi-FP] Chưa có file huấn luyện {} -> vị trí sẽ là 'unknown'.", path);
            return status();
        }

        Map<String, Map<String, Integer>> scans = new LinkedHashMap<>();
        Map<String, String> scanLabels = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                String label = parts[1].trim();
                String bssid = parts[2].trim().toUpperCase(Locale.ROOT);
                int rssi;
                try {
                    rssi = Integer.parseInt(parts[3].trim());
                } catch (NumberFormatException e) {
                    continue; // dòng tiêu đề hoặc dòng hỏng
                }
                if (label.isEmpty() || !isUsableBssid(bssid)) continue;
                String key = label + "|" + parts[0].trim();
                scans.computeIfAbsent(key, x -> new HashMap<>()).put(bssid, rssi);
                scanLabels.put(key, label);
            }
        } catch (IOException e) {
            log.error("[WiFi-FP] Không đọc được {}: {}", path, e.getMessage());
            return status();
        }

        TreeSet<String> allBssids = new TreeSet<>();
        scans.values().forEach(m -> allBssids.addAll(m.keySet()));
        List<String> bssids = new ArrayList<>(allBssids);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < bssids.size(); i++) index.put(bssids.get(i), i);

        List<Sample> samples = new ArrayList<>();
        Map<String, Integer> perLabel = new TreeMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : scans.entrySet()) {
            double[] v = new double[bssids.size()];
            Arrays.fill(v, floorDbm);
            e.getValue().forEach((b, r) -> v[index.get(b)] = Math.max(r, floorDbm));
            String label = scanLabels.get(e.getKey());
            samples.add(new Sample(label, v));
            perLabel.merge(label, 1, Integer::sum);
        }

        model = new Model(List.copyOf(bssids), Map.copyOf(index), List.copyOf(samples), Map.copyOf(perLabel));
        recentZones.clear();
        log.info("[WiFi-FP] Đã nạp {} lần quét, {} BSSID, số lần quét theo khu vực: {}",
                samples.size(), bssids.size(), perLabel);
        return status();
    }

    public Map<String, Object> status() {
        Model m = model;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("enabled", enabled);
        s.put("train_file", Path.of(trainFile).toAbsolutePath().toString());
        s.put("scans", m.samples().size());
        s.put("bssids", m.bssids().size());
        s.put("scans_per_zone", m.scansPerLabel());
        s.put("k", k);
        s.put("floor_dbm", floorDbm);
        s.put("unknown_distance", unknownDistance);
        s.put("smoothing_window", smoothingWindow);
        return s;
    }

    /** Phân loại một lần quét rồi cập nhật vị trí của mũ. Trả về null nếu chưa có dữ liệu huấn luyện. */
    public Result processScan(String deviceUid, WifiScanMessage msg) {
        if (!enabled || msg.getScan() == null) return null;
        Model m = model;
        if (m.samples().isEmpty()) {
            log.debug("[WiFi-FP] Bỏ qua scan của {}: chưa có dữ liệu huấn luyện.", deviceUid);
            return null;
        }

        Result raw = classify(m, msg.getScan());
        String smoothed = smooth(deviceUid, raw.rawZone());
        Result result = new Result(smoothed, raw.rawZone(), raw.confidence(), raw.nearestDistance(), raw.knownAps());
        log.debug("[WiFi-FP] {} -> {} (tức thời {}, tin cậy {}, khoảng cách {}, {} AP đã biết)",
                deviceUid, smoothed, raw.rawZone(), String.format("%.2f", raw.confidence()),
                String.format("%.1f", raw.nearestDistance()), raw.knownAps());

        LocationMessage loc = new LocationMessage();
        loc.setZone(smoothed);
        loc.setSource("wifi");
        loc.setConfidence(raw.confidence());
        locationService.processLocation(deviceUid, loc);
        return result;
    }

    private Result classify(Model m, Map<String, Integer> scan) {
        double[] x = new double[m.bssids().size()];
        Arrays.fill(x, floorDbm);
        int known = 0;
        for (Map.Entry<String, Integer> e : scan.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            String bssid = e.getKey().trim().toUpperCase(Locale.ROOT);
            Integer i = m.index().get(bssid);
            if (i != null && isUsableBssid(bssid)) {
                x[i] = Math.max(e.getValue(), floorDbm);
                known++;
            }
        }
        if (known == 0) {
            return new Result(LocationMessage.UNKNOWN_ZONE, LocationMessage.UNKNOWN_ZONE, 0, Double.NaN, 0);
        }

        int n = m.samples().size();
        double[] dist = new double[n];
        Integer[] order = new Integer[n];
        for (int s = 0; s < n; s++) {
            double[] v = m.samples().get(s).vector();
            double sum = 0;
            for (int j = 0; j < v.length; j++) {
                double d = x[j] - v[j];
                sum += d * d;
            }
            dist[s] = Math.sqrt(sum);
            order[s] = s;
        }
        Arrays.sort(order, Comparator.comparingDouble(s -> dist[s]));

        Map<String, Double> votes = new HashMap<>();
        double total = 0;
        for (int r = 0; r < Math.min(k, n); r++) {
            int s = order[r];
            double w = 1.0 / (dist[s] + 1e-6);
            votes.merge(m.samples().get(s).label(), w, Double::sum);
            total += w;
        }
        Map.Entry<String, Double> best = Collections.max(votes.entrySet(), Map.Entry.comparingByValue());
        double nearest = dist[order[0]];
        String zone = (unknownDistance > 0 && nearest > unknownDistance) ? LocationMessage.UNKNOWN_ZONE : best.getKey();
        return new Result(zone, zone, best.getValue() / total, nearest, known);
    }

    /** Đa số trong cửa sổ gần nhất; hòa thì lấy kết quả mới nhất. */
    private String smooth(String deviceUid, String zone) {
        int window = Math.max(1, smoothingWindow);
        Deque<String> q = recentZones.computeIfAbsent(deviceUid, x -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(zone);
            while (q.size() > window) q.removeFirst();
            Map<String, Integer> counts = new HashMap<>();
            for (String z : q) counts.merge(z, 1, Integer::sum);
            String best = zone;
            int bestCount = counts.get(zone);
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestCount) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            return best;
        }
    }

    /** BSSID có bit "locally administered" (ký tự thứ 2 là 2, 6, A, E) thường là hotspot/SSID ảo. */
    static boolean isUsableBssid(String bssid) {
        if (bssid == null || bssid.length() < 2) return false;
        try {
            return (Integer.parseInt(bssid.substring(0, 2), 16) & 0x02) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
