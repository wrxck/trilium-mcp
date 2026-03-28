package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.LocalDate;
import java.util.*;

import static com.trilium.mcp.ResultHelper.*;
import static com.trilium.mcp.tools.NoteTools.schema;

public final class SystemTools {

    private SystemTools() {}

    public static McpServerFeatures.SyncToolSpecification appInfo(TriliumClient client) {
        var schema = schema(Map.of(), List.of());

        var tool = McpSchema.Tool.builder()
                .name("app_info")
                .description("Get Trilium application info including version and build details.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        return jsonResult(client.getAppInfo());
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification triggerBackup(TriliumClient client) {
        var schema = schema(Map.of(
                "name", Map.of("type", "string", "description", "Name/label for the backup")),
                List.of("name"));

        var tool = McpSchema.Tool.builder()
                .name("trigger_backup")
                .description("Trigger a named backup of the Trilium database.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String name = getString(args, "name");
                        client.triggerBackup(name);
                        return jsonResult(Map.of("success", true, "name", name));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification getDayNote(TriliumClient client) {
        var schema = schema(Map.of(
                "date", Map.of("type", "string", "description", "Date in YYYY-MM-DD format (default: today)")),
                List.of());

        var tool = McpSchema.Tool.builder()
                .name("get_day_note")
                .description("Get the day note for a given date. Defaults to today if no date is provided.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String date = getOptionalString(args, "date");
                        if (date == null || date.isBlank()) {
                            date = LocalDate.now().toString();
                        }
                        return jsonResult(client.getDayNote(date));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
