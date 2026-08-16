package com.autohr.modules.system.service;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class SystemConfigService {

    private static final Pattern ENV_KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private final Path envPath;
    private final Function<String, String> environmentLookup;

    public SystemConfigService() {
        this(Paths.get(".env"), System::getenv);
    }

    SystemConfigService(Path envPath) {
        this(envPath, System::getenv);
    }

    SystemConfigService(Path envPath, Function<String, String> environmentLookup) {
        this.envPath = envPath;
        this.environmentLookup = environmentLookup;
    }

    public synchronized Map<String, String> loadConfig(String... keys) {
        Map<String, String> envFile = readEnvFile();
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : keys) {
            String value = environmentLookup.apply(key);
            if (value == null) {
                value = envFile.get(key);
            }
            result.put(key, value == null ? "" : value);
        }
        return result;
    }

    public synchronized void saveConfig(Map<String, String> updates) {
        Map<String, String> sanitizedUpdates = sanitizeUpdates(updates);
        if (sanitizedUpdates.isEmpty()) {
            return;
        }
        List<String> currentLines = readEnvLines();
        Set<String> pendingKeys = new LinkedHashSet<>(sanitizedUpdates.keySet());
        StringBuilder builder = new StringBuilder();
        for (String line : currentLines) {
            String key = parseKey(line);
            if (key != null && sanitizedUpdates.containsKey(key)) {
                builder.append(key).append('=').append(serializeValue(sanitizedUpdates.get(key)));
                pendingKeys.remove(key);
            } else {
                builder.append(line);
            }
            builder.append(System.lineSeparator());
        }
        for (String key : pendingKeys) {
            builder.append(key).append('=').append(serializeValue(sanitizedUpdates.get(key))).append(System.lineSeparator());
        }
        writeEnvFile(builder.toString());
    }

    private Map<String, String> readEnvFile() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(envPath)) {
            return values;
        }
        for (String line : readEnvLines()) {
            String key = parseKey(line);
            if (key != null) {
                values.put(key, parseValue(line, key));
            }
        }
        return values;
    }

    private List<String> readEnvLines() {
        if (!Files.exists(envPath)) {
            return List.of();
        }
        try {
            return Files.readAllLines(envPath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取.env配置失败", ex);
        }
    }

    private Map<String, String> sanitizeUpdates(Map<String, String> updates) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (updates == null) {
            return sanitized;
        }
        updates.forEach((key, value) -> {
            if (StrUtil.isBlank(key) || value == null) {
                return;
            }
            if (!ENV_KEY_PATTERN.matcher(key).matches()) {
                throw new BusinessException("环境变量名称无效: " + key);
            }
            if (containsControlCharacter(value)) {
                throw new BusinessException("环境变量值不能包含控制字符");
            }
            sanitized.put(key, value);
        });
        return sanitized;
    }

    private String parseKey(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        int index = line.indexOf('=');
        if (index < 1) {
            return null;
        }
        String key = line.substring(0, index).trim();
        return ENV_KEY_PATTERN.matcher(key).matches() ? key : null;
    }

    private String parseValue(String line, String key) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(line));
        } catch (IOException ex) {
            throw new IllegalStateException("解析.env配置失败", ex);
        }
        return properties.getProperty(key, "");
    }

    private String serializeValue(String value) {
        StringBuilder serialized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == ' ' || character == '=' || character == ':'
                    || character == '#' || character == '!') {
                serialized.append('\\');
            }
            serialized.append(character);
        }
        return serialized.toString();
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl((char) character));
    }

    private void writeEnvFile(String content) {
        Path absoluteEnvPath = envPath.toAbsolutePath();
        Path directory = absoluteEnvPath.getParent();
        Path temporaryPath = null;
        try {
            temporaryPath = Files.createTempFile(directory, absoluteEnvPath.getFileName().toString(), ".tmp");
            Files.writeString(temporaryPath, content, StandardCharsets.UTF_8);
            copyPosixPermissions(absoluteEnvPath, temporaryPath);
            try {
                Files.move(temporaryPath, absoluteEnvPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryPath, absoluteEnvPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("写入.env配置失败", ex);
        } finally {
            if (temporaryPath != null) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void copyPosixPermissions(Path source, Path target) {
        try {
            if (Files.exists(source)) {
                Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX filesystems do not expose POSIX permissions.
        }
    }
}
