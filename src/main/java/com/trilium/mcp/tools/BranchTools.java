package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

import static com.trilium.mcp.ResultHelper.*;
import static com.trilium.mcp.tools.NoteTools.schema;

public final class BranchTools {

    private BranchTools() {}

    public static McpServerFeatures.SyncToolSpecification branchMove(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("branchId", Map.of("type", "string", "description", "ID of the branch to move"));
        props.put("newParentNoteId", Map.of("type", "string", "description", "ID of the new parent note"));
        var schema = schema(props, List.of("branchId", "newParentNoteId"));

        var tool = McpSchema.Tool.builder()
                .name("branch_move")
                .description("Move a branch to a new parent note by updating its parentNoteId.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String branchId = getString(args, "branchId");
                        String newParentNoteId = getString(args, "newParentNoteId");
                        return jsonResult(client.updateBranch(branchId, Map.of("parentNoteId", newParentNoteId)));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification branchClone(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the note to clone"));
        props.put("parentNoteId", Map.of("type", "string", "description", "ID of the parent note to clone into"));
        var schema = schema(props, List.of("noteId", "parentNoteId"));

        var tool = McpSchema.Tool.builder()
                .name("branch_clone")
                .description("Clone a note into a new parent by creating an additional branch.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        String parentNoteId = getString(args, "parentNoteId");
                        return jsonResult(client.createBranch(Map.of("noteId", noteId, "parentNoteId", parentNoteId)));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification branchReorder(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("branchId", Map.of("type", "string", "description", "ID of the branch to reorder"));
        props.put("position", Map.of("type", "integer", "description", "New position index for the branch"));
        var schema = schema(props, List.of("branchId", "position"));

        var tool = McpSchema.Tool.builder()
                .name("branch_reorder")
                .description("Change the position of a branch within its parent.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String branchId = getString(args, "branchId");
                        int position = getInt(args, "position", 0);
                        return jsonResult(client.updateBranch(branchId, Map.of("notePosition", position)));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
