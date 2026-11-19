package com.cloudwalkers.virtualthreadslab.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(@NotNull Long authorId, @NotBlank @Size(max = 500) String content) {
}
