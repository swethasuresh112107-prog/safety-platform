package com.guardianvoice.controller;

import com.guardianvoice.dto.LiveLocationSessionResponse;
import com.guardianvoice.dto.LiveLocationShareResponse;
import com.guardianvoice.dto.LiveLocationStartRequest;
import com.guardianvoice.dto.LiveLocationUpdateRequest;
import com.guardianvoice.security.CurrentUser;
import com.guardianvoice.service.LiveLocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
public class LiveLocationController {

    private final LiveLocationService liveLocationService;

    public LiveLocationController(LiveLocationService liveLocationService) {
        this.liveLocationService = liveLocationService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<LiveLocationSessionResponse> start(@RequestBody LiveLocationStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(liveLocationService.start(CurrentUser.id(), request));
    }

    @PutMapping("/sessions/{id}")
    public ResponseEntity<LiveLocationSessionResponse> update(@PathVariable Long id,
                                                                @RequestBody LiveLocationUpdateRequest request) {
        return ResponseEntity.ok(liveLocationService.update(CurrentUser.id(), id, request));
    }

    @PutMapping("/sessions/{id}/end")
    public ResponseEntity<LiveLocationSessionResponse> end(@PathVariable Long id) {
        return ResponseEntity.ok(liveLocationService.end(CurrentUser.id(), id));
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<LiveLocationSessionResponse> active() {
        return ResponseEntity.ok(liveLocationService.active(CurrentUser.id()));
    }

    /** Public: no auth, reached only via the unguessable token from a share link. */
    @GetMapping("/share/{token}")
    public ResponseEntity<LiveLocationShareResponse> viewShare(@PathVariable String token) {
        return ResponseEntity.ok(liveLocationService.viewByShareToken(token));
    }
}
