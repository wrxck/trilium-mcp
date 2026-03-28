package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

import static com.trilium.mcp.ResultHelper.*;
import static com.trilium.mcp.tools.NoteTools.schema;

public final class NavigationTools {

    private NavigationTools() {}

    public static McpServerFeatures.SyncToolSpecification getChildren(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the parent note")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("get_children")
                .description("Get the direct children of a note. Returns up to 100 children with their noteId, title, and type.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        Map<String, Object> note = client.getNote(noteId);
                        Object childNoteIdsObj = note.get("childNoteIds");
                        if (!(childNoteIdsObj instanceof List<?> rawList)) {
                            return jsonResult(List.of());
                        }
                        List<?> childNoteIds = rawList;
                        int limit = Math.min(childNoteIds.size(), 100);
                        List<Map<String, Object>> children = new ArrayList<>(limit);
                        for (int i = 0; i < limit; i++) {
                            String childId = String.valueOf(childNoteIds.get(i));
                            Map<String, Object> child = client.getNote(childId);
                            children.add(Map.of(
                                    "noteId", childId,
                                    "title", Objects.toString(child.get("title"), ""),
                                    "type", Objects.toString(child.get("type"), "")
                            ));
                        }
                        return jsonResult(children);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification getNoteTree(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the root note for the tree"));
        props.put("depth", Map.of("type", "integer", "description", "Depth to traverse (default: 2, max: 10)"));
        var schema = schema(props, List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("get_note_tree")
                .description("Recursively get a note tree starting from a given note. Returns a nested structure of {noteId, title, type, children}.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        int depth = getInt(args, "depth", 2);
                        if (depth > 10) depth = 10;
                        return jsonResult(buildTree(client, noteId, depth));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildTree(TriliumClient client, String noteId, int depth) {
        Map<String, Object> note = client.getNote(noteId);
        var node = new LinkedHashMap<String, Object>();
        node.put("noteId", noteId);
        node.put("title", Objects.toString(note.get("title"), ""));
        node.put("type", Objects.toString(note.get("type"), ""));

        if (depth <= 0) {
            node.put("children", List.of());
            return node;
        }

        Object childNoteIdsObj = note.get("childNoteIds");
        List<Map<String, Object>> children = new ArrayList<>();
        if (childNoteIdsObj instanceof List<?> rawList) {
            int limit = Math.min(rawList.size(), 100);
            for (int i = 0; i < limit; i++) {
                String childId = String.valueOf(rawList.get(i));
                children.add(buildTree(client, childId, depth - 1));
            }
        }
        node.put("children", children);
        return node;
    }

    public static McpServerFeatures.SyncToolSpecification getRelatedNotes(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the note to find relations for")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("get_related_notes")
                .description("Get notes related to the given note via relation attributes. Returns a list of {relationName, targetNoteId, targetTitle}.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        List<Map<String, Object>> attributes = client.getAttributes(noteId);
                        List<Map<String, Object>> related = new ArrayList<>();
                        for (Map<String, Object> attr : attributes) {
                            if (!"relation".equals(attr.get("type"))) continue;
                            String targetId = Objects.toString(attr.get("value"), "");
                            if (targetId.isBlank()) continue;
                            String relationName = Objects.toString(attr.get("name"), "");
                            String targetTitle;
                            try {
                                Map<String, Object> target = client.getNote(targetId);
                                targetTitle = Objects.toString(target.get("title"), "");
                            } catch (Exception ex) {
                                targetTitle = "";
                            }
                            related.add(Map.of(
                                    "relationName", relationName,
                                    "targetNoteId", targetId,
                                    "targetTitle", targetTitle
                            ));
                        }
                        return jsonResult(related);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification getBacklinks(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the note to find backlinks for")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("get_backlinks")
                .description("Find notes that have a relation pointing to the given note.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        List<Map<String, Object>> results = client.searchNotes("~* = " + noteId, 100, null);
                        return jsonResult(results);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification getNotePath(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the note to get the path for")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("get_note_path")
                .description("Get the breadcrumb path from the root to the given note. Returns an ordered list of {noteId, title} from root to note.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        Deque<Map<String, Object>> path = new ArrayDeque<>();
                        String current = noteId;
                        Set<String> visited = new HashSet<>();
                        while (current != null && !visited.contains(current)) {
                            visited.add(current);
                            Map<String, Object> note = client.getNote(current);
                            path.addFirst(Map.of(
                                    "noteId", current,
                                    "title", Objects.toString(note.get("title"), "")
                            ));
                            Object parentNoteIdsObj = note.get("parentNoteIds");
                            if (parentNoteIdsObj instanceof List<?> parents && !parents.isEmpty()) {
                                current = String.valueOf(parents.get(0));
                            } else {
                                break;
                            }
                        }
                        return jsonResult(new ArrayList<>(path));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
