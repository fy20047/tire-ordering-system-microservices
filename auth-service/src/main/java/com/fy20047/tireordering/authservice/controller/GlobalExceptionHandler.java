package com.fy20047.tireordering.authservice.controller;

import com.fy20047.tireordering.authservice.dto.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 這個檔案用途：
// 提供 Auth Service 的統一例外處理，避免每個 Controller 重複 try/catch，並固定錯誤回傳格式。
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 這段處理用途：DTO 驗證失敗時，回傳 400 + 欄位錯誤清單。
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        ErrorResponse response = new ErrorResponse("Validation failed", details);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段處理用途：參數型別不符時，回傳 400。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter: " + ex.getName();
        ErrorResponse response = new ErrorResponse(message, null);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段處理用途：商業邏輯參數錯誤（例如帳密錯誤、token 不合法）時，回傳 400。
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage(), null);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段處理用途：狀態衝突錯誤時，回傳 409。
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // 這段處理用途：未預期錯誤時，回傳 500，避免內部細節外洩。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        ErrorResponse response = new ErrorResponse("Internal server error", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
