package com.tgnguyen.layoutlybe.service.ai;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class WorkspacePathGuard {

    private static final Set<String> BLOCKED_EXACT_NAMES = Set.of(
            ".env", ".env.local", ".env.production", ".env.development",
            "credentials.json", "id_rsa", "id_rsa.pub", "application.properties", "application.yml"
    );

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            ".*\\.(key|pem|p12|pfx|secret)$|.*secret.*|.*password.*|.*credentials.*", Pattern.CASE_INSENSITIVE);

    public Path validateAndResolve(Path workspaceRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Duong dan file khong duoc de rong.");
        }

        String normalizedRelative = relativePath.replace('\\', '/').trim();
        while (normalizedRelative.startsWith("/")) {
            normalizedRelative = normalizedRelative.substring(1);
        }

        if (normalizedRelative.contains("..")) {
            throw new SecurityException("Truy cap bi chan: Khong duoc phep dung '..' de truy cap thu muc cha.");
        }

        Path absoluteWorkspace = workspaceRoot.toAbsolutePath().normalize();
        Path resolved = absoluteWorkspace.resolve(normalizedRelative).normalize();

        if (!resolved.startsWith(absoluteWorkspace)) {
            throw new SecurityException("Truy cap bi chan: Duong dan nam ngoai workspace du an.");
        }

        String fileName = resolved.getFileName().toString().toLowerCase();

        if (BLOCKED_EXACT_NAMES.contains(fileName) || SENSITIVE_PATTERN.matcher(fileName).matches()) {
            throw new SecurityException("Truy cap bi chan: File nhay cảm/secret khong duoc phep truy cap (" + fileName + ").");
        }

        // Check symlinks if file exists
        if (Files.exists(resolved)) {
            try {
                Path realPath = resolved.toRealPath();
                if (!realPath.startsWith(absoluteWorkspace)) {
                    throw new SecurityException("Truy cap bi chan: Symlink escape khoi workspace.");
                }
            } catch (IOException e) {
                // If checking real path fails, fall back to strict normalization check
            }
        }

        return resolved;
    }

    public boolean isSafeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("..")) {
            return false;
        }
        String fileName = Path.of(relativePath).getFileName().toString().toLowerCase();
        return !BLOCKED_EXACT_NAMES.contains(fileName) && !SENSITIVE_PATTERN.matcher(fileName).matches();
    }
}
