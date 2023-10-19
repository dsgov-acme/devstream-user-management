package io.nuvalence.user.management.api.service.controller;

import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles any thrown exceptions.
 */
@ControllerAdvice
@Slf4j
public class GlobalErrorHandler {

    /**
     * Error Response Class.
     */
    @AllArgsConstructor
    @Getter
    public class ErrorResponse {
        private List<String> messages;

        public ErrorResponse(String message) {
            this.messages = Collections.singletonList(message);
        }
    }

    /**
     * Return a forbidden request if a ForbiddenException is thrown.
     * @param e Forbidden exception.
     * @return Forbidden request.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleException(AccessDeniedException e) {
        log.warn("User does not have permission: ", e);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Return a bad request if a ConstraintViolationException is thrown.
     * @param e ConstraintViolationException exception.
     * @return Bad request.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleException(ConstraintViolationException e) {
        return ResponseEntity.badRequest()
                .body(
                        e.getConstraintViolations().isEmpty()
                                ? new ErrorResponse(e.getMessage())
                                : new ErrorResponse(
                                        e.getConstraintViolations().stream()
                                                .map(
                                                        violation ->
                                                                String.format(
                                                                        "'%s': %s",
                                                                        violation.getPropertyPath(),
                                                                        violation.getMessage()))
                                                .collect(Collectors.toList())));
    }

    /**
     * Return Bad request if MethodArgumentNotValidException is thrown in the code.
     * @param e exception
     * @return ResponseEntity for HTTP 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleException(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(
                        e.getFieldErrorCount() == 0
                                ? new ErrorResponse(e.getMessage())
                                : new ErrorResponse(
                                        e.getFieldErrors().stream()
                                                .map(
                                                        fieldError ->
                                                                String.format(
                                                                        "'%s': %s",
                                                                        fieldError.getField(),
                                                                        fieldError
                                                                                .getDefaultMessage()))
                                                .collect(Collectors.toList())));
    }
}
