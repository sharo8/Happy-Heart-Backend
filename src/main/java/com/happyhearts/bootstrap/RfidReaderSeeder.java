package com.happyhearts.bootstrap;

import com.happyhearts.enums.ReaderType;
import com.happyhearts.model.Branch;
import com.happyhearts.model.RfidReader;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.RfidReaderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RfidReaderSeeder implements CommandLineRunner {

    private final RfidReaderRepository rfidReaderRepository;
    private final BranchRepository branchRepository;

    @Value("${app.init-data:true}")
    private boolean initData;

    @Override
    @Transactional
    public void run(String... args) {
        if (!initData) {
            return;
        }
        if (rfidReaderRepository.existsByReaderCode("ESP32_DOOR_01")) {
            return;
        }
        Branch branch = branchRepository.findAll().stream().findFirst().orElse(null);
        if (branch == null) {
            log.warn("No branch found — create a branch before ESP32 scans will work");
            return;
        }
        rfidReaderRepository.save(RfidReader.builder()
                .branch(branch)
                .readerCode("ESP32_DOOR_01")
                .readerType(ReaderType.ENTRY)
                .locationDescription("Main Entrance")
                .online(false)
                .build());
        log.info("Default RFID reader seeded: ESP32_DOOR_01 on branch {}", branch.getCode());
    }
}
