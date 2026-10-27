package com.helmet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "device_commands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 32)
    private String deviceId;

    @Column(name = "command_type", nullable = false, length = 20)
    private String commandType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = "SENT";

    @Column(name = "created_by")
    private Long createdBy;

    @Builder.Default
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();
}
