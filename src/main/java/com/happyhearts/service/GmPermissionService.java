package com.happyhearts.service;

import com.happyhearts.enums.Role;
import com.happyhearts.model.GmPermission;
import com.happyhearts.model.User;
import com.happyhearts.repository.GmPermissionRepository;
import com.happyhearts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GmPermissionService {

    /** Pages that exist in the GM permission matrix (rfid_readers/devices are hidden). */
    public static final List<String> ALL_PAGE_KEYS = List.of(
            "dashboard", "notifications", "evaluations", "messages", "reports", "announcements",
            "rfid_dashboard", "live_feed", "attendance_employees", "attendance_reports",
            "work_schedules", "early_departures", "grace_periods", "excuse_management",
            "branch_analytics", "employees", "calendar", "branches", "branch_leadership",
            "users", "settings", "audit_trail"
    );

    private static final Set<String> LOCKED_FULL_PAGES = Set.of(
            "dashboard", "notifications", "messages", "rfid_dashboard", "live_feed",
            "attendance_employees", "attendance_reports", "branch_analytics",
            "work_schedules", "early_departures", "grace_periods", "excuse_management",
            "reports", "settings", "audit_trail"
    );

    private static final Set<String> LOCKED_VIEW_ONLY_PAGES = Set.of("branches");

    private final GmPermissionRepository gmPermissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPageDefinitions() {
        List<Map<String, Object>> pages = new ArrayList<>();
        pages.add(pageDef("dashboard", "Tableau de bord", false, false, false, false, true, false));
        pages.add(pageDef("notifications", "Notifications", false, false, false, false, true, false));
        pages.add(pageDef("evaluations", "Évaluations", true, true, true, true, false, false));
        pages.add(pageDef("messages", "Messages", true, true, true, true, true, false));
        pages.add(pageDef("reports", "Rapports", false, false, false, true, true, false));
        pages.add(pageDef("announcements", "Annonces", true, true, true, true, false, false));
        pages.add(pageDef("rfid_dashboard", "Tableau CARDID", true, true, true, true, true, false));
        pages.add(pageDef("live_feed", "Flux en direct", false, false, false, false, true, false));
        pages.add(pageDef("attendance_employees", "Employés présence", true, true, true, true, true, false));
        pages.add(pageDef("attendance_reports", "Rapports présence", false, false, false, true, true, false));
        pages.add(pageDef("work_schedules", "Horaires de travail", true, true, true, true, true, false));
        pages.add(pageDef("early_departures", "Départs anticipés", true, true, true, true, true, false));
        pages.add(pageDef("grace_periods", "Périodes de grâce", true, true, true, true, true, false));
        pages.add(pageDef("excuse_management", "Gestion des excuses", true, true, true, true, true, false));
        pages.add(pageDef("branch_analytics", "Analyse par branche", false, false, false, true, true, false));
        pages.add(pageDef("employees", "Employés", true, true, true, true, false, false));
        pages.add(pageDef("calendar", "Calendrier", true, true, true, true, false, false));
        pages.add(pageDef("branches", "Branches", false, false, false, false, false, true));
        pages.add(pageDef("branch_leadership", "Leadership de branche", true, true, true, true, false, false));
        pages.add(pageDef("users", "Utilisateurs", true, true, true, true, false, false));
        pages.add(pageDef("settings", "Paramètres", true, true, true, true, true, false));
        pages.add(pageDef("audit_trail", "Journal d'audit", false, false, false, true, true, false));
        return pages;
    }

    private static Map<String, Object> pageDef(String key, String label,
            boolean create, boolean update, boolean delete, boolean export,
            boolean lockedFull, boolean lockedViewOnly) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pageKey", key);
        row.put("label", label);
        row.put("supportsCrud", Map.of(
                "create", create,
                "update", update,
                "delete", delete,
                "export", export
        ));
        row.put("isLockedFull", lockedFull);
        row.put("isLockedViewOnly", lockedViewOnly);
        return row;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getManagersWithPermissions() {
        List<User> managers = userRepository.findByRoleIn(
                List.of(Role.GENERAL_MANAGER_PEDAGOGIQUE),
                Sort.by("firstName", "lastName")
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : managers) {
            ensureDefaultsForUser(user.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", user.getId().toString());
            row.put("fullName", ((user.getFirstName() != null ? user.getFirstName() : "")
                    + " " + (user.getLastName() != null ? user.getLastName() : "")).trim());
            row.put("email", user.getEmail());
            row.put("status", user.isActive() ? "active" : "inactive");
            row.put("permissions", toPermissionRows(user.getId()));
            result.add(row);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyPermissions(UUID userId) {
        ensureDefaultsForUser(userId);
        return toPermissionRows(userId);
    }

    @Transactional
    public void savePermissions(UUID userId, List<Map<String, Object>> permissions, UUID grantedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("error.user.not.found"));
        if (user.getRole() != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            throw new IllegalArgumentException("error.gm.permissions.not.gmp");
        }
        for (Map<String, Object> perm : permissions) {
            String pageKey = String.valueOf(perm.get("pageKey"));
            if (!ALL_PAGE_KEYS.contains(pageKey)) {
                continue;
            }
            GmPermission entity = gmPermissionRepository.findByUserIdAndPageKey(userId, pageKey)
                    .orElseGet(() -> GmPermission.builder().userId(userId).pageKey(pageKey).build());

            if (entity.isLockedFull() || entity.isLockedViewOnly()) {
                continue;
            }

            entity.setCanView(true);
            boolean canCreate = toBool(perm.get("canCreate"), false);
            boolean canUpdate = toBool(perm.get("canUpdate"), false);
            boolean canDelete = toBool(perm.get("canDelete"), false);
            boolean canExport = toBool(perm.get("canExport"), false);
            if (canCreate) {
                CrudDefaults supported = crudSupportFromPageDefs(pageKey);
                if (supported.update()) canUpdate = true;
                if (supported.delete()) canDelete = true;
                if (supported.export()) canExport = true;
            }
            entity.setCanCreate(canCreate);
            entity.setCanUpdate(canUpdate);
            entity.setCanDelete(canDelete);
            entity.setCanExport(canExport);
            entity.setGrantedBy(grantedBy);
            gmPermissionRepository.save(entity);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefaultsForUser(UUID userId) {
        for (String pageKey : ALL_PAGE_KEYS) {
            Optional<GmPermission> existing = gmPermissionRepository.findByUserIdAndPageKey(userId, pageKey);
            if (existing.isEmpty()) {
                CrudDefaults defaults = defaultCrudForPage(pageKey);
                gmPermissionRepository.save(GmPermission.builder()
                        .userId(userId)
                        .pageKey(pageKey)
                        .canView(true)
                        .canCreate(defaults.create())
                        .canUpdate(defaults.update())
                        .canDelete(defaults.delete())
                        .canExport(defaults.export())
                        .lockedFull(LOCKED_FULL_PAGES.contains(pageKey))
                        .lockedViewOnly(LOCKED_VIEW_ONLY_PAGES.contains(pageKey))
                        .build());
            } else if (LOCKED_FULL_PAGES.contains(pageKey)) {
                GmPermission p = existing.get();
                boolean dirty = false;
                if (!p.isLockedFull()) {
                    p.setLockedFull(true);
                    dirty = true;
                }
                if (!p.isCanCreate() || !p.isCanUpdate() || !p.isCanDelete() || !p.isCanExport()) {
                    p.setCanCreate(true);
                    p.setCanUpdate(true);
                    p.setCanDelete(true);
                    p.setCanExport(true);
                    dirty = true;
                }
                if (dirty) {
                    gmPermissionRepository.save(p);
                }
            }
        }
    }

    private static CrudDefaults defaultCrudForPage(String pageKey) {
        if (LOCKED_FULL_PAGES.contains(pageKey)) {
            return new CrudDefaults(true, true, true, true);
        }
        if (LOCKED_VIEW_ONLY_PAGES.contains(pageKey)) {
            return new CrudDefaults(false, false, false, false);
        }
        return switch (pageKey) {
            case "evaluations" -> new CrudDefaults(true, false, false, true);
            default -> new CrudDefaults(false, false, false, false);
        };
    }

    private CrudDefaults crudSupportFromPageDefs(String pageKey) {
        return getPageDefinitions().stream()
                .filter(p -> pageKey.equals(p.get("pageKey")))
                .findFirst()
                .map(p -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Boolean> sc = (Map<String, Boolean>) p.get("supportsCrud");
                    return new CrudDefaults(
                            Boolean.TRUE.equals(sc.get("create")),
                            Boolean.TRUE.equals(sc.get("update")),
                            Boolean.TRUE.equals(sc.get("delete")),
                            Boolean.TRUE.equals(sc.get("export")));
                })
                .orElseGet(() -> defaultCrudForPage(pageKey));
    }

    private record CrudDefaults(boolean create, boolean update, boolean delete, boolean export) {}

    @Transactional(readOnly = true)
    public boolean isAllowed(UUID userId, String pageKey, String action) {
        Optional<GmPermission> perm = gmPermissionRepository.findByUserIdAndPageKey(userId, pageKey);
        if (perm.isEmpty()) {
            if (LOCKED_FULL_PAGES.contains(pageKey)) {
                return switch (action) {
                    case "view", "create", "update", "delete", "export" -> true;
                    default -> false;
                };
            }
            return "view".equals(action);
        }
        GmPermission p = perm.get();
        if (p.isLockedFull()) {
            return switch (action) {
                case "view", "create", "update", "delete", "export" -> true;
                default -> false;
            };
        }
        if (p.isCanCreate() && !p.isLockedViewOnly() && !"evaluations".equals(pageKey)) {
            return switch (action) {
                case "view", "create", "update", "delete", "export" -> true;
                default -> false;
            };
        }
        return switch (action) {
            case "view" -> p.isCanView();
            case "create" -> p.isCanCreate();
            case "update" -> p.isCanUpdate();
            case "delete" -> p.isCanDelete();
            case "export" -> p.isCanExport();
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public boolean isHttpMutationAllowed(UUID userId, String uri, String method) {
        Optional<MutationTarget> target = resolveMutation(uri, method);
        if (target.isEmpty()) {
            return false;
        }
        return isAllowed(userId, target.get().pageKey(), target.get().action());
    }

    private Optional<MutationTarget> resolveMutation(String uri, String method) {
        String m = method.toUpperCase();
        if ("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m)) {
            return Optional.empty();
        }
        String path = uri == null ? "" : uri;
        if (path.startsWith("/api/v1/employees")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("employees", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("employees", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("employees", "delete"));
        }
        if (path.startsWith("/api/v1/attendance/work-schedules")) {
            if ("PUT".equals(m) || "PATCH".equals(m)) {
                return Optional.of(new MutationTarget("work_schedules", "update"));
            }
        }
        if (path.startsWith("/api/v1/attendance/grace-periods")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("grace_periods", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("grace_periods", "update"));
        }
        if (path.startsWith("/api/v1/attendance/excuses")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("excuse_management", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("excuse_management", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("excuse_management", "delete"));
        }
        if (path.startsWith("/api/v1/calendar") || path.startsWith("/api/v1/calendar-events")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("calendar", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("calendar", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("calendar", "delete"));
        }
        if (path.startsWith("/api/v1/announcements")) {
            if ("POST".equals(m)) {
                if (path.endsWith("/resend")) {
                    return Optional.of(new MutationTarget("announcements", "update"));
                }
                if (path.endsWith("/read") || path.endsWith("/preview-recipient-count")) {
                    return Optional.empty();
                }
                return Optional.of(new MutationTarget("announcements", "create"));
            }
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("announcements", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("announcements", "delete"));
        }
        if (path.startsWith("/api/v1/branches")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("branches", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("branches", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("branches", "delete"));
        }
        if (path.startsWith("/api/v1/branch-leadership") || path.startsWith("/api/v1/branches/") && path.contains("leadership")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("branch_leadership", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("branch_leadership", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("branch_leadership", "delete"));
        }
        if (path.startsWith("/api/v1/users")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("users", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("users", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("users", "delete"));
        }
        if (path.startsWith("/api/v1/feedbacks")) {
            if ("POST".equals(m)) return Optional.of(new MutationTarget("evaluations", "create"));
            if ("PUT".equals(m) || "PATCH".equals(m)) return Optional.of(new MutationTarget("evaluations", "update"));
            if ("DELETE".equals(m)) return Optional.of(new MutationTarget("evaluations", "delete"));
        }
        if (path.startsWith("/api/v1/conversations")) {
            return Optional.empty();
        }
        if (path.startsWith("/api/v1/me")) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private List<Map<String, Object>> toPermissionRows(UUID userId) {
        return gmPermissionRepository.findByUserIdOrderByPageKeyAsc(userId).stream()
                .filter(p -> ALL_PAGE_KEYS.contains(p.getPageKey()))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("pageKey", p.getPageKey());
                    row.put("canView", p.isCanView());
                    row.put("canCreate", p.isCanCreate());
                    row.put("canUpdate", p.isCanUpdate());
                    row.put("canDelete", p.isCanDelete());
                    row.put("canExport", p.isCanExport());
                    row.put("isLockedFull", p.isLockedFull());
                    row.put("isLockedViewOnly", p.isLockedViewOnly());
                    return row;
                })
                .toList();
    }

    private static boolean toBool(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private record MutationTarget(String pageKey, String action) {}
}
