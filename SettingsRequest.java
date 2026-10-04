package com.guardianvoice.dto;

import jakarta.validation.constraints.NotBlank;

public record SettingsRequest(
    @NotBlank String sosKeyword,
    @NotBlank String fakeCallKeyword,
    @NotBlank String callerName,
    boolean listenEnabled,
    boolean trackingEnabled
) {}
