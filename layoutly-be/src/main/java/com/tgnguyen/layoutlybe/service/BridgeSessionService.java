package com.tgnguyen.layoutlybe.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ghep cap giua web (layoutly-fe) va Figma plugin thong qua mot ma ngan.
 * Web tao phien -> plugin nhap ma -> web dat lenh dong bo -> plugin poll lenh,
 * xuat thiet ke va gui /api/bridge/import kem ma -> web nhan snapshotId.
 * Trang thai chi nam trong bo nho (mat khi khoi dong lai backend).
 */
@Service
public class BridgeSessionService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // bo ky tu de nham 0/O, 1/I
    private static final int CODE_LENGTH = 6;
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private static final Duration PLUGIN_ONLINE_WINDOW = Duration.ofSeconds(8);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public SessionView create() {
        purgeExpired();
        String code;
        Session session;
        do {
            code = randomCode();
            session = new Session(code, Instant.now().plus(SESSION_TTL));
        } while (sessions.putIfAbsent(code, session) != null);
        return session.view();
    }

    public SessionView get(String code) {
        return require(code).view();
    }

    /** Web yeu cau plugin xuat lai thiet ke. scope: SELECTION hoac PAGE. */
    public SessionView requestSync(String code, String scope) {
        Session session = require(code);
        session.requestSync("PAGE".equalsIgnoreCase(scope) ? "PAGE" : "SELECTION");
        return session.view();
    }

    /** Plugin poll: danh dau plugin dang online va lay lenh dang cho (neu co). */
    public Command poll(String code) {
        return require(code).poll();
    }

    public void attachSnapshot(String code, String snapshotId) {
        require(code).attachSnapshot(snapshotId);
    }

    /** Nem IllegalArgumentException neu ma khong ton tai hoac da het han. */
    public Session require(String code) {
        String normalized = normalize(code);
        Session session = sessions.get(normalized);
        if (session == null || session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(normalized);
            throw new IllegalArgumentException("Ma ket noi khong ton tai hoac da het han.");
        }
        return session;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.values().removeIf(session -> session.expiresAt.isBefore(now));
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return builder.toString();
    }

    static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public record Command(int id, String scope) {
    }

    public record SessionView(String code, boolean pluginConnected, String snapshotId, Instant snapshotAt,
                              String pendingScope, Instant expiresAt) {
    }

    public static final class Session {
        private final String code;
        private final Instant expiresAt;
        private Instant pluginSeenAt;
        private String snapshotId;
        private Instant snapshotAt;
        private String pendingScope;
        private int commandId;

        private Session(String code, Instant expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }

        private synchronized void requestSync(String scope) {
            this.pendingScope = scope;
            this.commandId++;
        }

        private synchronized Command poll() {
            this.pluginSeenAt = Instant.now();
            if (pendingScope == null) return null;
            Command command = new Command(commandId, pendingScope);
            this.pendingScope = null; // giao toi da mot lan
            return command;
        }

        private synchronized void attachSnapshot(String snapshotId) {
            this.snapshotId = snapshotId;
            this.snapshotAt = Instant.now();
            this.pluginSeenAt = this.snapshotAt;
        }

        private synchronized SessionView view() {
            boolean online = pluginSeenAt != null
                    && pluginSeenAt.isAfter(Instant.now().minus(PLUGIN_ONLINE_WINDOW));
            return new SessionView(code, online, snapshotId, snapshotAt, pendingScope, expiresAt);
        }
    }
}
