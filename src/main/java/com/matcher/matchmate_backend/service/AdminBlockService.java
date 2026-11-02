package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.AdminBlockResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.UserBlock;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Admin-console view of user-to-user blocks. */
@Service
public class AdminBlockService {

    private final UserBlockRepository blockRepository;
    private final AppUserRepository userRepository;

    public AdminBlockService(UserBlockRepository blockRepository, AppUserRepository userRepository) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<AdminBlockResponse> list(int page, int size) {
        Page<UserBlock> result = blockRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));

        Set<UUID> ids = new HashSet<>();
        for (UserBlock b : result.getContent()) {
            ids.add(b.getBlockerId());
            ids.add(b.getBlockedId());
        }
        Map<UUID, String> names = new HashMap<>();
        for (AppUser u : userRepository.findAllById(ids)) {
            names.put(u.getId(), u.getFullName());
        }

        Map<UUID, Long> totals = new HashMap<>();
        return PageResponse.of(result, b -> AdminBlockResponse.builder()
                .blockerId(b.getBlockerId())
                .blockerName(names.getOrDefault(b.getBlockerId(), "Unknown"))
                .blockedId(b.getBlockedId())
                .blockedName(names.getOrDefault(b.getBlockedId(), "Unknown"))
                .blockedUserTotalBlocks(totals.computeIfAbsent(
                        b.getBlockedId(), blockRepository::countByBlockedId))
                .createdAt(b.getCreatedAt())
                .build());
    }

    @Transactional
    public void remove(UUID blockerId, UUID blockedId) {
        if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Block not found");
        }
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}
