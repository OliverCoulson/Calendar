package com.example.voiceinput.config;

import com.example.voiceinput.dao.UserDAO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserDAO userDAO;

    public AuthInterceptor(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) return true;

        String token = request.getHeader("X-Token");
        if (token != null && !token.isEmpty() && userDAO.findByToken(token) != null) {
            return true;
        }

        response.setStatus(401);
        return false;
    }
}
