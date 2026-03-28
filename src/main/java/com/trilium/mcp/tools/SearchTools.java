package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

import static com.trilium.mcp.ResultHelper.*;
import static com.trilium.mcp.tools.NoteTools.schema;

public final class SearchTools {

    private SearchTools() {}

    public static McpServerFeatures.SyncToolSpecification searchNotes(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("query", Map.of("type", "string", "description", "Search query string"));
        props.put("limit", Map.of("type", "integer", "description", "Maximum number of results (default: 100)"));
        props.put("orderBy", Map.of("type", "string", "description", "Field to order results by"));
        var schema = schema(props, List.of("query"));

        var tool = McpSchema.Tool.builder()
                .name("search_notes")
                .description("Search for notes using a query string. Supports Trilium's search syntax.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String query = getString(args, "query");
                        int limit = getInt(args, "limit", 100);
                        String orderBy = getOptionalString(args, "orderBy");
                        return jsonResult(client.searchNotes(query, limit, orderBy));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification searchByLabel(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("name", Map.of("type", "string", "description", "Label name to search for"));
        props.put("value", Map.of("type", "string", "description", "Label value to match (optional)"));
        var schema = schema(props, List.of("name"));

        var tool = McpSchema.Tool.builder()
                .name("search_by_label")
                .description("Search for notes by label name and optional value.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String name = getString(args, "name");
                        String value = getOptionalString(args, "value");
                        String query = (value != null) ? "#" + name + "=" + value : "#" + name;
                        return jsonResult(client.searchNotes(query, 100, null));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification searchFulltext(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("query", Map.of("type", "string", "description", "Full-text search query"));
        props.put("limit", Map.of("type", "integer", "description", "Maximum number of results (default: 100)"));
        var schema = schema(props, List.of("query"));

        var tool = McpSchema.Tool.builder()
                .name("search_fulltext")
                .description("Full-text search across all note content.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String query = getString(args, "query");
                        int limit = getInt(args, "limit", 100);
                        return jsonResult(client.searchNotes(query, limit, null));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
