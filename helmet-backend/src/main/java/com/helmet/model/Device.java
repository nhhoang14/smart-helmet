package com.helmet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "devices", indexes = {
    @Index(name = "idx_devices_uid", columnList = "device_uid", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_uid", nullable = false, unique = true, length = 100)
    private String deviceUid;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "worker_name", length = 255)
    private String workerName;

    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(name = "is_online", nullable = false)
    private boolean isOnline = false;

    @Builder.Default
    @Column(name = "is_worn", nullable = false)
    private boolean isWorn = false;

    @Builder.Default
    @Column(name = "buzzer_on", nullable = false)
    private boolean buzzerOn = false;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lng")
    private Double lastLng;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public String getUid() {
        return deviceUid != null ? deviceUid : String.valueOf(id);
    }
}
