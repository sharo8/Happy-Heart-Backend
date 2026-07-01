package com.happyhearts.service;

import com.happyhearts.model.AuditLog;
import com.happyhearts.model.Branch;
import com.happyhearts.model.User;
import com.happyhearts.repository.AuditLogRepository;
import com.happyhearts.security.ClientInfoMdcFilter;
import com.happyhearts.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            UserPrincipal actor,
            String action,
            UUID targetId,
            String targetType,
            String detailsJson,
            UUID branchId
    ) {
        String ip = null;
        String ua = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            Object ipAttr = req.getAttribute(ClientInfoMdcFilter.REQ_ATTR_IP);
            ip = ipAttr != null ? ipAttr.toString() : req.getRemoteAddr();
            ua = Optional.ofNullable(req.getHeader("User-Agent")).orElse(null);
        }

        User userRef = null;
        if (actor != null) {
            userRef = User.builder().id(actor.getId()).build();
        }
        Branch branchRef = null;
        if (branchId != null) {
            branchRef = Branch.builder().id(branchId).build();
        }

        AuditLog row = AuditLog.builder()
                .user(userRef)
                .action(action)
                .targetId(targetId)
                .targetType(targetType)
                .details(detailsJson)
                .ipAddress(ip)
                .userAgent(ua)
                .branch(branchRef)
                .build();
        auditLogRepository.save(row);
    }
}
