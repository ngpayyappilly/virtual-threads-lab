package com.cloudwalkers.threadsapp.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(@NotNull Long authorId, @NotBlank @Size(max = 500) String content) {
}
