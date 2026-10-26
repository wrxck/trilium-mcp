package com.trilium.mcp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TriliumClientHttpVersionTest {

    private HttpServer server;

    // trilium behind docker-proxy answers 400 to an h2c upgrade, like this stub
    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/etapi/app-info", exchange -> {
            boolean upgrade = exchange.getRequestHeaders().containsKey("Upgrade");
            byte[] body = (upgrade ? "{\"message\":\"bad request\"}" : "{\"appVersion\":\"0.99.0\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(upgrade ? 400 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void getAppInfo_doesNotSendAnHttp2Upgrade() {
        var client = new TriliumClient("http://127.0.0.1:" + server.getAddress().getPort(), "token123",
                new RateLimiter());
        assertEquals("0.99.0", client.getAppInfo().get("appVersion"));
    }
}
