package com.helmet.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_commands")
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

    @Column(nullable = false, length = 10)
    private String status = "SENT";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    public DeviceCommand() {}

    public DeviceCommand(String deviceId, String commandType, String payload, String status) {
        this.deviceId = deviceId;
        this.commandType = commandType;
        this.payload = payload;
        this.status = status;
        this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
