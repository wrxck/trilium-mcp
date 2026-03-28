package com.trilium.mcp.tools;

import com.trilium.mcp.TriliumClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static com.trilium.mcp.ResultHelper.*;
import static com.trilium.mcp.tools.NoteTools.schema;

/**
 * Read-only ontology query tools: list/get projects, domains, containers, SSL expiry.
 * Write/mutation tools live in OntologyWriteTools.
 */
public final class OntologyTools {

    private OntologyTools() {}

    // --- package-visible helpers (shared with OntologyWriteTools) ---

    static String findFolderByTitle(TriliumClient client, String title) {
        var results = client.searchNotes("note.title = " + title, 1, null);
        if (results.isEmpty()) throw new RuntimeException("Folder not found: " + title);
        return String.valueOf(results.get(0).get("noteId"));
    }

    static String getLabelValue(List<Map<String, Object>> attrs, String name) {
        return attrs.stream()
                .filter(a -> "label".equals(a.get("type")) && name.equals(a.get("name")))
                .map(a -> String.valueOf(a.get("value")))
                .findFirst().orElse(null);
    }

    // --- tool 1: list_projects ---

    public static McpServerFeatures.SyncToolSpecification listProjects(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("list_projects")
                .description("List all projects in the infrastructure ontology. Searches for notes tagged #entityType=project and returns key labels: appName, status, port, and linked domains.")
                .inputSchema(schema(Map.of(), List.of()))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        List<Map<String, Object>> notes = client.searchNotes("#entityType=project", 100, null);
                        List<Map<String, Object>> projects = new ArrayList<>();
                        for (Map<String, Object> note : notes) {
                            String noteId = String.valueOf(note.get("noteId"));
                            List<Map<String, Object>> attrs = client.getAttributes(noteId);
                            var project = new LinkedHashMap<String, Object>();
                            project.put("noteId", noteId);
                            project.put("title", Objects.toString(note.get("title"), ""));
                            project.put("appName", getLabelValue(attrs, "appName"));
                            project.put("status", getLabelValue(attrs, "status"));
                            project.put("port", getLabelValue(attrs, "port"));
                            project.put("appType", getLabelValue(attrs, "appType"));
                            project.put("serviceName", getLabelValue(attrs, "serviceName"));
                            project.put("composePath", getLabelValue(attrs, "composePath"));
                            project.put("usesSharedDb", getLabelValue(attrs, "usesSharedDb"));
                            List<String> domains = attrs.stream()
                                    .filter(a -> "relation".equals(a.get("type")) && "hasDomain".equals(a.get("name")))
                                    .map(a -> String.valueOf(a.get("value")))
                                    .collect(Collectors.toList());
                            project.put("domains", domains);
                            projects.add(project);
                        }
                        return jsonResult(projects);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 2: get_project ---

    public static McpServerFeatures.SyncToolSpecification getProject(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("get_project")
                .description("Get full details for a project note, including all attributes and linked domains, repositories, and containers via relations.")
                .inputSchema(schema(Map.of(
                        "noteId", Map.of("type", "string", "description", "ID of the project note")),
                        List.of("noteId")))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        Map<String, Object> note = client.getNote(noteId);
                        List<Map<String, Object>> attrs = client.getAttributes(noteId);

                        var result = new LinkedHashMap<String, Object>();
                        result.put("noteId", noteId);
                        result.put("title", Objects.toString(note.get("title"), ""));
                        result.put("appName", getLabelValue(attrs, "appName"));
                        result.put("appType", getLabelValue(attrs, "appType"));
                        result.put("port", getLabelValue(attrs, "port"));
                        result.put("status", getLabelValue(attrs, "status"));
                        result.put("composePath", getLabelValue(attrs, "composePath"));
                        result.put("serviceName", getLabelValue(attrs, "serviceName"));
                        result.put("usesSharedDb", getLabelValue(attrs, "usesSharedDb"));

                        List<Map<String, Object>> domains = new ArrayList<>();
                        List<Map<String, Object>> repos = new ArrayList<>();
                        List<Map<String, Object>> containers = new ArrayList<>();

                        for (Map<String, Object> attr : attrs) {
                            if (!"relation".equals(attr.get("type"))) continue;
                            String relName = String.valueOf(attr.get("name"));
                            String targetId = String.valueOf(attr.get("value"));
                            if (targetId.isBlank()) continue;
                            try {
                                Map<String, Object> target = client.getNote(targetId);
                                List<Map<String, Object>> targetAttrs = client.getAttributes(targetId);
                                var linked = new LinkedHashMap<String, Object>();
                                linked.put("noteId", targetId);
                                linked.put("title", Objects.toString(target.get("title"), ""));
                                if ("hasDomain".equals(relName)) {
                                    linked.put("sslExpiry", getLabelValue(targetAttrs, "sslExpiry"));
                                    domains.add(linked);
                                } else if ("hasRepo".equals(relName)) {
                                    repos.add(linked);
                                } else if ("hasContainer".equals(relName)) {
                                    linked.put("containerName", getLabelValue(targetAttrs, "containerName"));
                                    linked.put("health", getLabelValue(targetAttrs, "health"));
                                    linked.put("image", getLabelValue(targetAttrs, "image"));
                                    containers.add(linked);
                                }
                            } catch (Exception ignored) {}
                        }

                        result.put("domains", domains);
                        result.put("repositories", repos);
                        result.put("containers", containers);
                        return jsonResult(result);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 3: list_unhealthy ---

    public static McpServerFeatures.SyncToolSpecification listUnhealthy(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("list_unhealthy")
                .description("List all containers with health status 'unhealthy' or 'restarting'.")
                .inputSchema(schema(Map.of(), List.of()))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        List<Map<String, Object>> unhealthy = client.searchNotes(
                                "#entityType=container #health=unhealthy", 100, null);
                        List<Map<String, Object>> restarting = client.searchNotes(
                                "#entityType=container #health=restarting", 100, null);
                        List<Map<String, Object>> combined = new ArrayList<>(unhealthy);
                        combined.addAll(restarting);
                        return jsonResult(combined);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 4: list_domains ---

    public static McpServerFeatures.SyncToolSpecification listDomains(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("list_domains")
                .description("List all domains in the ontology. Returns each domain with its sslExpiry and linked project, sorted by SSL expiry date.")
                .inputSchema(schema(Map.of(), List.of()))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        List<Map<String, Object>> notes = client.searchNotes("#entityType=domain", 100, null);
                        List<Map<String, Object>> domains = new ArrayList<>();
                        for (Map<String, Object> note : notes) {
                            String noteId = String.valueOf(note.get("noteId"));
                            List<Map<String, Object>> attrs = client.getAttributes(noteId);
                            var domain = new LinkedHashMap<String, Object>();
                            domain.put("noteId", noteId);
                            domain.put("title", Objects.toString(note.get("title"), ""));
                            domain.put("sslExpiry", getLabelValue(attrs, "sslExpiry"));
                            domain.put("project", getLabelValue(attrs, "project"));
                            domains.add(domain);
                        }
                        domains.sort(Comparator.comparing(
                                d -> Objects.toString(d.get("sslExpiry"), ""),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ));
                        return jsonResult(domains);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 5: get_domain ---

    public static McpServerFeatures.SyncToolSpecification getDomain(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("get_domain")
                .description("Get full details for a domain note, including SSL expiry and the linked project via the belongsTo relation.")
                .inputSchema(schema(Map.of(
                        "noteId", Map.of("type", "string", "description", "ID of the domain note")),
                        List.of("noteId")))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        String noteId = getString(args, "noteId");
                        Map<String, Object> note = client.getNote(noteId);
                        List<Map<String, Object>> attrs = client.getAttributes(noteId);

                        var result = new LinkedHashMap<String, Object>();
                        result.put("noteId", noteId);
                        result.put("title", Objects.toString(note.get("title"), ""));
                        result.put("sslExpiry", getLabelValue(attrs, "sslExpiry"));
                        result.put("project", getLabelValue(attrs, "project"));

                        Map<String, Object> linkedProject = null;
                        for (Map<String, Object> attr : attrs) {
                            if ("relation".equals(attr.get("type")) && "belongsTo".equals(attr.get("name"))) {
                                String targetId = String.valueOf(attr.get("value"));
                                if (!targetId.isBlank()) {
                                    try {
                                        Map<String, Object> proj = client.getNote(targetId);
                                        List<Map<String, Object>> projAttrs = client.getAttributes(targetId);
                                        linkedProject = new LinkedHashMap<>();
                                        linkedProject.put("noteId", targetId);
                                        linkedProject.put("title", Objects.toString(proj.get("title"), ""));
                                        linkedProject.put("appName", getLabelValue(projAttrs, "appName"));
                                        linkedProject.put("status", getLabelValue(projAttrs, "status"));
                                    } catch (Exception ignored) {}
                                }
                                break;
                            }
                        }
                        result.put("linkedProject", linkedProject);
                        return jsonResult(result);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // --- tool 6: get_ssl_expiring ---

    public static McpServerFeatures.SyncToolSpecification getSslExpiring(TriliumClient client) {
        var tool = McpSchema.Tool.builder()
                .name("get_ssl_expiring")
                .description("List domains whose SSL certificates expire within N days (default 30). Parses sslExpiry labels and returns results sorted by expiry date.")
                .inputSchema(schema(Map.of(
                        "days", Map.of("type", "integer", "description", "Number of days ahead to check (default: 30)")),
                        List.of()))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
                        int days = getInt(args, "days", 30);
                        LocalDate threshold = LocalDate.now(ZoneOffset.UTC).plusDays(days);

                        List<Map<String, Object>> notes = client.searchNotes("#entityType=domain", 100, null);
                        List<Map<String, Object>> expiring = new ArrayList<>();
                        for (Map<String, Object> note : notes) {
                            String noteId = String.valueOf(note.get("noteId"));
                            List<Map<String, Object>> attrs = client.getAttributes(noteId);
                            String sslExpiry = getLabelValue(attrs, "sslExpiry");
                            if (sslExpiry == null) continue;
                            try {
                                LocalDate expiryDate = LocalDate.parse(sslExpiry);
                                if (!expiryDate.isAfter(threshold)) {
                                    var domain = new LinkedHashMap<String, Object>();
                                    domain.put("noteId", noteId);
                                    domain.put("title", Objects.toString(note.get("title"), ""));
                                    domain.put("sslExpiry", sslExpiry);
                                    domain.put("project", getLabelValue(attrs, "project"));
                                    domain.put("daysUntilExpiry", (int) LocalDate.now(ZoneOffset.UTC)
                                            .until(expiryDate, java.time.temporal.ChronoUnit.DAYS));
                                    expiring.add(domain);
                                }
                            } catch (Exception ignored) {}
                        }
                        expiring.sort(Comparator.comparing(d -> Objects.toString(d.get("sslExpiry"), "")));
                        return jsonResult(expiring);
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }
}
