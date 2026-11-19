package com.cloudwalkers.virtualthreadslab.system;

import com.cloudwalkers.virtualthreadslab.comment.CommentCreateRequest;
import com.cloudwalkers.virtualthreadslab.comment.CommentService;
import com.cloudwalkers.virtualthreadslab.post.PostCreateRequest;
import com.cloudwalkers.virtualthreadslab.post.PostService;
import com.cloudwalkers.virtualthreadslab.user.UserRequest;
import com.cloudwalkers.virtualthreadslab.user.UserService;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Seeds an empty database so the API has something to return; does nothing if users exist. Disable with
 * {@code app.sample-data.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.sample-data.enabled", havingValue = "true", matchIfMissing = true)
class SampleDataLoader implements CommandLineRunner {

	private final UserService users;

	private final PostService posts;

	private final CommentService comments;

	SampleDataLoader(UserService users, PostService posts, CommentService comments) {
		this.users = users;
		this.posts = posts;
		this.comments = comments;
	}

	@Override
	public void run(String... args) {
		if (users.list(PageRequest.of(0, 1)).totalElements() > 0) {
			return;
		}
		Long alice = users.create(new UserRequest("alice", "alice@example.com", "Alice", "Loves virtual threads")).id();
		Long bob = users.create(new UserRequest("bob", "bob@example.com", "Bob", "Metrics enthusiast")).id();

		Long first = posts.create(new PostCreateRequest(alice, "Hello Threads! First post from the sample app.")).id();
		posts.create(new PostCreateRequest(bob, "Spring Boot + virtual threads + Micrometer = nice."));

		comments.create(first, new CommentCreateRequest(bob, "Welcome, Alice!"));
		comments.create(first, new CommentCreateRequest(alice, "Thanks Bob."));
	}

}
