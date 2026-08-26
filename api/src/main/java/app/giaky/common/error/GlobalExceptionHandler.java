package app.giaky.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The one place an exception becomes a response body: RFC 7807 Problem Details in the shape of
 * {@code .docs/05-api-specification.md} §2, extended with {@code code}, {@code correlationId} and
 * {@code errors}.
 *
 * <p>Anything unrecognised is a bug: it is logged in full with the correlation id and answered with
 * a bare 500 carrying that id and nothing else, so internals never reach a client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        // Expected outcomes, not faults: logged at the level their status deserves, without a stack.
        if (ex.code().status().is5xxServerError()) {
            log.error("{}: {}", ex.code(), ex.getMessage(), ex);
        } else {
            log.debug("{}: {}", ex.code(), ex.getMessage());
        }
        ProblemDetail body = Problems.of(ex.code(), ex.getMessage(), ex.errors());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.code().status());
        if (ex instanceof TooManyRequestsException limited) {
            // Seconds, rounded up: RFC 9110 delay-seconds is an integer, and rounding down would
            // invite a retry that is still refused.
            long seconds = (limited.retryAfter().toMillis() + 999L) / 1000L;
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return response.body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.debug("Optimistic lock failure", ex);
        String detail = "This resource changed since you loaded it. Reload and try again.";
        return ResponseEntity.status(ErrorCode.VERSION_CONFLICT.status())
                .body(problem(ErrorCode.VERSION_CONFLICT, detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> errors =
                ex.getConstraintViolations().stream()
                        .map(GlobalExceptionHandler::toFieldViolation)
                        .toList();
        ProblemDetail body =
                problem(ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid.");
        body.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    /**
     * The catch-all. Nothing about the cause reaches the client — only the correlation id, which is
     * enough to find the full stack in the logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        String detail = "Something went wrong. Quote the correlation id when reporting this.";
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(problem(ErrorCode.INTERNAL_ERROR, detail));
    }

    // --- Spring MVC's own exceptions, remapped onto our catalogue ------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<FieldViolation> errors =
                ex.getBindingResult().getAllErrors().stream()
                        .map(
                                error -> {
                                    String field =
                                            error instanceof FieldError fieldError
                                                    ? fieldError.getField()
                                                    : error.getObjectName();
                                    return new FieldViolation(field, error.getDefaultMessage());
                                })
                        .toList();
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "The request body is invalid.");
        body.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return malformed("The request body could not be read as JSON.");
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return malformed("Missing required parameter: " + ex.getParameterName());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String name =
                ex instanceof MethodArgumentTypeMismatchException mismatch
                        ? mismatch.getName()
                        : "value";
        return malformed("Parameter has the wrong type: " + name);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(ErrorCode.RESOURCE_NOT_FOUND, "No such endpoint."));
    }

    // --- helpers -------------------------------------------------------------------------------

    private static ResponseEntity<Object> malformed(String detail) {
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.status())
                .body(problem(ErrorCode.MALFORMED_REQUEST, detail));
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        return Problems.of(code, detail);
    }

    private static FieldViolation toFieldViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        // Bean Validation reports "method.arg0.field"; the client only cares about the leaf.
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        return new FieldViolation(field, violation.getMessage());
    }
}
