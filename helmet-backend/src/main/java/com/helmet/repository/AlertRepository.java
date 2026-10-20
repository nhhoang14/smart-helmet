package com.helmet.repository;

import com.helmet.model.Alert;
import com.helmet.model.AlertStatus;
import com.helmet.model.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByDeviceIdAndStatusAndType(String deviceId, AlertStatus status, AlertType type);
    Optional<Alert> findTop1ByDeviceIdAndTypeOrderByCreatedAtDesc(String deviceId, AlertType type);

    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);
    Page<Alert> findByTypeOrderByCreatedAtDesc(AlertType type, Pageable pageable);
    Page<Alert> findByStatusAndTypeOrderByCreatedAtDesc(AlertStatus status, AlertType type, Pageable pageable);

    long countByStatus(AlertStatus status);
}
