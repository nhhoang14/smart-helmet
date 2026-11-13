package com.helmet.repository;

import com.helmet.model.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {

    List<Telemetry> findByDeviceUidAndLogTimeBetweenOrderByLogTimeAsc(
            String deviceUid, Instant start, Instant end);

    List<Telemetry> findTop60ByDeviceUidOrderByLogTimeDesc(String deviceUid);

    Optional<Telemetry> findTop1ByDeviceUidOrderByLogTimeDesc(String deviceUid);

    default List<Telemetry> findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String deviceId, Instant start, Instant end) {
        return findByDeviceUidAndLogTimeBetweenOrderByLogTimeAsc(deviceId, start, end);
    }

    default List<Telemetry> findTop60ByDeviceIdOrderByRecordedAtDesc(String deviceId) {
        return findTop60ByDeviceUidOrderByLogTimeDesc(deviceId);
    }

    default Optional<Telemetry> findTop1ByDeviceIdOrderByRecordedAtDesc(String deviceId) {
        return findTop1ByDeviceUidOrderByLogTimeDesc(deviceId);
    }
}
