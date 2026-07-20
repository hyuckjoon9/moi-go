package com.mycom.myapp.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JoinApplicationCreateRequest(@NotBlank String motivation, String experience, String availableTime, String desiredRole) {
	
}
