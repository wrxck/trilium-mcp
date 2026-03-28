package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.trilium.mcp.ResultHelper.*;
import static com.trilium.mcp.tools.NoteTools.schema;
import static com.trilium.mcp.tools.OntologyTools.findFolderByTitle;
import static com.trilium.mcp.tools.OntologyTools.getLabelValue;

/**
 * Write/mutation ontology tools: create deployments, incidents, runbooks,
 * resolve incidents, update container health, refresh dashboard.
 */
public final class OntologyWriteTools {

    private OntologyWriteTools() {}

    // --- tool 7: create_deployment ---

    public static McpServerFeatures.SyncToolSpecification createDeployment(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("projectNoteId", Map.of("type", "string", "description", "ID of the project note this deployment belongs to"));
        props.put("version", Map.of("type", "string", "description", "Version string for this deployment"));
        props.put("commit", Map.of("type", "string", "description", "Git commit SHA for this deployment"));
        props.put("branch", Map.of("type", "string", "description", "Git branch deployed from"));
        props.put("status", Map.of("type", "string", "description", "Deployment status (default: success)"));
        var tool = McpSchema.Tool.builder()
                .name("create_deployment")
                .description("Create a deployment record note under the Deployments folder. Links to the project via a belongsTo relation.")
                .inputSchema(schema(props, List.of("projectNoteId")))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String projectNoteId = getString(args, "projectNoteId");
                        String version = getOptionalString(args, "version");
                        String commit = getOptionalString(args, "commit");
                        String branch = getOptionalString(args, "branch");
                        String status = getOptionalString(args, "status");
                        if (status == null) status = "success";

                        Map<String, Object> projectNote = client.getNote(projectNoteId);
                        String projectName = Objects.toString(projectNote.get("title"), projectNoteId);
                        String deployedAt = Instant.now().toString();
                        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
                        String title = date + " " + projectName;

                        String content = "<table>"
                                + "<tr><th>Field</th><th>Value</th></tr>"
                                + "<tr><td>Project</td><td>" + projectName + "</td></tr>"
                                + "<tr><td>Version</td><td>" + Objects.toString(version, "") + "</td></tr>"
                                + "<tr><td>Commit</td><td>" + Objects.toString(commit, "") + "</td></tr>"
                                + "<tr><td>Branch</td><td>" + Objects.toString(branch, "") + "</td></tr>"
                                + "<tr><td>Status</td><td>" + status + "</td></tr>"
                                + "<tr><td>Deployed At</td><td>" + deployedAt + "</td></tr>"
                                + "</table>";

                        String folderId = findFolderByTitle(client, "Deployments");
                        Map<String, Object> created = client.createNote(folderId, title, "text", content);
                        String newNoteId = String.valueOf(((Map<?, ?>) created.get("note")).get("noteId"));

                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "entityType", "value", "deployment"));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "project", "value", projectName));
                        if (version != null) client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "version", "value", version));
                        if (commit != null) client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "commit", "value", commit));
                        if (branch != null) client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "branch", "value", branch));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "status", "value", status));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "deployedAt", "value", deployedAt));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "relation", "name", "belongsTo", "value", projectNoteId));

                        return jsonResult(Map.of("noteId", newNoteId, "title", title, "deployedAt", deployedAt));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 8: create_incident ---

    public static McpServerFeatures.SyncToolSpecification createIncident(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("projectNoteId", Map.of("type", "string", "description", "ID of the project note this incident relates to"));
        props.put("severity", Map.of("type", "string", "description", "Incident severity: P1, P2, P3, or P4"));
        props.put("description", Map.of("type", "string", "description", "Short description of the incident"));
        var tool = McpSchema.Tool.builder()
                .name("create_incident")
                .description("Create an incident record note under the Incidents folder. Links to the project via a belongsTo relation. Status is set to 'open'.")
                .inputSchema(schema(props, List.of("projectNoteId", "severity", "description")))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String projectNoteId = getString(args, "projectNoteId");
                        String severity = getString(args, "severity");
                        String description = getString(args, "description");

                        Map<String, Object> projectNote = client.getNote(projectNoteId);
                        String projectName = Objects.toString(projectNote.get("title"), projectNoteId);
                        String detectedAt = Instant.now().toString();
                        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
                        String truncated = description.length() > 50 ? description.substring(0, 50) : description;
                        String title = date + " " + severity + " " + truncated;

                        String folderId = findFolderByTitle(client, "Incidents");
                        Map<String, Object> created = client.createNote(folderId, title, "text", "<p>" + description + "</p>");
                        String newNoteId = String.valueOf(((Map<?, ?>) created.get("note")).get("noteId"));

                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "entityType", "value", "incident"));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "project", "value", projectName));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "severity", "value", severity));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "status", "value", "open"));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "detectedAt", "value", detectedAt));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "relation", "name", "belongsTo", "value", projectNoteId));

                        return jsonResult(Map.of("noteId", newNoteId, "title", title, "detectedAt", detectedAt, "status", "open"));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 9: resolve_incident ---

    public static McpServerFeatures.SyncToolSpecification resolveIncident(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("noteId", Map.of("type", "string", "description", "ID of the incident note to resolve"));
        props.put("rootCause", Map.of("type", "string", "description", "Root cause description for the incident"));
        var tool = McpSchema.Tool.builder()
                .name("resolve_incident")
                .description("Resolve an open incident. Updates the status label to 'resolved', adds a resolvedAt timestamp, and records the root cause.")
                .inputSchema(schema(props, List.of("noteId", "rootCause")))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        String rootCause = getString(args, "rootCause");

                        List<Map<String, Object>> attrs = client.getAttributes(noteId);
                        for (Map<String, Object> attr : attrs) {
                            if ("label".equals(attr.get("type")) && "status".equals(attr.get("name"))) {
                                client.updateAttribute(String.valueOf(attr.get("attributeId")), Map.of("value", "resolved"));
                                break;
                            }
                        }

                        String resolvedAt = Instant.now().toString();
                        client.createAttribute(Map.of("noteId", noteId, "type", "label", "name", "resolvedAt", "value", resolvedAt));
                        client.createAttribute(Map.of("noteId", noteId, "type", "label", "name", "rootCause", "value", rootCause));

                        return jsonResult(Map.of("noteId", noteId, "status", "resolved", "resolvedAt", resolvedAt));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 10: update_container_status ---

    @SuppressWarnings("unchecked")
    public static McpServerFeatures.SyncToolSpecification updateContainerStatus(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("update_container_status")
                .description("Bulk update container health statuses. Accepts a map of containerName → health value and updates the matching container notes.")
                .inputSchema(schema(Map.of(
                        "statuses", Map.of("type", "object", "description", "Map of containerName to health value (e.g. healthy, unhealthy, restarting)")),
                        List.of("statuses")))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        Object statusesObj = args.get("statuses");
                        if (!(statusesObj instanceof Map<?, ?> rawMap)) {
                            return errorResult("statuses must be an object");
                        }
                        Map<String, Object> statuses = (Map<String, Object>) rawMap;
                        int updated = 0;
                        for (Map.Entry<String, Object> entry : statuses.entrySet()) {
                            String containerName = entry.getKey();
                            String health = String.valueOf(entry.getValue());
                            List<Map<String, Object>> results = client.searchNotes(
                                    "#entityType=container #containerName=" + containerName, 1, null);
                            if (results.isEmpty()) continue;
                            String noteId = String.valueOf(results.get(0).get("noteId"));
                            List<Map<String, Object>> attrs = client.getAttributes(noteId);
                            boolean found = false;
                            for (Map<String, Object> attr : attrs) {
                                if ("label".equals(attr.get("type")) && "health".equals(attr.get("name"))) {
                                    client.updateAttribute(String.valueOf(attr.get("attributeId")), Map.of("value", health));
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                client.createAttribute(Map.of("noteId", noteId, "type", "label", "name", "health", "value", health));
                            }
                            updated++;
                        }
                        return jsonResult(Map.of("updated", updated));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 11: refresh_dashboard ---

    public static McpServerFeatures.SyncToolSpecification refreshDashboard(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("refresh_dashboard")
                .description("Regenerate the 'Status Overview' dashboard note with current infrastructure stats: project count, container count, and unhealthy container count.")
                .inputSchema(schema(Map.of(), List.of()))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        List<Map<String, Object>> dashResults = client.searchNotes("note.title = Status Overview", 1, null);
                        if (dashResults.isEmpty()) {
                            return errorResult("Dashboard note 'Status Overview' not found");
                        }
                        String dashNoteId = String.valueOf(dashResults.get(0).get("noteId"));

                        int projectCount = client.searchNotes("#entityType=project", 100, null).size();
                        int containerCount = client.searchNotes("#entityType=container", 100, null).size();
                        int unhealthyCount = client.searchNotes("#entityType=container #health=unhealthy", 100, null).size()
                                + client.searchNotes("#entityType=container #health=restarting", 100, null).size();
                        String timestamp = Instant.now().toString();

                        String content = "<h2>Status Overview</h2>"
                                + "<p><em>Last updated: " + timestamp + "</em></p>"
                                + "<table>"
                                + "<tr><th>Metric</th><th>Count</th></tr>"
                                + "<tr><td>Projects</td><td>" + projectCount + "</td></tr>"
                                + "<tr><td>Containers</td><td>" + containerCount + "</td></tr>"
                                + "<tr><td>Unhealthy / Restarting</td><td>" + unhealthyCount + "</td></tr>"
                                + "</table>";

                        client.setNoteContent(dashNoteId, content);
                        return jsonResult(Map.of(
                                "success", true,
                                "updatedAt", timestamp,
                                "projects", projectCount,
                                "containers", containerCount,
                                "unhealthy", unhealthyCount
                        ));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 12: add_runbook ---

    public static McpServerFeatures.SyncToolSpecification addRunbook(TriliumClient client) {
        var props = new LinkedHashMap<String, Map<String, Object>>();
        props.put("title", Map.of("type", "string", "description", "Title of the runbook note"));
        props.put("content", Map.of("type", "string", "description", "HTML content for the runbook"));
        var tool = McpSchema.Tool.builder()
                .name("add_runbook")
                .description("Create a new runbook note under the Runbooks folder with the given title and HTML content. Tags the note with entityType=runbook.")
                .inputSchema(schema(props, List.of("title", "content")))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String title = getString(args, "title");
                        String content = getString(args, "content");

                        String folderId = findFolderByTitle(client, "Runbooks");
                        Map<String, Object> created = client.createNote(folderId, title, "text", content);
                        String newNoteId = String.valueOf(((Map<?, ?>) created.get("note")).get("noteId"));
                        client.createAttribute(Map.of("noteId", newNoteId, "type", "label", "name", "entityType", "value", "runbook"));

                        return jsonResult(Map.of("noteId", newNoteId, "title", title));
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
