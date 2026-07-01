package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.RfidReaderRequest;
import com.happyhearts.dto.response.DeviceTokenResponse;
import com.happyhearts.dto.response.RfidReaderResponse;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.RfidDeviceAuthService;
import com.happyhearts.service.RfidReaderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rfid-readers")
@RequiredArgsConstructor
public class RfidReaderController {

    private final RfidReaderService rfidReaderService;
    private final RfidDeviceAuthService rfidDeviceAuthService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<RfidReaderResponse>>> list(@AuthenticationPrincipal UserPrincipal principal) {
        List<RfidReaderResponse> data = rfidReaderService.findAll(principal);
        String msg = messageSource.getMessage("reader.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RfidReaderResponse>> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        RfidReaderResponse data = rfidReaderService.findById(principal, id);
        String msg = messageSource.getMessage("reader.get.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RfidReaderResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RfidReaderRequest request
    ) {
        RfidReaderResponse data = rfidReaderService.create(principal, request);
        String msg = messageSource.getMessage("reader.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RfidReaderResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RfidReaderRequest request
    ) {
        RfidReaderResponse data = rfidReaderService.update(principal, id, request);
        String msg = messageSource.getMessage("reader.update.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        rfidReaderService.delete(principal, id);
        String msg = messageSource.getMessage("reader.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @PostMapping("/device-token")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DeviceTokenResponse>> mintDeviceToken(@RequestParam String readerCode) {
        DeviceTokenResponse data = rfidDeviceAuthService.mintDeviceToken(readerCode);
        String msg = messageSource.getMessage("reader.device.token.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/{id}/heartbeat")
    @PreAuthorize("hasAuthority('RFID_READER')")
    public ResponseEntity<ApiResponse<Void>> heartbeat(@PathVariable UUID id) {
        rfidReaderService.heartbeat(id);
        String msg = messageSource.getMessage("reader.heartbeat.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }
}
