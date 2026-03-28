package com.trilium.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
