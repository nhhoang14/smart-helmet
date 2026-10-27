package com.helmet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "devices")
public class Device {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String zone;

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
}
