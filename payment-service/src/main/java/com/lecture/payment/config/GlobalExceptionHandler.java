package com.lecture.payment.config;

import com.lecture.payment.dto.PaymentDto;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.lecture.payment.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(PaymentDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(PaymentDto.ApiResponse.error(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException e
    ) {
        return ResponseEntity.badRequest()
                .body(PaymentDto.ApiResponse.error("요청 값의 범위가 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException e
    ) {
        return ResponseEntity.badRequest()
                .body(PaymentDto.ApiResponse.error("요청 값의 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PaymentDto.ApiResponse.error("서버 오류가 발생했습니다"));
    }
}
