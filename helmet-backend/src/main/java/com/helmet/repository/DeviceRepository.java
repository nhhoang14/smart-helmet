package com.helmet.repository;

import com.helmet.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceUidAndDeletedAtIsNull(String deviceUid);
    Optional<Device> findByDeviceUid(String deviceUid);
    boolean existsByDeviceUid(String deviceUid);
    List<Device> findByDeletedAtIsNull();
    List<Device> findByIsOnlineTrueAndDeletedAtIsNull();
    default List<Device> findByIsOnlineTrue() {
        return findByIsOnlineTrueAndDeletedAtIsNull();
    }
}
