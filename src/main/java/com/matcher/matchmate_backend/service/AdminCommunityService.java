package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.CommunityPostResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.dto.response.UserStatusResponse;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.entity.UserStatus;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import com.matcher.matchmate_backend.repository.CommunityPostRepository;
import com.matcher.matchmate_backend.repository.UserStatusRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AdminCommunityService {

    private final CommunityPostRepository communityPostRepository;
    private final UserStatusRepository userStatusRepository;
    private final CommunityPostService communityPostService;
    private final UserStatusService userStatusService;

    public AdminCommunityService(
            CommunityPostRepository communityPostRepository,
            UserStatusRepository userStatusRepository,
            CommunityPostService communityPostService,
            UserStatusService userStatusService
    ) {
        this.communityPostRepository = communityPostRepository;
        this.userStatusRepository = userStatusRepository;
        this.communityPostService = communityPostService;
        this.userStatusService = userStatusService;
    }

    @Transactional
    public PageResponse<CommunityPostResponse> listPosts(String search, String categoryStr, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        String query = StringUtils.hasText(search) ? search.trim() : null;

        CommunityPostCategory category = null;
        if (StringUtils.hasText(categoryStr)) {
            try {
                category = CommunityPostCategory.valueOf(categoryStr.trim().toUpperCase());
            } catch (Exception ignored) {}
        }

        Page<CommunityPost> result = communityPostRepository.searchForAdmin(
                query,
                category,
                PageRequest.of(safePage, safeSize)
        );

        return PageResponse.of(result, post -> communityPostService.toPostResponse(post, false, null));
    }

    @Transactional
    public void deletePost(UUID id) {
        CommunityPost post = communityPostRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        post.setStatus(CommunityPostStatus.ARCHIVED);
        communityPostRepository.save(post);
    }

    @Transactional
    public PageResponse<UserStatusResponse> listStatuses(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);

        Page<UserStatus> result = userStatusRepository.findByExpiresAtAfterOrderByCreatedAtDesc(
                OffsetDateTime.now(),
                PageRequest.of(safePage, safeSize)
        );

        return PageResponse.of(result, status -> userStatusService.toStatusResponse(status, null));
    }

    @Transactional
    public void deleteStatus(UUID id) {
        UserStatus status = userStatusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status not found"));
        userStatusRepository.delete(status);
    }
}
