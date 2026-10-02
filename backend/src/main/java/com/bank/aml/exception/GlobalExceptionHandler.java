package com.bank.aml.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail handleInvalidFile(InvalidFileException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid File Upload");
        problem.setType(URI.create("https://api.bank.internal/errors/invalid-file"));
        problem.setProperty("code", "AML-DOC-4001");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(FileOversizedException.class)
    public ProblemDetail handleFileOversized(FileOversizedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
        problem.setTitle("File Size Limit Exceeded");
        problem.setType(URI.create("https://api.bank.internal/errors/file-oversized"));
        problem.setProperty("code", "AML-DOC-4013");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum file upload size exceeded.");
        problem.setTitle("Upload Size Exceeded");
        problem.setType(URI.create("https://api.bank.internal/errors/upload-size-exceeded"));
        problem.setProperty("code", "AML-DOC-4013");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(DuplicateDocumentException.class)
    public ProblemDetail handleDuplicateDocument(DuplicateDocumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Document Detected");
        problem.setType(URI.create("https://api.bank.internal/errors/duplicate-document"));
        problem.setProperty("code", "AML-DOC-4009");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://api.bank.internal/errors/not-found"));
        problem.setProperty("code", "AML-RES-4004");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied: insufficient compliance privileges.");
        problem.setTitle("Forbidden Access");
        problem.setType(URI.create("https://api.bank.internal/errors/forbidden"));
        problem.setProperty("code", "AML-SEC-4003");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ProblemDetail handleAuthentication(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid authentication credentials or token.");
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("https://api.bank.internal/errors/unauthorized"));
        problem.setProperty("code", "AML-SEC-4001");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Input Validation Error");
        problem.setType(URI.create("https://api.bank.internal/errors/validation-error"));
        problem.setProperty("code", "AML-VAL-4000");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParams(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Required request parameter '" + ex.getParameterName() + "' of type " + ex.getParameterType() + " is missing."
        );
        problem.setTitle("Missing Request Parameter");
        problem.setType(URI.create("https://api.bank.internal/errors/missing-parameter"));
        problem.setProperty("code", "AML-VAL-4002");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setTitle("Unsupported Media Type");
        problem.setType(URI.create("https://api.bank.internal/errors/unsupported-media-type"));
        problem.setProperty("code", "AML-VAL-4015");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request payload.");
        problem.setTitle("Malformed JSON Request");
        problem.setType(URI.create("https://api.bank.internal/errors/malformed-json"));
        problem.setProperty("code", "AML-VAL-4001");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        log.error("Unhandled internal application exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An internal compliance engine error occurred.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.bank.internal/errors/internal-error"));
        problem.setProperty("code", "AML-SYS-5000");
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }
}
