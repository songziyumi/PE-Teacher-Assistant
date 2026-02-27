package com.pe.assistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return "error/500";
    }
}
