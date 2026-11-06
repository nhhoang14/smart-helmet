package com.helmet.repository;

import com.helmet.model.Alert;
import com.helmet.model.AlertStatus;
import com.helmet.model.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByDeviceUidAndStatusAndType(String deviceUid, AlertStatus status, AlertType type);

    List<Alert> findByDeviceUidOrderByOccurredAtDesc(String deviceUid);

    Page<Alert> findByTypeAndStatus(AlertType type, AlertStatus status, Pageable pageable);

    Page<Alert> findByType(AlertType type, Pageable pageable);

    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    default List<Alert> findByDeviceIdAndStatusAndType(String deviceId, AlertStatus status, AlertType type) {
        return findByDeviceUidAndStatusAndType(deviceId, status, type);
    }

    long countByStatus(AlertStatus status);

    default List<Alert> findByDeviceIdOrderByCreatedAtDesc(String deviceId) {
        return findByDeviceUidOrderByOccurredAtDesc(deviceId);
    }
}
