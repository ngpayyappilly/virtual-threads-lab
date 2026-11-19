package com.cloudwalkers.threadsapp.user;

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
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
class UserController {

	private final UserService service;

	UserController(UserService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List users (paginated)")
	PageResponse<UserResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return service.list(pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a user by id")
	UserResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	@Operation(summary = "Create a user")
	ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
		UserResponse created = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace a user")
	UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a user along with their posts and comments")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
