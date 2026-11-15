package com.tgnguyen.layoutlybe.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePathGuardTest {

    private WorkspacePathGuard guard;

    @BeforeEach
    void setUp() {
        guard = new WorkspacePathGuard();
    }

    @Test
    void testValidRelativePathResolves(@TempDir Path tempDir) {
        Path resolved = guard.validateAndResolve(tempDir, "src/App.jsx");
        assertEquals(tempDir.resolve("src/App.jsx").normalize(), resolved);
    }

    @Test
    void testPathTraversalThrowsSecurityException(@TempDir Path tempDir) {
        assertThrows(SecurityException.class, () -> guard.validateAndResolve(tempDir, "../../../etc/passwd"));
        assertThrows(SecurityException.class, () -> guard.validateAndResolve(tempDir, "src/../../secret.txt"));
    }

    @Test
    void testSecretFileAccessThrowsSecurityException(@TempDir Path tempDir) {
        assertThrows(SecurityException.class, () -> guard.validateAndResolve(tempDir, ".env"));
        assertThrows(SecurityException.class, () -> guard.validateAndResolve(tempDir, ".env.local"));
        assertThrows(SecurityException.class, () -> guard.validateAndResolve(tempDir, "credentials.json"));
        assertThrows(SecurityException.class, () -> guard.validateAndResolve(tempDir, "id_rsa"));
        assertThrows(SecurityException.class, () -> guard.validateAndResolve(tempDir, "my_secret.key"));
    }

    @Test
    void testIsSafeRelativePath() {
        assertTrue(guard.isSafeRelativePath("src/components/Navbar.jsx"));
        assertTrue(guard.isSafeRelativePath("index.html"));
        assertFalse(guard.isSafeRelativePath("../index.html"));
        assertFalse(guard.isSafeRelativePath(".env"));
        assertFalse(guard.isSafeRelativePath("server_key.pem"));
    }
}
