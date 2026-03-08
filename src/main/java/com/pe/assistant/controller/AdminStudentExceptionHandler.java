package com.pe.assistant.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(assignableTypes = AdminController.class)
public class AdminStudentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException ex,
                                                 HttpServletRequest request,
                                                 RedirectAttributes ra) {
        String uri = request.getRequestURI();
        if (uri != null && (uri.contains("/admin/students/add") || uri.contains("/admin/students/edit/"))) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/students";
        }
        throw ex;
    }
}
