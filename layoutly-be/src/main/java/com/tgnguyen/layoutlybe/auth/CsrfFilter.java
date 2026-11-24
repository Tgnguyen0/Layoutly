package com.tgnguyen.layoutlybe.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CSRF kieu double-submit: frontend goi GET /api/auth/csrf (server dat cookie + tra token),
 * roi gui token do trong header X-CSRF-TOKEN cho moi POST/PUT/DELETE vao /api/auth va /api/projects.
 */
@Component
public class CsrfFilter extends OncePerRequestFilter {
    public static final String COOKIE = "layoutly_csrf";
    public static final String HEADER = "X-CSRF-TOKEN";
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean protectedPath = path.startsWith("/api/auth/") || path.startsWith("/api/projects");
        return !protectedPath || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cookie = cookieToken(request);
        String header = request.getHeader(HEADER);
        boolean valid = cookie != null && header != null && MessageDigest.isEqual(
                cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Phiên bảo mật không hợp lệ. Vui lòng tải lại trang.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Token trong cookie neu dung dinh dang, nguoc lai null (khong bao gio phan chieu gia tri la). */
    static String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                    && TOKEN_FORMAT.matcher(cookie.getValue()).matches()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
