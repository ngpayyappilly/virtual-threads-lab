package com.cloudwalkers.virtualthreadslab.post;

import java.net.URI;

import com.cloudwalkers.virtualthreadslab.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "Posts")
class PostController {

	private final PostService service;

	PostController(PostService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List posts (paginated, newest first), optionally filtered by author")
	PageResponse<PostResponse> list(@RequestParam(required = false) Long authorId,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return service.list(authorId, pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a post by id")
	PostResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	@Operation(summary = "Create a post")
	ResponseEntity<PostResponse> create(@Valid @RequestBody PostCreateRequest request) {
		PostResponse created = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit a post's content")
	PostResponse update(@PathVariable Long id, @Valid @RequestBody PostUpdateRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a post along with its comments")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
