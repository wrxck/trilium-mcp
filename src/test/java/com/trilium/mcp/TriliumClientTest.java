package com.trilium.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriliumClientTest {

    private static final String BASE = "http://localhost:8080";
    private static final String UNREACHABLE = "http://localhost:1";
    private static final String TOKEN = "token123";

    @Test
    void constructor_rejectsBlankUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> new TriliumClient("", TOKEN, new RateLimiter()));
    }

    @Test
    void constructor_rejectsBlankToken() {
        assertThrows(IllegalArgumentException.class,
                () -> new TriliumClient(BASE, "", new RateLimiter()));
    }

    @Test
    void constructor_stripsTrailingSlash() {
        var client = new TriliumClient(BASE + "/", TOKEN, new RateLimiter());
        assertEquals(BASE, client.getBaseUrl());
    }

    @Test
    void constructor_stripsMultipleTrailingSlashes() {
        var client = new TriliumClient(BASE + "///", TOKEN, new RateLimiter());
        assertEquals(BASE, client.getBaseUrl());
    }

    @Test
    void constructor_preservesUrlWithNoTrailingSlash() {
        var client = new TriliumClient(BASE, TOKEN, new RateLimiter());
        assertEquals(BASE, client.getBaseUrl());
    }

    @Test
    void getNote_throwsOnInvalidNoteId() {
        var client = new TriliumClient(BASE, TOKEN, new RateLimiter());
        assertThrows(IllegalArgumentException.class,
                () -> client.getNote("../../../etc/passwd"));
    }

    @Test
    void getNote_throwsOnEmptyNoteId() {
        var client = new TriliumClient(BASE, TOKEN, new RateLimiter());
        assertThrows(IllegalArgumentException.class,
                () -> client.getNote(""));
    }

    @Test
    void getNote_throwsOnNoteIdWithSpaces() {
        var client = new TriliumClient(BASE, TOKEN, new RateLimiter());
        assertThrows(IllegalArgumentException.class,
                () -> client.getNote("note id with spaces"));
    }

    @Test
    void getNote_throwsOnTooLongNoteId() {
        var client = new TriliumClient(BASE, TOKEN, new RateLimiter());
        String longId = "a".repeat(51);
        assertThrows(IllegalArgumentException.class,
                () -> client.getNote(longId));
    }

    @Test
    void getNote_allowsSpecialIds() {
        // "root" is a valid note id — will get connection refused on unreachable host
        var client = new TriliumClient(UNREACHABLE, TOKEN, new RateLimiter());
        var ex = assertThrows(TriliumClient.TriliumApiException.class,
                () -> client.getNote("root"));
        assertEquals(0, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Cannot connect"));
    }

    @Test
    void getNote_allowsAlphanumericUnderscoreIds() {
        // valid format — will get connection refused, not IllegalArgumentException
        var client = new TriliumClient(UNREACHABLE, TOKEN, new RateLimiter());
        assertThrows(TriliumClient.TriliumApiException.class,
                () -> client.getNote("abc123_XYZ"));
    }
}
