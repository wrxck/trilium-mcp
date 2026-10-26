package com.trilium.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TriliumAuthTest {

    @Test
    void getConfig_throwsWhenNoConfigExists(@TempDir Path tempDir) {
        var ex = assertThrows(IllegalStateException.class,
                () -> TriliumAuth.getConfig(tempDir.resolve("config.properties")));
        assertTrue(ex.getMessage().contains("Config not found"));
    }

    @Test
    void getConfig_loadsValidConfig(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(configFile, "trilium.url=http://localhost:8787\ntrilium.token=test-token-123\n");
        var config = TriliumAuth.getConfig(configFile);
        assertEquals("http://localhost:8787", config.url());
        assertEquals("test-token-123", config.token());
    }

    @Test
    void getConfig_throwsOnMissingUrl(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(configFile, "trilium.token=test-token-123\n");
        assertThrows(IllegalStateException.class, () -> TriliumAuth.getConfig(configFile));
    }

    @Test
    void getConfig_throwsOnMissingToken(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(configFile, "trilium.url=http://localhost:8787\n");
        assertThrows(IllegalStateException.class, () -> TriliumAuth.getConfig(configFile));
    }

    @Test
    void getConfig_prefersEnvironmentOverFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(configFile, "trilium.url=http://file:1\ntrilium.token=file-token\n");
        var env = Map.of("TRILIUM_URL", "http://env:8787", "TRILIUM_TOKEN", "env-token");
        var config = TriliumAuth.getConfig(configFile, env);
        assertEquals("http://env:8787", config.url());
        assertEquals("env-token", config.token());
    }

    @Test
    void getConfig_usesEnvironmentWhenNoFileExists(@TempDir Path tempDir) {
        var env = Map.of("TRILIUM_URL", "http://env:8787", "TRILIUM_ETAPI_TOKEN", "etapi-token");
        var config = TriliumAuth.getConfig(tempDir.resolve("missing.properties"), env);
        assertEquals("http://env:8787", config.url());
        assertEquals("etapi-token", config.token());
    }

    @Test
    void getConfig_fallsBackToFileWhenEnvironmentIsIncomplete(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(configFile, "trilium.url=http://file:1\ntrilium.token=file-token\n");
        var env = Map.of("TRILIUM_TOKEN", "env-token", "TRILIUM_URL", " ");
        var config = TriliumAuth.getConfig(configFile, env);
        assertEquals("http://file:1", config.url());
        assertEquals("file-token", config.token());
    }
}
