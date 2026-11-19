package com.cloudwalkers.threadsapp.post;

import com.cloudwalkers.threadsapp.common.PageResponse;
import com.cloudwalkers.threadsapp.common.ResourceNotFoundException;
import com.cloudwalkers.threadsapp.user.UserService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostService {

	private static final Logger log = LoggerFactory.getLogger(PostService.class);

	private final PostRepository posts;

	private final UserService users;

	private final Counter postsCreated;

	PostService(PostRepository posts, UserService users, MeterRegistry registry) {
		this.posts = posts;
		this.users = users;
		this.postsCreated = Counter.builder("threadsapp.posts.created")
			.description("Number of posts created")
			.register(registry);
	}

	public PageResponse<PostResponse> list(Long authorId, Pageable pageable) {
		var page = authorId == null ? posts.findAll(pageable) : posts.findByAuthorId(authorId, pageable);
		return PageResponse.of(page, PostResponse::from);
	}

	public PostResponse get(Long id) {
		return PostResponse.from(find(id));
	}

	/** Used by other modules that need the entity itself. */
	public Post find(Long id) {
		return posts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Post", id));
	}

	@Transactional
	public PostResponse create(PostCreateRequest request) {
		Post post = posts.save(new Post(users.find(request.authorId()), request.content()));
		postsCreated.increment();
		log.info("Created post id={} authorId={}", post.getId(), request.authorId());
		return PostResponse.from(post);
	}

	@Transactional
	public PostResponse update(Long id, PostUpdateRequest request) {
		Post post = find(id);
		post.setContent(request.content());
		log.info("Updated post id={}", id);
		return PostResponse.from(posts.saveAndFlush(post));
	}

	/** Deletes the post; its comments are removed by FK cascade. */
	@Transactional
	public void delete(Long id) {
		posts.delete(find(id));
		log.info("Deleted post id={} (comments cascade)", id);
	}

}
