package com.tgnguyen.layoutlybe.service.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SandboxedExecutionService {

    public record BuildResult(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            List<String> errors
    ) {}

    private final WorkspacePathGuard pathGuard;

    @Autowired
    public SandboxedExecutionService(WorkspacePathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    public BuildResult validateAndBuild(Path workspaceDir, String targetFramework) {
        Path normalizedDir = workspaceDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedDir)) {
            return new BuildResult(false, -1, "", "Workspace directory does not exist", List.of("Thu muc workspace khong ton tai."));
        }

        // No OS/container sandbox is installed. Never execute project npm scripts
        // or launch Node from an AI-controlled directory on the host.
        return runFallbackValidation(normalizedDir, targetFramework);
    }

    public BuildResult runTests(Path workspaceDir, String targetFramework) {
        // Run tests/check references & syntax
        return validateAndBuild(workspaceDir, targetFramework);
    }

    private BuildResult runNodeValidation(Path workspaceDir, String targetFramework) {
        List<String> errors = new ArrayList<>();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            // Run a lightweight node syntax check script on all JS/JSX/CSS files in workspace
            List<Path> codeFiles = new ArrayList<>();
            try (var stream = Files.walk(workspaceDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".jsx") || name.endsWith(".js") || name.endsWith(".html") || name.endsWith(".css");
                        })
                        .forEach(codeFiles::add);
            }

            for (Path file : codeFiles) {
                String relative = workspaceDir.relativize(file).toString().replace('\\', '/');
                if (!pathGuard.isSafeRelativePath(relative)) continue;

                if (relative.endsWith(".js") && !relative.endsWith(".jsx")) {
                    String fileContent = Files.readString(file, StandardCharsets.UTF_8);
                    if (!fileContent.contains("<") && !fileContent.contains(">")) {
                        ProcessBuilder pb = new ProcessBuilder("node", "--check", file.toString());
                        pb.directory(workspaceDir.toFile());
                        Map<String, String> env = pb.environment();
                        env.remove("OPENAI_API_KEY");
                        env.remove("FIGMA_TOKEN");

                        Process process = pb.start();
                        boolean finished = process.waitFor(10, TimeUnit.SECONDS);

                        if (!finished) {
                            process.destroyForcibly();
                            errors.add("Timeout (10s) khi kiem tra syntax file: " + relative);
                            stderr.append("Process timeout: ").append(relative).append("\n");
                        } else if (process.exitValue() != 0) {
                            String errOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                            errors.add("Loi syntax trong " + relative + ": " + errOutput.trim());
                            stderr.append(errOutput).append("\n");
                        } else {
                            stdout.append("OK: ").append(relative).append("\n");
                        }
                    }
                }
            }

            // Also run fallback checks for HTML/JSX structure
            BuildResult fallbackResult = runFallbackValidation(workspaceDir, targetFramework);
            errors.addAll(fallbackResult.errors());

            boolean success = errors.isEmpty();
            return new BuildResult(success, success ? 0 : 1, stdout.toString(), stderr.toString(), errors);

        } catch (Exception e) {
            return runFallbackValidation(workspaceDir, targetFramework);
        }
    }

    private BuildResult runFallbackValidation(Path workspaceDir, String targetFramework) {
        List<String> errors = new ArrayList<>();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try (var stream = Files.walk(workspaceDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                String relative = workspaceDir.relativize(file).toString().replace('\\', '/');
                if (!pathGuard.isSafeRelativePath(relative)) continue;
                if (!relative.matches("(?i).*\\.(jsx?|tsx?|css|html?)$")) continue;
                if (Files.size(file) > 2_000_000) { errors.add("Source exceeds 2MB validation limit: " + relative); continue; }
                String content = Files.readString(file, StandardCharsets.UTF_8);

                if (relative.endsWith(".jsx") || relative.endsWith(".js")) {
                    validateJsxContent(relative, content, errors);
                } else if (relative.endsWith(".html")) {
                    validateHtmlContent(relative, content, errors);
                } else if (relative.endsWith(".css")) {
                    validateCssContent(relative, content, errors);
                }
            }
        } catch (Exception e) {
            errors.add("Loi khi quet va phan tich workspace: " + e.getMessage());
        }

        boolean success = errors.isEmpty();
        return new BuildResult(
                success,
                success ? 0 : 1,
                success ? "STATIC_VALIDATION_ONLY: lightweight syntax and structure checks passed. No npm scripts, production build, or project tests were executed." : "",
                String.join("\n", errors),
                errors
        );
    }

    private void validateJsxContent(String path, String content, List<String> errors) {
        // Check unbalanced curly braces
        int openBraces = countMatches(content, '{') - countMatches(content, '}');
        if (Math.abs(openBraces) > 3) {
            errors.add(path + ": Mat can bang ngoac nhon '{ }' (" + openBraces + ")");
        }

        // Check unbalanced parentheses
        int openParens = countMatches(content, '(') - countMatches(content, ')');
        if (Math.abs(openParens) > 3) {
            errors.add(path + ": Mat can bang ngoac don '( )' (" + openParens + ")");
        }

        // Check for broken imports (e.g., import ... from '')
        if (content.contains("from ''") || content.contains("from \"\"")) {
            errors.add(path + ": Import tu duong dan rong (empty import path)");
        }
    }

    private void validateHtmlContent(String path, String content, List<String> errors) {
        // Check DOCTYPE or standard HTML tags
        if (!content.toLowerCase().contains("<html") && !content.toLowerCase().contains("<!doctype html")) {
            errors.add(path + ": Thieu the HTML root/DOCTYPE");
        }
    }

    private void validateCssContent(String path, String content, List<String> errors) {
        int openBraces = countMatches(content, '{') - countMatches(content, '}');
        if (openBraces != 0) {
            errors.add(path + ": CSS mat can bang ngoac nhon '{ }'");
        }
    }

    private int countMatches(String text, char ch) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ch) count++;
        }
        return count;
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
