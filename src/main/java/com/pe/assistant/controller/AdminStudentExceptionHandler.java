package com.pe.assistant.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;

@ControllerAdvice(assignableTypes = AdminController.class)
public class AdminStudentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException ex,
                                                 HttpServletRequest request,
                                                 RedirectAttributes ra) {
        return redirectStudentFormError(ex, request, ra);
    }

    @ExceptionHandler({IllegalStateException.class, NoSuchElementException.class})
    public String handleStudentStateException(RuntimeException ex,
                                              HttpServletRequest request,
                                              RedirectAttributes ra) {
        return redirectStudentFormError(ex, request, ra);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            TypeMismatchException.class,
            BindException.class
    })
    public String handleBindingException(Exception ex,
                                         HttpServletRequest request,
                                         RedirectAttributes ra) {
        String message = resolveBindingMessage(ex);
        return redirectStudentFormError(new IllegalArgumentException(message), request, ra);
    }

    private String redirectStudentFormError(RuntimeException ex,
                                            HttpServletRequest request,
                                            RedirectAttributes ra) {
        String uri = request.getRequestURI();
        if (uri != null && (uri.contains("/admin/students/add") || uri.contains("/admin/students/edit/"))) {
            String message = ex.getMessage();
            ra.addFlashAttribute("error", message == null || message.isBlank()
                    ? "\u64cd\u4f5c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
                    : message);
            return "redirect:/admin/students";
        }
        throw ex;
    }

    private String resolveBindingMessage(Exception ex) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            return switch (missing.getParameterName()) {
                case "classId" -> "\u73ed\u7ea7\u4e0d\u80fd\u4e3a\u7a7a";
                case "name" -> "\u59d3\u540d\u4e0d\u80fd\u4e3a\u7a7a";
                case "studentNo" -> "\u5b66\u53f7\u4e0d\u80fd\u4e3a\u7a7a";
                default -> "\u63d0\u4ea4\u53c2\u6570\u4e0d\u5b8c\u6574";
            };
        }
        if (ex instanceof TypeMismatchException mismatch) {
            return "classId".equals(mismatch.getPropertyName())
                    ? "\u73ed\u7ea7\u53c2\u6570\u65e0\u6548"
                    : "\u63d0\u4ea4\u53c2\u6570\u683c\u5f0f\u65e0\u6548";
        }
        return "\u63d0\u4ea4\u53c2\u6570\u65e0\u6548";
    }
}
