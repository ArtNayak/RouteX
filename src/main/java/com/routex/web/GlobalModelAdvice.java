package com.routex.web;

import com.routex.model.User;
import com.routex.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Values every page needs (the signed-in user for the navbar) and friendly error pages. */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final UserService userService;

    @ModelAttribute("currentUser")
    public User currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        return userService.findByEmail(auth.getName()).orElse(null);
    }

    /** "Booking not found" and similar lookups. */
    @ExceptionHandler(IllegalArgumentException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(IllegalArgumentException e, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("title", "Not found");
        model.addAttribute("message", e.getMessage());
        return "error";
    }

    /** Fallback for a request that can't be completed in the current state. */
    @ExceptionHandler(IllegalStateException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(IllegalStateException e, Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("title", "Can't do that");
        model.addAttribute("message", e.getMessage());
        return "error";
    }
}
