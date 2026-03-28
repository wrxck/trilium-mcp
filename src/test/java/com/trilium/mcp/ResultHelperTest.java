package com.trilium.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResultHelperTest {

    @Test
    void jsonResult_serializesMapToJson() {
        var result = ResultHelper.jsonResult(Map.of("key", "value", "count", 42));

        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        var text = (io.modelcontextprotocol.spec.McpSchema.TextContent) result.content().get(0);
        assertTrue(text.text().contains("key") || text.text().contains("count"));
        assertTrue(text.text().contains("value") || text.text().contains("42"));
    }

    @Test
    void sanitizedResult_wrapsContentWithBoundary() {
        var result = ResultHelper.sanitizedResult("Hello, this is note content.");

        assertFalse(result.isError());
        assertEquals(2, result.content().size());
        var first = (io.modelcontextprotocol.spec.McpSchema.TextContent) result.content().get(0);
        var second = (io.modelcontextprotocol.spec.McpSchema.TextContent) result.content().get(1);
        assertTrue(first.text().contains("UNTRUSTED"));
        assertTrue(second.text().contains("Hello, this is note content."));
    }

    @Test
    void errorResult_setsIsError() {
        var result = ResultHelper.errorResult("Something went wrong");

        assertTrue(result.isError());
        var text = (io.modelcontextprotocol.spec.McpSchema.TextContent) result.content().get(0);
        assertEquals("Something went wrong", text.text());
    }

    @Test
    void getString_throwsOnMissing() {
        var args = Map.<String, Object>of("present", "yes");

        assertThrows(IllegalArgumentException.class, () -> ResultHelper.getString(args, "missing"));
    }

    @Test
    void getInt_returnsDefault() {
        var args = Map.<String, Object>of("other", "value");

        int result = ResultHelper.getInt(args, "missing", 99);

        assertEquals(99, result);
    }

    @Test
    void getOptionalString_returnsNullWhenMissing() {
        var args = Map.<String, Object>of("present", "yes");

        String result = ResultHelper.getOptionalString(args, "absent");

        assertNull(result);
    }
}
