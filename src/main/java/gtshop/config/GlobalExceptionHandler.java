package gtshop.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value={MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value", (existing, replacement) -> existing));
        HashMap<String, Object> response = new HashMap<String, Object>();
        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("errors", errors);
        response.put("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(value={IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(value={RuntimeException.class})
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        response.put("success", false);
        response.put("message", "An error occurred: " + ex.getMessage());
        response.put("timestamp", Instant.now());
        return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(value={Exception.class})
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        response.put("success", false);
        response.put("message", "Internal server error");
        response.put("timestamp", Instant.now());
        return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

