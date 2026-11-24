package com.tgnguyen.layoutlybe.auth;

import com.tgnguyen.layoutlybe.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record RegisterRequest(String displayName, String email, String password, String acceptedTermsVersion) {
    }

    public record LoginRequest(String email, String password, Boolean rememberMe) {
    }

    public record UserView(String id, String displayName, String email) {
        static UserView of(UserAccount user) {
            return new UserView(user.getId().toString(), user.getDisplayName(), user.getEmail());
        }
    }

    public record AuthResponse(UserView user) {
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        // contractVersion 1 la hop dong frontend yeu cau. passwordReset chua bat: can gui email truoc.
        return Map.of("contractVersion", 1, "login", true, "registration", true, "passwordReset", false);
    }

    /** Buoc 1 cua CSRF double-submit: dat cookie va tra token de frontend gui lai trong header. */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
        String token = CsrfFilter.cookieToken(request);
        if (token == null) {
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        }
        ResponseCookie cookie = ResponseCookie.from(CsrfFilter.COOKIE, token)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("headerName", CsrfFilter.HEADER, "token", token));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        AuthService.Issued issued = auth.register(body.displayName(), body.email(), body.password(), body.acceptedTermsVersion());
        return respond(HttpStatus.CREATED, issued, false, request);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        boolean remember = Boolean.TRUE.equals(body.rememberMe());
        AuthService.Issued issued = auth.login(body.email(), body.password(), remember, request.getRemoteAddr());
        return respond(HttpStatus.OK, issued, remember, request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout(AuthService.extractToken(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, baseCookie("", request).maxAge(0).build().toString())
                .build();
    }

    @GetMapping("/me")
    public AuthResponse me(HttpServletRequest request) {
        return auth.findUser(AuthService.extractToken(request))
                .map(user -> new AuthResponse(UserView.of(user)))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập."));
    }

    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthService.Issued issued,
                                                 boolean persistent, HttpServletRequest request) {
        ResponseCookie.ResponseCookieBuilder cookie = baseCookie(issued.token(), request);
        // Khong "ghi nho": cookie phien (het khi dong trinh duyet), server van het han sau 24 gio.
        if (persistent) cookie.maxAge(issued.ttl());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.build().toString())
                .body(new AuthResponse(UserView.of(issued.user())));
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value, HttpServletRequest request) {
        return ResponseCookie.from(AuthService.SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/");
    }
}
