package com.pe.assistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String logout,
                        HttpSession session, Model model) {
        if (Boolean.TRUE.equals(session.getAttribute("LOCKED"))) {
            session.removeAttribute("LOCKED");
            model.addAttribute("locked", true);
        } else if (Boolean.TRUE.equals(session.getAttribute("LOGIN_ERROR"))) {
            session.removeAttribute("LOGIN_ERROR");
            model.addAttribute("loginError", true);
        }
        if (logout != null) model.addAttribute("logoutSuccess", true);
        return "auth/login";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }
}
