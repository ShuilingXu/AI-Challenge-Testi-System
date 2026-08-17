package com.autohr.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/** Creates the local environment file before Spring resolves application properties. */
public final class EnvironmentFileBootstrap {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern JWT_LINE = Pattern.compile("(?m)^\\s*JWT_SECRET\\s*=.*$");

    private EnvironmentFileBootstrap() {
    }

    public static Path ensureDefaultEnvFile() {
        return ensureEnvFile(locateProjectRoot());
    }

    static Path ensureEnvFile(Path projectRoot) {
        Path envPath = projectRoot.resolve(".env");
        try {
            if (!Files.exists(envPath)) {
                Path template = projectRoot.resolve(".env.example");
                String content = Files.exists(template)
                        ? Files.readString(template, StandardCharsets.UTF_8)
                        : "JWT_SECRET=\nSCHOOL_LLM_BASE_URL=\nSCHOOL_LLM_MODEL=\nSCHOOL_LLM_API_KEY=\n";
                Files.writeString(envPath, content, StandardCharsets.UTF_8);
            }
            ensureJwtSecret(envPath);
            return envPath;
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建本地 .env 配置文件", ex);
        }
    }

    public static Path defaultEnvPath() {
        return locateProjectRoot().resolve(".env");
    }

    private static void ensureJwtSecret(Path envPath) throws IOException {
        List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
        String current = lines.stream()
                .filter(line -> line.trim().startsWith("JWT_SECRET="))
                .map(line -> line.substring(line.indexOf('=') + 1).trim())
                .findFirst()
                .orElse("");
        if (!current.isBlank()) {
            return;
        }
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(48));
        String content = Files.readString(envPath, StandardCharsets.UTF_8);
        if (JWT_LINE.matcher(content).find()) {
            content = JWT_LINE.matcher(content).replaceFirst("JWT_SECRET=" + secret);
        } else {
            content = "JWT_SECRET=" + secret + System.lineSeparator() + content;
        }
        Files.writeString(envPath, content, StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static Path locateProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve(".env.example")) || Files.exists(current.resolve(".env"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && (Files.exists(parent.resolve(".env.example")) || Files.exists(parent.resolve(".env")))) {
            return parent;
        }
        return current;
    }
}
