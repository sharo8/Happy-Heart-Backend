package com.happyhearts.controller;



import com.happyhearts.dto.ApiResponse;

import com.happyhearts.dto.request.Esp32BatchOfflineSyncRequest;

import com.happyhearts.dto.request.Esp32HeartbeatRequest;

import com.happyhearts.dto.request.Esp32OfflineSyncRequest;

import com.happyhearts.dto.request.Esp32RfidScanRequest;

import com.happyhearts.dto.response.OfflineSyncResponse;

import com.happyhearts.dto.response.RfidScanResponse;

import com.happyhearts.service.RfidScanService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;



@RestController

@RequestMapping("/api/attendance")

@RequiredArgsConstructor

public class Esp32AttendanceController {



    private final RfidScanService rfidScanService;



    @PostMapping("/scan")

    @PreAuthorize("hasAuthority('RFID_READER')")

    public ResponseEntity<ApiResponse<RfidScanResponse>> scan(@Valid @RequestBody Esp32RfidScanRequest request) {

        RfidScanResponse data = rfidScanService.scanFromEsp32(request);

        return ResponseEntity.ok(ApiResponse.ok("Scan accepted", data));

    }



    @PostMapping("/sync-offline")

    @PreAuthorize("hasAuthority('RFID_READER')")

    public ResponseEntity<ApiResponse<OfflineSyncResponse>> syncOffline(@Valid @RequestBody Esp32OfflineSyncRequest request) {

        OfflineSyncResponse data = rfidScanService.syncOneFromEsp32(request);

        return ResponseEntity.ok(ApiResponse.ok("Offline scan synced", data));

    }



    @PostMapping("/sync-offline-batch")

    @PreAuthorize("hasAuthority('RFID_READER')")

    public ResponseEntity<ApiResponse<OfflineSyncResponse>> syncOfflineBatch(

            @Valid @RequestBody Esp32BatchOfflineSyncRequest request

    ) {

        OfflineSyncResponse data = rfidScanService.batchSyncFromEsp32(request);

        return ResponseEntity.ok(ApiResponse.ok("Offline batch synced", data));

    }



    @PostMapping("/heartbeat")

    @PreAuthorize("hasAuthority('RFID_READER')")

    public ResponseEntity<ApiResponse<Void>> heartbeat(@Valid @RequestBody Esp32HeartbeatRequest request) {

        rfidScanService.heartbeatFromEsp32(request);

        return ResponseEntity.ok(ApiResponse.ok("Heartbeat accepted", null));

    }

}

