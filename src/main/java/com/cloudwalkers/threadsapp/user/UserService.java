package com.cloudwalkers.threadsapp.user;

import com.cloudwalkers.threadsapp.common.ConflictException;
import com.cloudwalkers.threadsapp.common.PageResponse;
import com.cloudwalkers.threadsapp.common.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	private final UserRepository users;

	UserService(UserRepository users) {
		this.users = users;
	}

	public PageResponse<UserResponse> list(Pageable pageable) {
		return PageResponse.of(users.findAll(pageable), UserResponse::from);
	}

	public UserResponse get(Long id) {
		return UserResponse.from(find(id));
	}

	/** Used by other modules that need the entity itself. */
	public User find(Long id) {
		return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
	}

	@Transactional
	public UserResponse create(UserRequest request) {
		if (users.existsByUsernameIgnoreCase(request.username())) {
			log.warn("Rejected user create: username '{}' already taken", request.username());
			throw new ConflictException("Username '%s' is already taken".formatted(request.username()));
		}
		if (users.existsByEmailIgnoreCase(request.email())) {
			throw new ConflictException("Email '%s' is already registered".formatted(request.email()));
		}
		User user = users.save(new User(request.username(), request.email(), request.displayName(), request.bio()));
		log.info("Created user id={} username={}", user.getId(), user.getUsername());
		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse update(Long id, UserRequest request) {
		User user = find(id);
		if (users.existsByUsernameIgnoreCaseAndIdNot(request.username(), id)) {
			throw new ConflictException("Username '%s' is already taken".formatted(request.username()));
		}
		if (users.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
			throw new ConflictException("Email '%s' is already registered".formatted(request.email()));
		}
		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setDisplayName(request.displayName());
		user.setBio(request.bio());
		log.info("Updated user id={}", id);
		return UserResponse.from(users.saveAndFlush(user));
	}

	/** Deletes the user; their posts and comments are removed by FK cascade. */
	@Transactional
	public void delete(Long id) {
		users.delete(find(id));
		log.info("Deleted user id={} (posts and comments cascade)", id);
	}

}
