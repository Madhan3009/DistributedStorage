package com.dSystems.demo.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * THE GLOBAL ER (EMERGENCY ROOM) / ERROR TRANSLATOR.
 * 
 * Think of this class as a safety net under all our web controllers. 
 * If a request comes in and causes a crash or fails validation anywhere in the app, 
 * Spring intercepts the error and routes it here. This class translates technical code exceptions 
 * into clear, readable error messages (like listing which form fields were typed incorrectly).
 * 
 * Annotations:
 * - @RestControllerAdvice: Tells Spring to watch all controllers and step in whenever an exception occurs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Catches and handles validation errors.
     * 
     * For example, if a user tries to register with a blank password, Spring will reject the request 
     * and throw a `MethodArgumentNotValidException`. This method catches that exception, collects all 
     * the specific field errors, and formats them into a neat JSON dictionary sent back with an HTTP 400 status.
     * 
     * @param ex The validation exception object containing the list of errors.
     * @return A map of field names to error messages (e.g., {"password": "must not be blank"}).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        // LinkedHashMap keeps the errors in the order they were found
        Map<String, String> errors = new LinkedHashMap<>();
        
        // Loop through all field validation failures
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        
        // Return the map of errors with HTTP 400 (Bad Request)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
