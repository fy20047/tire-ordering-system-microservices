package com.fy20047.tireordering.orderservice.controller;

import com.fy20047.tireordering.orderservice.dto.ErrorResponse;
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
// 集中處理 Controller 層例外並輸出統一錯誤格式，避免每個 API 重複 try/catch。
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 這段方法用途：處理 DTO 欄位驗證失敗，回傳 400 與欄位錯誤明細。
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        ErrorResponse response = new ErrorResponse("Validation failed", details);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段方法用途：處理路徑或查詢參數型別不符，回傳 400。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter: " + ex.getName();
        ErrorResponse response = new ErrorResponse(message, null);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段方法用途：處理輸入/查詢邏輯錯誤，回傳 400。
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage(), null);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段方法用途：處理業務狀態衝突，回傳 409。
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // 這段方法用途：處理未預期錯誤，回傳 500 並避免洩漏內部訊息。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        ErrorResponse response = new ErrorResponse("Internal server error", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
