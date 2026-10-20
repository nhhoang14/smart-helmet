package com.helmet.repository;

import com.helmet.model.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    List<Telemetry> findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(String deviceId, Instant from, Instant to);
    List<Telemetry> findTop60ByDeviceIdOrderByRecordedAtDesc(String deviceId);
    Optional<Telemetry> findTop1ByDeviceIdOrderByRecordedAtDesc(String deviceId);
}
