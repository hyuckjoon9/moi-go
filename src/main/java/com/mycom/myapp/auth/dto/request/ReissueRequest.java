package com.mycom.myapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(@NotBlank String refreshToken) {}
