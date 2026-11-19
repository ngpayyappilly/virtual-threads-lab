package com.cloudwalkers.virtualthreadslab.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRequest(
		@NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9_.]+$",
				message = "must contain only letters, digits, '_' or '.'") String username,
		@NotBlank @Email @Size(max = 255) String email,
		@Size(max = 60) String displayName,
		@Size(max = 160) String bio) {
}
