package com.tgnguyen.layoutlybe.auth;

import com.tgnguyen.layoutlybe.exception.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {
    public static final String SESSION_COOKIE = "layoutly_session";

    static final Duration SHORT_SESSION = Duration.ofHours(24);
    static final Duration LONG_SESSION = Duration.ofDays(30);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String BAD_CREDENTIALS = "Email hoặc mật khẩu không đúng.";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Ket qua dang nhap/dang ky: nguoi dung + token phien (chi dung de dat cookie). */
    public record Issued(UserAccount user, String token, Duration ttl) {
    }

    private final UserRepository users;
    private final SessionRepository sessions;
    private final LoginThrottle throttle;
    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    public AuthService(UserRepository users,
                       SessionRepository sessions,
                       LoginThrottle throttle,
                       @Value("${layoutly.auth.bcrypt-strength:10}") int bcryptStrength) {
        this.users = users;
        this.sessions = sessions;
        this.throttle = throttle;
        this.encoder = new BCryptPasswordEncoder(bcryptStrength);
        // Bam gia de thoi gian phan hoi khong lo email nao ton tai.
        this.dummyHash = encoder.encode(prehash("layoutly-dummy-password"));
    }

    @Transactional
    public Issued register(String name, String email, String password, String acceptedTermsVersion) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 100) {
            throw bad("Vui lòng nhập họ và tên (tối đa 100 ký tự).");
        }
        String cleanEmail = normalizeEmail(email);
        if (cleanEmail.isEmpty() || cleanEmail.length() > 254 || !EMAIL.matcher(cleanEmail).matches()) {
            throw bad("Email không hợp lệ.");
        }
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw bad("Mật khẩu cần từ 12 đến 128 ký tự.");
        }
        if (!"1".equals(acceptedTermsVersion)) throw bad("Cần đồng ý với điều khoản để tạo tài khoản.");
        if (users.existsByEmail(cleanEmail)) throw emailTaken();

        UserAccount user;
        try {
            user = users.saveAndFlush(new UserAccount(cleanEmail, cleanName, encoder.encode(prehash(password)), acceptedTermsVersion));
        } catch (DataIntegrityViolationException race) {
            throw emailTaken();
        }
        return issue(user, false);
    }

    @Transactional
    public Issued login(String email, String password, boolean remember, String clientIp) {
        String cleanEmail = normalizeEmail(email);
        if (cleanEmail.isEmpty() || cleanEmail.length() > 254 || password == null
                || password.isEmpty() || password.length() > 128) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        String key = clientIp + "|" + cleanEmail;
        if (throttle.isBlocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã thử sai quá nhiều lần. Vui lòng thử lại sau 15 phút.");
        }
        UserAccount user = users.findByEmail(cleanEmail).orElse(null);
        boolean matches = encoder.matches(prehash(password), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches) {
            throttle.recordFailure(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        throttle.reset(key);
        sessions.deleteExpired(Instant.now());
        return issue(user, remember);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.deleteByHash(sha256Hex(token));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findUser(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findActive(sha256Hex(token), Instant.now()).map(UserSession::getUser);
    }

    public UserAccount requireUser(HttpServletRequest request) {
        return findUser(extractToken(request))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập."));
    }

    public static String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private Issued issue(UserAccount user, boolean remember) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Duration ttl = remember ? LONG_SESSION : SHORT_SESSION;
        sessions.save(new UserSession(sha256Hex(token), user, Instant.now().plus(ttl)));
        return new Issued(user, token, ttl);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "Email này đã được đăng ký.");
    }

    /** SHA-256 truoc khi BCrypt: tranh gioi han 72 byte cua BCrypt (mat khau dai khong bi cat). */
    private static String prehash(String password) {
        return Base64.getEncoder().encodeToString(digest(password));
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
