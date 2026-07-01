package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.enums.Role;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.GmPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gm-permissions")
@RequiredArgsConstructor
public class GmPermissionController {

    private final GmPermissionService gmPermissionService;

    @GetMapping("/pages")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPageDefinitions() {
        return ResponseEntity.ok(ApiResponse.ok("ok", gmPermissionService.getPageDefinitions()));
    }

    @GetMapping("/managers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getManagers() {
        return ResponseEntity.ok(ApiResponse.ok("ok", gmPermissionService.getManagersWithPermissions()));
    }

    @GetMapping("/my-permissions")
    @PreAuthorize("hasAnyRole('GENERAL_MANAGER_PEDAGOGIQUE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyPermissions(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            List<Map<String, Object>> all = gmPermissionService.getPageDefinitions().stream()
                    .map(p -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Boolean> crud = (Map<String, Boolean>) p.get("supportsCrud");
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("pageKey", p.get("pageKey"));
                        row.put("canView", true);
                        row.put("canCreate", crud.getOrDefault("create", true));
                        row.put("canUpdate", crud.getOrDefault("update", true));
                        row.put("canDelete", crud.getOrDefault("delete", true));
                        row.put("canExport", crud.getOrDefault("export", true));
                        return row;
                    })
                    .toList();
            return ResponseEntity.ok(ApiResponse.ok("ok", all));
        }
        return ResponseEntity.ok(ApiResponse.ok("ok", gmPermissionService.getMyPermissions(principal.getId())));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> savePermissions(
            @PathVariable UUID userId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) body.get("permissions");
        if (permissions == null || permissions.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("error.gm.permissions.required"));
        }
        gmPermissionService.savePermissions(userId, permissions, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok("ok", Map.of(
                "saved", true,
                "userId", userId.toString(),
                "permissionsCount", permissions.size()
        )));
    }
}
