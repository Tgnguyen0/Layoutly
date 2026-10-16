package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.service.AccountService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AccountController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.tgnguyen.layoutlybe.service.PasswordResetService reset;
    private static final String COOKIE = "layoutly_session";
    private final AccountService accounts;
    private final boolean secureCookie;

    public AccountController(AccountService accounts, @Value("${layoutly.auth.secure-cookie:false}") boolean secureCookie) {
        this.accounts = accounts;
        this.secureCookie = secureCookie;
    }

    // MVC debug logging calls toString() on request bodies; never include credentials there.
    public record Register(String displayName, String email, String password, String acceptedTermsVersion) {
        @Override public String toString() { return "Register[redacted]"; }
    }
    public record Credentials(String email, String password, boolean rememberMe) {
        @Override public String toString() { return "Credentials[redacted]"; }
    }
    public record Profile(String displayName, String email, String company, String bio, String currentPassword) {
        @Override public String toString() { return "Profile[redacted]"; }
    }
    public record Password(String currentPassword, String newPassword) {
        @Override public String toString() { return "Password[redacted]"; }
    }

    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("contractVersion", 1, "login", true, "registration", true, "passwordReset", reset != null && reset.available());
    }

    @GetMapping("/csrf")
    public CsrfView csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("user", accounts.current(token(request)));
        return result;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Register body, HttpServletRequest request, HttpServletResponse response) {
        checkCsrf(request);
        accounts.limit("register:" + request.getRemoteAddr(), 10);
        AccountService.Login login = accounts.register(body.displayName(), body.email(), body.password(), body.acceptedTermsVersion(), request.getHeader("User-Agent"));
        signedIn(login, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", login.user()));
    }

    @PostMapping("/login")
    public Map<String, ?> login(@RequestBody Credentials body, HttpServletRequest request, HttpServletResponse response) {
        checkCsrf(request);
        accounts.limit("login:" + request.getRemoteAddr(), 30);
        AccountService.Login login = accounts.login(body.email(), body.password(), body.rememberMe(), request.getHeader("User-Agent"), token(request));
        signedIn(login, request, response);
        return Map.of("user", login.user());
    }

    @PatchMapping("/profile")
    public Map<String, ?> profile(@RequestBody Profile body, HttpServletRequest request) {
        checkCsrf(request);
        accounts.require(token(request));
        accounts.limit("sensitive:" + request.getRemoteAddr(), 30);
        return Map.of("user", accounts.update(token(request), body.displayName(), body.email(), body.company(), body.bio(), body.currentPassword()));
    }

    @PostMapping("/password")
    public Map<String, ?> password(@RequestBody Password body, HttpServletRequest request, HttpServletResponse response) {
        checkCsrf(request);
        accounts.require(token(request));
        accounts.limit("sensitive:" + request.getRemoteAddr(), 30);
        AccountService.Login login = accounts.changePassword(token(request), body.currentPassword(), body.newPassword(), request.getHeader("User-Agent"));
        signedIn(login, request, response);
        return Map.of("user", login.user());
    }

    @GetMapping("/sessions")
    public Map<String, ?> sessions(HttpServletRequest request) { return Map.of("sessions", accounts.sessions(token(request))); }

    @PostMapping("/sessions/revoke-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(HttpServletRequest request) { checkCsrf(request); accounts.revokeOthers(token(request)); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        checkCsrf(request);
        accounts.logout(token(request));
        clear(request, response);
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestBody Password body, HttpServletRequest request, HttpServletResponse response) {
        checkCsrf(request);
        accounts.require(token(request));
        accounts.limit("sensitive:" + request.getRemoteAddr(), 30);
        accounts.delete(token(request), body.currentPassword());
        clear(request, response);
    }

    @ExceptionHandler(AccountService.AccountException.class)
    public ResponseEntity<?> accountError(AccountService.AccountException error) {
        return ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(Map.of("message", error.getMessage()));
    }

    private void checkCsrf(HttpServletRequest request) {
        // Spring Security's CsrfFilter verifies all unsafe API requests before MVC.
    }

    private void signedIn(AccountService.Login login, HttpServletRequest request, HttpServletResponse response) {
        // Rotate both the authentication token and servlet session after authentication.
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        expireLegacyCookie(request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, login.token()).path("/api")
                .httpOnly(true).sameSite("Lax").secure(secureCookie || request.isSecure())
                .maxAge(login.session().rememberMe() ? Duration.ofDays(30) : Duration.ofSeconds(-1)).build().toString());
    }

    private void clear(HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        expireLegacyCookie(request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "").path("/api")
                .httpOnly(true).sameSite("Lax").secure(secureCookie || request.isSecure()).maxAge(0).build().toString());
    }

    private String token(HttpServletRequest request) {
        return com.tgnguyen.layoutlybe.security.SessionAuthenticationFilter.token(request);
    }

    private void expireLegacyCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "").path("/api/auth")
            .httpOnly(true).sameSite("Lax").secure(secureCookie || request.isSecure()).maxAge(0).build().toString());
    }

    @PostMapping("/bridge-token")
    public AccountService.BridgeToken bridgeToken(HttpServletRequest request) {
        accounts.limit("bridge:" + request.getRemoteAddr(), 30);
        return accounts.bridgeToken(token(request));
    }
    public record CsrfView(String headerName, String token) { @Override public String toString() { return "CsrfView[redacted]"; } }

    public record Forgot(String email) { @Override public String toString() { return "Forgot[redacted]"; } }
    public record Reset(String token, String password) { @Override public String toString() { return "Reset[redacted]"; } }
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@RequestBody Forgot body, HttpServletRequest request) {
        accounts.limit("reset:" + request.getRemoteAddr(), 10);
        reset.forgot(body.email());
    }
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@RequestBody Reset body, HttpServletRequest request, HttpServletResponse response) {
        accounts.limit("reset:" + request.getRemoteAddr(), 10);
        reset.reset(body.token(), body.password());
        clear(request, response);
    }
}
