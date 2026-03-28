package com.trilium.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.List;
import java.util.Map;

public final class ResultHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ResultHelper() {}

    public static CallToolResult sanitizedResult(String content) {
        String boundary = ContentSanitizer.generateBoundary();
        String wrapped = ContentSanitizer.wrapContent(content, boundary);
        String securityContext = ContentSanitizer.buildSecurityContext(boundary);
        return new CallToolResult(List.of(
                new McpSchema.TextContent(securityContext),
                new McpSchema.TextContent(wrapped)), false);
    }

    public static CallToolResult jsonResult(Object data) {
        try {
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            return new CallToolResult(List.of(new McpSchema.TextContent(json)), false);
        } catch (Exception e) {
            return errorResult("Failed to serialise result: " + e.getMessage());
        }
    }

    public static CallToolResult errorResult(String message) {
        return new CallToolResult(List.of(new McpSchema.TextContent(message)), true);
    }

    public static String getString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return String.valueOf(value);
    }

    public static int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    public static String getOptionalString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) return null;
        return String.valueOf(value);
    }

    public static boolean getBoolean(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
