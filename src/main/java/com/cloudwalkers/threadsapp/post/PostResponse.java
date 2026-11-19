package com.cloudwalkers.threadsapp.post;

import java.time.Instant;

public record PostResponse(Long id, Long authorId, String authorUsername, String content, long commentCount,
		Instant createdAt, Instant updatedAt) {

	static PostResponse from(Post post) {
		return new PostResponse(post.getId(), post.getAuthor().getId(), post.getAuthor().getUsername(),
				post.getContent(), post.getCommentCount(), post.getCreatedAt(), post.getUpdatedAt());
	}

}
