package com.trilium.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Console;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

final class TriliumAuth {

    private static final Logger log = LoggerFactory.getLogger(TriliumAuth.class);
    private static final Path CONFIG_DIR;
    private static final Path CONFIG_FILE;
    private static final String MCP_SERVER_NAME = "trilium";

    static {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            throw new IllegalStateException("user.home system property is not set");
        }
        CONFIG_DIR = Path.of(home, ".trilium-mcp");
        CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
    }

    private TriliumAuth() {}

    record Config(String url, String token) {}

    static Config getConfig() {
        return getConfig(CONFIG_FILE);
    }

    static Config getConfig(Path configFile) {
        if (!Files.exists(configFile)) {
            throw new IllegalStateException(
                    "Config not found at " + configFile + ". Run with --init to set up.");
        }

        var props = new Properties();
        try (var reader = Files.newBufferedReader(configFile)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config: " + e.getMessage(), e);
        }

        String url = requireProp(props, "trilium.url");
        String token = requireProp(props, "trilium.token");

        return new Config(url, token);
    }

    static void init(String claudeBinary) throws IOException, InterruptedException {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("No console available. Run --init from an interactive terminal.");
        }

        System.err.println("Trilium MCP Server — Setup");
        System.err.println("==========================");
        System.err.println();

        boolean configExists = Files.exists(CONFIG_FILE);
        if (configExists) {
            System.err.println("[skip] Config already exists at " + CONFIG_FILE);
        } else {
            setupConfig(console);
        }

        System.err.println();
        registerWithClaude(console, claudeBinary);

        System.err.println();
        System.err.println("Done. Restart Claude Code to use the trilium tools.");
    }

    private static void setupConfig(Console console) throws IOException {
        System.err.println("You'll need your Trilium ETAPI token.");
        System.err.println("Generate one in Trilium: Options → ETAPI → Generate new token");
        System.err.println();

        String url = prompt(console, "Trilium URL [http://127.0.0.1:8080]: ");
        if (url.isBlank()) {
            url = "http://127.0.0.1:8080";
        }
        String token = prompt(console, "ETAPI token: ");

        System.err.println("Validating connection...");
        validateConnection(url, token);

        ensureConfigDir();

        var props = new Properties();
        props.setProperty("trilium.url", url);
        props.setProperty("trilium.token", token);

        try (var writer = Files.newBufferedWriter(CONFIG_FILE)) {
            props.store(writer, "Trilium MCP Server Configuration");
        }

        Files.setPosixFilePermissions(CONFIG_FILE, PosixFilePermissions.fromString("rw-------"));
        System.err.println("[done] Config saved to " + CONFIG_FILE);
    }

    private static void validateConnection(String url, String token) {
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/etapi/app-info"))
                    .header("Authorization", token)
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Connection validation failed: HTTP " + response.statusCode()
                        + ". Check your URL and token.");
            }
            System.err.println("[done] Connection validated successfully.");
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(
                    "Failed to connect to Trilium at " + url + ": " + e.getMessage(), e);
        }
    }

    private static void registerWithClaude(Console console, String claudeBinary)
            throws IOException, InterruptedException {
        if (claudeBinary == null || claudeBinary.isBlank()) {
            claudeBinary = findClaudeBinary();
        }

        if (claudeBinary == null) {
            System.err.println("[skip] Claude Code binary not found. Register manually:");
            printManualRegistration();
            return;
        }

        if (isAlreadyRegistered(claudeBinary)) {
            System.err.println("[skip] Already registered with Claude Code");
            return;
        }

        String jarPath = resolveJarPath();
        System.err.println("Registering with Claude Code...");

        var process = new ProcessBuilder(
                claudeBinary, "mcp", "add",
                "--scope", "user",
                "--transport", "stdio",
                MCP_SERVER_NAME, "--",
                "java", "-jar", jarPath)
                .inheritIO()
                .start();

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            System.err.println("[done] Registered as '" + MCP_SERVER_NAME + "'");
        } else {
            System.err.println("[fail] Registration failed (exit " + exitCode + "). Register manually:");
            printManualRegistration();
        }
    }

    private static String findClaudeBinary() {
        String[] candidates = {
                "claude",
                System.getProperty("user.home") + "/.local/bin/claude",
                "/usr/local/bin/claude",
                "/usr/bin/claude"
        };

        for (String candidate : candidates) {
            try {
                var process = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                int exit = process.waitFor();
                if (exit == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean isAlreadyRegistered(String claudeBinary) {
        try {
            var process = new ProcessBuilder(claudeBinary, "mcp", "list")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output.contains(MCP_SERVER_NAME + ":");
        } catch (Exception e) {
            return false;
        }
    }

    private static String resolveJarPath() {
        String jarPath = TriliumAuth.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        if (jarPath.endsWith(".jar")) {
            return Path.of(jarPath).toAbsolutePath().toString();
        }
        return Path.of("target", "trilium-mcp-1.0.0.jar").toAbsolutePath().toString();
    }

    private static void printManualRegistration() {
        String jarPath = resolveJarPath();
        System.err.println("  claude mcp add --scope user --transport stdio trilium -- \\");
        System.err.println("    java -jar " + jarPath);
    }

    private static void ensureConfigDir() throws IOException {
        if (!Files.exists(CONFIG_DIR)) {
            Files.createDirectories(CONFIG_DIR);
            Files.setPosixFilePermissions(CONFIG_DIR, PosixFilePermissions.fromString("rwx------"));
            log.info("Created config directory: {}", CONFIG_DIR);
        }
    }

    private static String requireProp(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required config property: " + key + ". Run with --init to reconfigure.");
        }
        return value.trim();
    }

    private static String prompt(Console console, String message) {
        System.err.print(message);
        String line = console.readLine();
        return line == null ? "" : line.trim();
    }
}
