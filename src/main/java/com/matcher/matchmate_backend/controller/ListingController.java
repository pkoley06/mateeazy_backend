package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.request.ListingCreateRequest;
import com.matcher.matchmate_backend.dto.request.ListingUpdateRequest;
import com.matcher.matchmate_backend.dto.response.ListingResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.ListingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody ListingCreateRequest request
    ) {
        ListingResponse response = listingService.create(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(listingService.getById(id));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ListingResponse>> getMine(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(listingService.getMine(principal.getUserId()));
    }

    /** Owner-only edit. PUT and PATCH behave identically (partial update). */
    @PutMapping("/{id}")
    public ResponseEntity<ListingResponse> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody ListingUpdateRequest request
    ) {
        return ResponseEntity.ok(listingService.update(principal.getUserId(), id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ListingResponse> patch(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody ListingUpdateRequest request
    ) {
        return ResponseEntity.ok(listingService.update(principal.getUserId(), id, request));
    }

    /** Owner-only delete. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        listingService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
