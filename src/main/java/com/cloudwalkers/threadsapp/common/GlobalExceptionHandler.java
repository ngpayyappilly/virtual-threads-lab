package com.cloudwalkers.threadsapp.common;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem responses.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		log.debug("Not found: {}", ex.getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Resource not found");
		problem.setType(URI.create("urn:problem:not-found"));
		return problem;
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		log.warn("Conflict: {}", ex.getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Conflict");
		problem.setType(URI.create("urn:problem:conflict"));
		return problem;
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The request conflicts with existing data");
		problem.setTitle("Data integrity violation");
		problem.setType(URI.create("urn:problem:conflict"));
		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
		return validationProblem(errors);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
		return ResponseEntity.badRequest().body(validationProblem(errors));
	}

	private ProblemDetail validationProblem(Map<String, String> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setTitle("Validation error");
		problem.setType(URI.create("urn:problem:validation"));
		problem.setProperty("errors", errors);
		return problem;
	}

}
