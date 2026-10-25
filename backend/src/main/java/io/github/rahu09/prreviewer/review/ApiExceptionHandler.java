package io.github.rahu09.prreviewer.review;

import io.github.rahu09.prreviewer.context.DiffParseException;
import io.github.rahu09.prreviewer.github.GitHubException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** One place that turns failures into RFC 9457 problem responses: no stack traces to clients. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ReviewGenerationException.class)
	ProblemDetail generationFailed(ReviewGenerationException e) {
		log.error("Review generation failed: {}", e.getMessage(), e);
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
	}

	@ExceptionHandler(DiffParseException.class)
	ProblemDetail invalidDiff(DiffParseException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid diff: " + e.getMessage());
	}

	@ExceptionHandler(GitHubException.class)
	ProblemDetail githubFailed(GitHubException e) {
		log.warn("GitHub call failed: {}", e.getMessage());
		HttpStatus status = e.status() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
		return ProblemDetail.forStatusAndDetail(status, e.getMessage());
	}

	@ExceptionHandler(RunNotFoundException.class)
	ProblemDetail runNotFound(RunNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(FixtureNotFoundException.class)
	ProblemDetail fixtureNotFound(FixtureNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
