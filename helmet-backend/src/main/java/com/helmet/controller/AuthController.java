package com.helmet.controller;

import com.helmet.model.User;
import com.helmet.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login(HttpSession session) {
        if (session.getAttribute("authenticatedUser") != null) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @PostMapping("/login")
    public String authenticate(@RequestParam String username,
                               @RequestParam String password,
                               HttpServletRequest request,
                               Model model) {
        User user = userRepository.findByUsername(username.trim()).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng.");
            model.addAttribute("username", username);
            return "login";
        }

        request.getSession(true).setAttribute("authenticatedUser", user.getUsername());
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
