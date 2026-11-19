package com.cloudwalkers.threadsapp.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

	@Override
	@EntityGraph(attributePaths = "author")
	Page<Post> findAll(Pageable pageable);

	@EntityGraph(attributePaths = "author")
	Page<Post> findByAuthorId(Long authorId, Pageable pageable);

}
