package com.happyhearts.service;

import com.happyhearts.dto.request.RfidReaderRequest;
import com.happyhearts.dto.response.RfidReaderResponse;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.RfidReader;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.RfidReaderRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RfidReaderService {

    private final RfidReaderRepository rfidReaderRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public List<RfidReaderResponse> findAll(UserPrincipal principal) {
        branchAccessService.assertSuperAdmin(principal);
        return rfidReaderRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RfidReaderResponse findById(UserPrincipal principal, UUID id) {
        branchAccessService.assertSuperAdmin(principal);
        RfidReader reader = rfidReaderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.reader.not.found"));
        return toResponse(reader);
    }

    @Transactional
    public RfidReaderResponse create(UserPrincipal principal, RfidReaderRequest request) {
        branchAccessService.assertSuperAdmin(principal);
        if (rfidReaderRepository.existsByReaderCode(request.getReaderCode())) {
            throw new BusinessException("error.reader.code.exists");
        }
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        RfidReader reader = RfidReader.builder()
                .branch(branch)
                .readerCode(request.getReaderCode())
                .readerType(request.getReaderType())
                .locationDescription(request.getLocationDescription())
                .online(false)
                .build();
        return toResponse(rfidReaderRepository.save(reader));
    }

    @Transactional
    public RfidReaderResponse update(UserPrincipal principal, UUID id, RfidReaderRequest request) {
        branchAccessService.assertSuperAdmin(principal);
        RfidReader reader = rfidReaderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.reader.not.found"));
        rfidReaderRepository.findByReaderCode(request.getReaderCode()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BusinessException("error.reader.code.exists");
            }
        });
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        reader.setBranch(branch);
        reader.setReaderCode(request.getReaderCode());
        reader.setReaderType(request.getReaderType());
        reader.setLocationDescription(request.getLocationDescription());
        return toResponse(rfidReaderRepository.save(reader));
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        branchAccessService.assertSuperAdmin(principal);
        if (!rfidReaderRepository.existsById(id)) {
            throw new ResourceNotFoundException("error.reader.not.found");
        }
        rfidReaderRepository.deleteById(id);
    }

    @Transactional
    public void heartbeat(UUID id) {
        RfidReader reader = rfidReaderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.reader.not.found"));
        reader.setOnline(true);
        reader.setLastSyncAt(Instant.now());
        rfidReaderRepository.save(reader);
    }

    private RfidReaderResponse toResponse(RfidReader r) {
        return RfidReaderResponse.builder()
                .id(r.getId())
                .branchId(r.getBranch().getId())
                .readerCode(r.getReaderCode())
                .readerType(r.getReaderType())
                .locationDescription(r.getLocationDescription())
                .online(r.isOnline())
                .lastSyncAt(r.getLastSyncAt())
                .pendingSyncCount(r.getPendingSyncCount())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
