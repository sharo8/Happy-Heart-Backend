package com.happyhearts.service;

import com.happyhearts.dto.request.UpdateProfileRequest;
import com.happyhearts.dto.response.MeResponse;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.User;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MeResponse getMe(UserPrincipal principal) {
        User user = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        return toResponse(user);
    }

    @Transactional
    public MeResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        user.setFirstName(trimToNull(request.getFirstName()));
        user.setLastName(trimToNull(request.getLastName()));
        if (request.getProfilePhotoUrl() != null) {
            user.setProfilePhotoUrl(trimToNull(request.getProfilePhotoUrl()));
        }
        user = userRepository.save(user);
        return toResponse(user);
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static MeResponse toResponse(User user) {
        return MeResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .preferredLanguage(user.getPreferredLanguage())
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                .branchCode(user.getBranch() != null ? user.getBranch().getCode() : null)
                .branchName(user.getBranch() != null ? user.getBranch().getName() : null)
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .build();
    }
}
