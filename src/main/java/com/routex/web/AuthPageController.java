package com.routex.web;

import com.routex.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Login and registration pages (§2.3.1). The actual sign-in (POST /login) and sign-out (POST /logout)
 * are handled by Spring Security, see SecurityConfig.
 */
@Controller
@RequiredArgsConstructor
public class AuthPageController {

    private final UserService userService;

    @Data
    public static class RegisterForm {
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name is too long")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
        private String password;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) return "register";
        try {
            userService.register(form.getName(), form.getEmail(), form.getPassword());
        } catch (IllegalArgumentException e) {
            result.rejectValue("email", "duplicate", "That email is already registered.");
            return "register";
        }
        redirect.addFlashAttribute("success", "Account created. Please sign in.");
        return "redirect:/login";
    }
}
