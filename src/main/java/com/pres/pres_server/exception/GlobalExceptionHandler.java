package com.pres.pres_server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        log.warn("잘못된 상태 요청: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(Map.of("error", errorMsg));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String errorMsg = "파라미터 타입이 올바르지 않습니다: " + ex.getName();
        return ResponseEntity.badRequest().body(Map.of("error", errorMsg));
    }

    // Spring Security 인증 예외 처리
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(AuthenticationException ex) {
        log.warn("인증 실패: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "인증이 필요합니다."));
    }

    // OAuth2 인증 예외 처리
    @ExceptionHandler(OAuth2AuthenticationException.class)
    public ResponseEntity<?> handleOAuth2AuthenticationException(OAuth2AuthenticationException ex) {
        log.warn("OAuth2 인증 실패: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "OAuth2 인증에 실패했습니다."));
    }

    // JSON 파싱 오류 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("JSON 파싱 오류: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "잘못된 JSON 형식입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }

    // Q&A 생성 관련 예외 처리
    @ExceptionHandler(QnaGenerationException.class)
    public ResponseEntity<Map<String, Object>> handleQnaGenerationException(QnaGenerationException ex) {
        log.error("Q&A 생성 오류: ", ex);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("questionCount", 0);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    // 카카오 관련 예외 처리, badrequest 400
    @ExceptionHandler(KakaoBadRequestException.class)
    public ResponseEntity<Map<String, String>> handleKakaoBadRequest(KakaoBadRequestException ex) {
        log.warn("카카오 API 요청 오류 (400): {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    // 카카오 인증 관련 예외 처리, unauthorized 401
    @ExceptionHandler(KakaoUnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleKakaoUnauthorized(KakaoUnauthorizedException ex) {
        log.warn("카카오 API 인증 실패 (401): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    // 카카오 서버 오류 관련 예외 처리, internal server error 500
    @ExceptionHandler(KakaoInternalServerException.class)
    public ResponseEntity<Map<String, String>> handleKakaoInternalServer(KakaoInternalServerException ex) {
        log.error("카카오 API 내부 오류 (500): {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }

    // 기타 카카오 예외 처리, service unavailable 503
    @ExceptionHandler(KakaoException.class)
    public ResponseEntity<Map<String, String>> handleKakaoException(KakaoException ex) {
        log.error("알 수 없는 카카오 API 오류: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }

}
