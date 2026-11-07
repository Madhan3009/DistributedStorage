package com.dSystems.demo.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions raised by REST controllers into structured HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Converts request validation failures into a 400 response.
     *
     * @param ex the validation exception
     * @return a map of field names to validation messages, e.g. {@code {"password": "must not be blank"}}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        // LinkedHashMap preserves the order in which errors were reported.
        Map<String, String> errors = new LinkedHashMap<>();
        
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
