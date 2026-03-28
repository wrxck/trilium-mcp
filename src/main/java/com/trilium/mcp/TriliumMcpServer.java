package com.trilium.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trilium.mcp.tools.*;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TriliumMcpServer {

    private static final Logger log = LoggerFactory.getLogger(TriliumMcpServer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        try {
            if (args.length > 0 && "--init".equals(args[0])) {
                String claudeBinary = null;
                for (int i = 1; i < args.length - 1; i++) {
                    if ("--claude-binary".equals(args[i])) {
                        claudeBinary = args[i + 1];
                        break;
                    }
                }
                TriliumAuth.init(claudeBinary);
                return;
            }
            var config = TriliumAuth.getConfig();
            var rateLimiter = new RateLimiter();
            var client = new TriliumClient(config.url(), config.token(), rateLimiter);
            startServer(client);
        } catch (Exception e) {
            log.error("Failed to start Trilium MCP server", e);
            System.exit(1);
        }
    }

    private static void startServer(TriliumClient client) {
        var transport = new StdioServerTransportProvider(
                new JacksonMcpJsonMapper(OBJECT_MAPPER));

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("trilium", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(
                        // NoteTools (7)
                        NoteTools.noteGet(client),
                        NoteTools.noteGetContent(client),
                        NoteTools.noteCreate(client),
                        NoteTools.noteUpdate(client),
                        NoteTools.noteSetContent(client),
                        NoteTools.noteDelete(client),
                        NoteTools.noteExport(client),
                        // AttributeTools (5)
                        AttributeTools.attributeList(client),
                        AttributeTools.attributeCreate(client),
                        AttributeTools.attributeUpdate(client),
                        AttributeTools.attributeDelete(client),
                        AttributeTools.attributeBulkSet(client),
                        // SearchTools (3)
                        SearchTools.searchNotes(client),
                        SearchTools.searchByLabel(client),
                        SearchTools.searchFulltext(client),
                        // NavigationTools (5)
                        NavigationTools.getChildren(client),
                        NavigationTools.getNoteTree(client),
                        NavigationTools.getRelatedNotes(client),
                        NavigationTools.getBacklinks(client),
                        NavigationTools.getNotePath(client),
                        // BranchTools (3)
                        BranchTools.branchMove(client),
                        BranchTools.branchClone(client),
                        BranchTools.branchReorder(client),
                        // SystemTools (3)
                        SystemTools.appInfo(client),
                        SystemTools.triggerBackup(client),
                        SystemTools.getDayNote(client),
                        // OntologyTools — read (6)
                        OntologyTools.listProjects(client),
                        OntologyTools.getProject(client),
                        OntologyTools.listUnhealthy(client),
                        OntologyTools.listDomains(client),
                        OntologyTools.getDomain(client),
                        OntologyTools.getSslExpiring(client),
                        // OntologyWriteTools — write (6)
                        OntologyWriteTools.createDeployment(client),
                        OntologyWriteTools.createIncident(client),
                        OntologyWriteTools.resolveIncident(client),
                        OntologyWriteTools.updateContainerStatus(client),
                        OntologyWriteTools.refreshDashboard(client),
                        OntologyWriteTools.addRunbook(client)
                )
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Trilium MCP server");
            server.close();
        }));

        log.info("Trilium MCP server started");
    }
}
