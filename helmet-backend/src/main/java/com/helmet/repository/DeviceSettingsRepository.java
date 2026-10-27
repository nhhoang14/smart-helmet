package com.helmet.repository;

import com.helmet.model.DeviceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceSettingsRepository extends JpaRepository<DeviceSettings, String> {
}
