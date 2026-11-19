package com.cloudwalkers.virtualthreadslab.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(@NotBlank @Size(max = 500) String content) {
}
