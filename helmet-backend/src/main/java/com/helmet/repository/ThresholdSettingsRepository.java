package com.helmet.repository;

import com.helmet.model.ThresholdSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThresholdSettingsRepository extends JpaRepository<ThresholdSettings, Long> {
    Optional<ThresholdSettings> findByDeviceUid(String deviceUid);
    boolean existsByDeviceUid(String deviceUid);
}

