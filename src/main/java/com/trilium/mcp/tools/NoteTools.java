package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

import static com.trilium.mcp.ResultHelper.*;

public final class NoteTools {

    private NoteTools() {}

    @SuppressWarnings("unchecked")
    static McpSchema.JsonSchema schema(Map<String, ?> properties, List<String> required) {
        return new McpSchema.JsonSchema("object",
                (Map<String, Object>) (Map<?, ?>) properties, required, false, null, null);
    }

    public static McpServerFeatures.SyncToolSpecification noteGet(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the note to retrieve")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("note_get")
                .description("Get a note by ID. Returns note metadata including title, type, and attributes.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        return jsonResult(client.getNote(noteId));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification noteGetContent(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the note whose content to retrieve")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("note_get_content")
                .description("Get the raw content of a note by ID. Returns the note body (HTML or plain text).")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        return sanitizedResult(client.getNoteContent(noteId));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification noteCreate(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("parentNoteId", Map.of("type", "string", "description", "ID of the parent note"));
        props.put("title", Map.of("type", "string", "description", "Title of the new note"));
        props.put("type", Map.of("type", "string", "description", "Note type (default: text)"));
        props.put("content", Map.of("type", "string", "description", "Initial content of the note (default: empty)"));
        var schema = schema(props, List.of("parentNoteId", "title"));

        var tool = McpSchema.Tool.builder()
                .name("note_create")
                .description("Create a new note under a parent note.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String parentNoteId = getString(args, "parentNoteId");
                        String title = getString(args, "title");
                        String type = getOptionalString(args, "type");
                        if (type == null) type = "text";
                        String content = getOptionalString(args, "content");
                        if (content == null) content = "";
                        return jsonResult(client.createNote(parentNoteId, title, type, content));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification noteUpdate(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the note to update"));
        props.put("title", Map.of("type", "string", "description", "New title for the note"));
        props.put("type", Map.of("type", "string", "description", "New type for the note"));
        var schema = schema(props, List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("note_update")
                .description("Update a note's metadata (title and/or type).")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        var updates = new LinkedHashMap<String, Object>();
                        String title = getOptionalString(args, "title");
                        if (title != null) updates.put("title", title);
                        String type = getOptionalString(args, "type");
                        if (type != null) updates.put("type", type);
                        return jsonResult(client.updateNote(noteId, updates));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification noteSetContent(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the note"));
        props.put("content", Map.of("type", "string", "description", "New content to set"));
        var schema = schema(props, List.of("noteId", "content"));

        var tool = McpSchema.Tool.builder()
                .name("note_set_content")
                .description("Set (overwrite) the content of a note.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        String content = getString(args, "content");
                        client.setNoteContent(noteId, content);
                        return jsonResult(Map.of("success", true));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification noteDelete(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the note to delete")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("note_delete")
                .description("Delete a note by ID. This action cannot be undone.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        client.deleteNote(noteId);
                        return jsonResult(Map.of("success", true));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification noteExport(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the note to export"));
        props.put("format", Map.of("type", "string", "description", "Export format (default: html)"));
        var schema = schema(props, List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("note_export")
                .description("Export a note as a file. Returns the content as a Base64-encoded string.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        String format = getOptionalString(args, "format");
                        if (format == null) format = "html";
                        byte[] bytes = client.exportNote(noteId, format);
                        String encoded = Base64.getEncoder().encodeToString(bytes);
                        return jsonResult(Map.of("format", format, "data", encoded));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
