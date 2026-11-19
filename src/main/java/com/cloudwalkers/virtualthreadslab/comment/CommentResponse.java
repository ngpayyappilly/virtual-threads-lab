package com.cloudwalkers.virtualthreadslab.comment;

import java.time.Instant;

public record CommentResponse(Long id, Long postId, Long authorId, String authorUsername, String content,
		Instant createdAt, Instant updatedAt) {

	static CommentResponse from(Comment comment) {
		return new CommentResponse(comment.getId(), comment.getPost().getId(), comment.getAuthor().getId(),
				comment.getAuthor().getUsername(), comment.getContent(), comment.getCreatedAt(),
				comment.getUpdatedAt());
	}

}
