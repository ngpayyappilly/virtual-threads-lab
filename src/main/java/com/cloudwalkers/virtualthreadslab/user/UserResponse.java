package com.cloudwalkers.virtualthreadslab.user;

import java.time.Instant;

public record UserResponse(Long id, String username, String email, String displayName, String bio, Instant createdAt,
		Instant updatedAt) {

	static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
				user.getBio(), user.getCreatedAt(), user.getUpdatedAt());
	}

}
