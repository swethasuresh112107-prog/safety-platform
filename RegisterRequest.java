package com.guardianvoice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    String phone,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password
) {}
