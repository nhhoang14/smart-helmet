package com.helmet.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class WebAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/login") || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/favicon")) {
            return true;
        }

        if (request.getSession(false) != null
                && request.getSession(false).getAttribute("authenticatedUser") != null) {
            return true;
        }

        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }
}
