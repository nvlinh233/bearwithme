package dev.bearwithme;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads deploy/.env so tests use the same infrastructure settings as Docker Compose. */
final class DeployEnv {

    private static final Properties PROPS = load();

    private DeployEnv() {
    }

    static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing key '" + key + "' in deploy/.env");
        }
        return value;
    }

    private static Properties load() {
        String file = System.getProperty("deploy.env.file");
        if (file == null) {
            throw new IllegalStateException("System property 'deploy.env.file' is not set");
        }
        Path path = Path.of(file);
        try (var reader = Files.newBufferedReader(path)) {
            var props = new Properties();
            props.load(reader);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}