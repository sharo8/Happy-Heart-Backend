package com.happyhearts.controller;

import com.happyhearts.enums.ReaderType;
import com.happyhearts.service.Esp32AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final Esp32AttendanceService esp32AttendanceService;

    @GetMapping
    public ResponseEntity<?> list() {
        List<Map<String, Object>> devices = esp32AttendanceService.listDevices();
        return ResponseEntity.ok(Map.of("devices", devices));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> listPending() {
        List<Map<String, Object>> devices = esp32AttendanceService.listPendingDevices();
        return ResponseEntity.ok(Map.of("devices", devices));
    }

    @PutMapping("/{deviceId}/heartbeat")
    public ResponseEntity<?> heartbeat(
            @PathVariable String deviceId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String location = body != null ? (String) body.get("location") : null;
        Integer freeHeap = null;
        if (body != null && body.get("freeHeap") != null) {
            freeHeap = ((Number) body.get("freeHeap")).intValue();
        }
        String approvalStatus = esp32AttendanceService.touchDeviceHeartbeat(deviceId, location, freeHeap);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("serverTime", Instant.now().toString());
        response.put("approvalStatus", approvalStatus);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deviceId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable String deviceId,
            @RequestBody Map<String, String> body
    ) {
        String branchIdRaw = body.get("branchId");
        if (branchIdRaw == null || branchIdRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "branchId required"));
        }
        ReaderType readerType = ReaderType.ENTRY;
        if (body.get("readerType") != null && !body.get("readerType").isBlank()) {
            readerType = ReaderType.valueOf(body.get("readerType").toUpperCase());
        }
        try {
            Map<String, Object> result = esp32AttendanceService.approveDevice(
                    deviceId,
                    UUID.fromString(branchIdRaw),
                    readerType,
                    body.get("location"),
                    body.get("description")
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid branchId"));
        } catch (Exception e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{deviceId}/reject")
    public ResponseEntity<?> reject(@PathVariable String deviceId) {
        try {
            esp32AttendanceService.rejectDevice(deviceId);
            return ResponseEntity.ok(Map.of("rejected", true));
        } catch (Exception e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "deviceId required"));
        }
        try {
            esp32AttendanceService.registerDevice(deviceId, body.get("location"), body.get("description"));
        } catch (Exception e) {
            return ResponseEntity.status(409).body(Map.of("error", "Device already registered"));
        }
        return ResponseEntity.status(201).body(Map.of("created", true));
    }
}
