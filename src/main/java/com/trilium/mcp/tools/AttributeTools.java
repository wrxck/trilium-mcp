package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

import static com.trilium.mcp.ResultHelper.*;
import static com.trilium.mcp.tools.NoteTools.schema;

public final class AttributeTools {

    private AttributeTools() {}

    public static McpServerFeatures.SyncToolSpecification attributeList(TriliumClient client) {
        var schema = schema(Map.of(
                "noteId", Map.of("type", "string", "description", "ID of the note whose attributes to list")),
                List.of("noteId"));

        var tool = McpSchema.Tool.builder()
                .name("attribute_list")
                .description("List all attributes (labels and relations) on a note.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        return jsonResult(client.getAttributes(noteId));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification attributeCreate(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the note to attach the attribute to"));
        props.put("type", Map.of("type", "string", "description", "Attribute type: label or relation"));
        props.put("name", Map.of("type", "string", "description", "Attribute name"));
        props.put("value", Map.of("type", "string", "description", "Attribute value"));
        var schema = schema(props, List.of("noteId", "type", "name", "value"));

        var tool = McpSchema.Tool.builder()
                .name("attribute_create")
                .description("Create a new attribute (label or relation) on a note.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        var attr = new LinkedHashMap<String, Object>();
                        attr.put("noteId", getString(args, "noteId"));
                        attr.put("type", getString(args, "type"));
                        attr.put("name", getString(args, "name"));
                        attr.put("value", getString(args, "value"));
                        return jsonResult(client.createAttribute(attr));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification attributeUpdate(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("attributeId", Map.of("type", "string", "description", "ID of the attribute to update"));
        props.put("value", Map.of("type", "string", "description", "New value for the attribute"));
        var schema = schema(props, List.of("attributeId", "value"));

        var tool = McpSchema.Tool.builder()
                .name("attribute_update")
                .description("Update the value of an existing attribute.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String attributeId = getString(args, "attributeId");
                        var updates = Map.<String, Object>of("value", getString(args, "value"));
                        return jsonResult(client.updateAttribute(attributeId, updates));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification attributeDelete(TriliumClient client) {
        var schema = schema(Map.of(
                "attributeId", Map.of("type", "string", "description", "ID of the attribute to delete")),
                List.of("attributeId"));

        var tool = McpSchema.Tool.builder()
                .name("attribute_delete")
                .description("Delete an attribute by ID.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String attributeId = getString(args, "attributeId");
                        client.deleteAttribute(attributeId);
                        return jsonResult(Map.of("success", true));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    public static McpServerFeatures.SyncToolSpecification attributeBulkSet(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the note to set labels on"));
        props.put("labels", Map.of("type", "object", "description", "Map of label name to value"));
        var schema = schema(props, List.of("noteId", "labels"));

        var tool = McpSchema.Tool.builder()
                .name("attribute_bulk_set")
                .description("Bulk set labels on a note. Existing labels with matching names are updated; new ones are created.")
                .inputSchema(schema)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");

                        @SuppressWarnings("unchecked")
                        var labelsInput = (Map<String, Object>) args.get("labels");
                        if (labelsInput == null) {
                            return errorResult("Missing required parameter: labels");
                        }

                        var existing = client.getAttributes(noteId);
                        int created = 0;
                        int updated = 0;

                        for (var entry : labelsInput.entrySet()) {
                            String name = entry.getKey();
                            String value = String.valueOf(entry.getValue());

                            Optional<Map<String, Object>> match = existing.stream()
                                    .filter(a -> "label".equals(a.get("type")) && name.equals(a.get("name")))
                                    .findFirst();

                            if (match.isPresent()) {
                                String attributeId = String.valueOf(match.get().get("attributeId"));
                                client.updateAttribute(attributeId, Map.of("value", value));
                                updated++;
                            } else {
                                var attr = new LinkedHashMap<String, Object>();
                                attr.put("noteId", noteId);
                                attr.put("type", "label");
                                attr.put("name", name);
                                attr.put("value", value);
                                client.createAttribute(attr);
                                created++;
                            }
                        }

                        return jsonResult(Map.of("created", created, "updated", updated));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
