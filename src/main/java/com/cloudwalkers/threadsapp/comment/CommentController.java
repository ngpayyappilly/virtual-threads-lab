package com.cloudwalkers.threadsapp.comment;

import java.net.URI;

import com.cloudwalkers.threadsapp.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@Tag(name = "Comments")
class CommentController {

	private final CommentService service;

	CommentController(CommentService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List comments on a post (paginated, oldest first)")
	PageResponse<CommentResponse> list(@PathVariable Long postId,
			@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
		return service.list(postId, pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a comment by id")
	CommentResponse get(@PathVariable Long postId, @PathVariable Long id) {
		return service.get(postId, id);
	}

	@PostMapping
	@Operation(summary = "Comment on a post")
	ResponseEntity<CommentResponse> create(@PathVariable Long postId,
			@Valid @RequestBody CommentCreateRequest request) {
		CommentResponse created = service.create(postId, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit a comment")
	CommentResponse update(@PathVariable Long postId, @PathVariable Long id,
			@Valid @RequestBody CommentUpdateRequest request) {
		return service.update(postId, id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a comment")
	ResponseEntity<Void> delete(@PathVariable Long postId, @PathVariable Long id) {
		service.delete(postId, id);
		return ResponseEntity.noContent().build();
	}

}
