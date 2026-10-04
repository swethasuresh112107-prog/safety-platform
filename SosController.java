package com.guardianvoice.controller;

import com.guardianvoice.dto.SosRequest;
import com.guardianvoice.dto.SosResponse;
import com.guardianvoice.security.CurrentUser;
import com.guardianvoice.service.SosService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sos")
public class SosController {

    private final SosService sosService;

    public SosController(SosService sosService) {
        this.sosService = sosService;
    }

    @PostMapping
    public ResponseEntity<SosResponse> trigger(@RequestBody SosRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sosService.trigger(CurrentUser.id(), request));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<SosResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(sosService.cancel(CurrentUser.id(), id));
    }

    @GetMapping("/history")
    public ResponseEntity<List<SosResponse>> history() {
        return ResponseEntity.ok(sosService.history(CurrentUser.id()));
    }
}
