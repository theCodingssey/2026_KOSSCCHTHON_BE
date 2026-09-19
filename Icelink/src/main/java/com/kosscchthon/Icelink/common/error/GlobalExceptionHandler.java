package com.kosscchthon.Icelink.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 에러를 RFC 9457 Problem Details(application/problem+json) 로 통일한다.
 * 형식: docs/02-api-spec.md 0.2절
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TYPE_BASE = "https://icelink.app/errors/";

    @ExceptionHandler(IcelinkException.class)
    public ResponseEntity<ProblemDetail> handleIcelink(IcelinkException ex, HttpServletRequest request) {
        ProblemDetail pd = problem(ex.code(), ex.detail(), request);
        ex.properties().forEach(pd::setProperty);
        return ResponseEntity.status(ex.code().status()).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        ProblemDetail pd = problem(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", request);
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex, HttpServletRequest request) {
        ProblemDetail pd = problem(ErrorCode.VALIDATION_ERROR, "요청 본문 또는 파라미터를 해석할 수 없습니다.", request);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "존재하지 않는 경로입니다.");
        pd.setTitle("Not Found");
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail pd = problem(ErrorCode.INTERNAL_ERROR, "서버 내부 오류가 발생했습니다.", request);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(pd);
    }

    private static ProblemDetail problem(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setType(URI.create(TYPE_BASE + code.slug()));
        pd.setTitle(code.title());
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    private static Map<String, String> toFieldError(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()
        );
    }
}
