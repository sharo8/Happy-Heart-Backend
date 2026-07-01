package com.happyhearts.bootstrap;

import com.happyhearts.enums.DeviceApprovalStatus;
import com.happyhearts.enums.DeviceStatus;
import com.happyhearts.model.RfidDevice;
import com.happyhearts.repository.RfidDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RfidDeviceSeeder implements CommandLineRunner {

    private final RfidDeviceRepository rfidDeviceRepository;

    @Value("${app.init-data:true}")
    private boolean initData;

    @Override
    @Transactional
    public void run(String... args) {
        if (!initData) {
            return;
        }
        if (rfidDeviceRepository.findByDeviceId("ESP32_DOOR_01").isEmpty()) {
            rfidDeviceRepository.save(RfidDevice.builder()
                    .deviceId("ESP32_DOOR_01")
                    .location("Main Entrance")
                    .description("Front door RFID reader")
                    .status(DeviceStatus.offline)
                    .approvalStatus(DeviceApprovalStatus.ACTIVE)
                    .build());
            log.info("Default RFID device seeded: ESP32_DOOR_01");
        }
    }
}
