package com.cloudwalkers.virtualthreadslab.comment;

import com.cloudwalkers.virtualthreadslab.common.PageResponse;
import com.cloudwalkers.virtualthreadslab.common.ResourceNotFoundException;
import com.cloudwalkers.virtualthreadslab.post.PostService;
import com.cloudwalkers.virtualthreadslab.user.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommentService {

	private static final Logger log = LoggerFactory.getLogger(CommentService.class);

	private final CommentRepository comments;

	private final PostService posts;

	private final UserService users;

	CommentService(CommentRepository comments, PostService posts, UserService users) {
		this.comments = comments;
		this.posts = posts;
		this.users = users;
	}

	public PageResponse<CommentResponse> list(Long postId, Pageable pageable) {
		posts.find(postId);
		return PageResponse.of(comments.findByPostId(postId, pageable), CommentResponse::from);
	}

	public CommentResponse get(Long postId, Long id) {
		return CommentResponse.from(find(postId, id));
	}

	@Transactional
	public CommentResponse create(Long postId, CommentCreateRequest request) {
		Comment comment = comments
			.save(new Comment(posts.find(postId), users.find(request.authorId()), request.content()));
		log.info("Created comment id={} postId={} authorId={}", comment.getId(), postId, request.authorId());
		return CommentResponse.from(comment);
	}

	@Transactional
	public CommentResponse update(Long postId, Long id, CommentUpdateRequest request) {
		Comment comment = find(postId, id);
		comment.setContent(request.content());
		return CommentResponse.from(comments.saveAndFlush(comment));
	}

	@Transactional
	public void delete(Long postId, Long id) {
		comments.delete(find(postId, id));
		log.info("Deleted comment id={} postId={}", id, postId);
	}

	private Comment find(Long postId, Long id) {
		return comments.findByIdAndPostId(id, postId).orElseThrow(() -> new ResourceNotFoundException("Comment", id));
	}

}
