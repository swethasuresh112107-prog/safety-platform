package com.guardianvoice.controller;

import com.guardianvoice.dto.SettingsRequest;
import com.guardianvoice.dto.SettingsResponse;
import com.guardianvoice.security.CurrentUser;
import com.guardianvoice.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<SettingsResponse> get() {
        return ResponseEntity.ok(settingsService.get(CurrentUser.id()));
    }

    @PutMapping
    public ResponseEntity<SettingsResponse> update(@Valid @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(settingsService.update(CurrentUser.id(), request));
    }
}
