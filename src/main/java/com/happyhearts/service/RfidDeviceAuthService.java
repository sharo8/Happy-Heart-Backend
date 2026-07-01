package com.happyhearts.service;

import com.happyhearts.dto.response.DeviceTokenResponse;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.repository.RfidReaderRepository;
import com.happyhearts.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RfidDeviceAuthService {

    private final RfidReaderRepository rfidReaderRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public DeviceTokenResponse mintDeviceToken(String readerCode) {
        if (!rfidReaderRepository.existsByReaderCode(readerCode)) {
            throw new BusinessException("error.reader.not.found");
        }
        return DeviceTokenResponse.builder()
                .deviceId(readerCode)
                .token(jwtTokenProvider.createDeviceAccessToken(readerCode))
                .expiresAt(jwtTokenProvider.deviceTokenExpiresAt())
                .build();
    }
}
