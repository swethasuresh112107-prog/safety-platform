package com.guardianvoice.dto;

import jakarta.validation.constraints.NotBlank;

public record ContactRequest(
    @NotBlank String name,
    @NotBlank String phone,
    String relation,
    boolean primary,
    boolean liveLocationRecipient
) {}
