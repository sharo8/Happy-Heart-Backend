package com.happyhearts.repository;

import com.happyhearts.enums.DeviceApprovalStatus;
import com.happyhearts.model.RfidDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RfidDeviceRepository extends JpaRepository<RfidDevice, Long> {

    Optional<RfidDevice> findByDeviceId(String deviceId);

    List<RfidDevice> findAllByOrderByLastSeenDesc();

    List<RfidDevice> findByApprovalStatusOrderByLastSeenDesc(DeviceApprovalStatus approvalStatus);
}
