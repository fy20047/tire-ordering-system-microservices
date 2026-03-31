package com.fy20047.tireordering.tireservice.controller;

import com.fy20047.tireordering.tireservice.dto.ErrorResponse;
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
// 集中處理 API 例外，統一回傳結構化錯誤格式給前端。
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 這段處理用途：表單驗證失敗時回傳 400 與欄位錯誤明細。
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        ErrorResponse response = new ErrorResponse("Validation failed", details);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段處理用途：路徑參數型別不符時回傳 400。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter: " + ex.getName();
        ErrorResponse response = new ErrorResponse(message, null);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段處理用途：業務參數不合法時回傳 400。
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage(), null);
        return ResponseEntity.badRequest().body(response);
    }

    // 這段處理用途：業務狀態衝突時回傳 409。
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // 這段處理用途：未預期例外統一回傳 500，避免洩漏內部錯誤細節。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        ErrorResponse response = new ErrorResponse("Internal server error", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
