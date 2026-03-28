# trilium-mcp

MCP server for TriliumNext Notes — full ETAPI access with infrastructure ontology tools

A [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server that exposes TriliumNext Notes via ETAPI to AI assistants like [Claude Code](https://docs.anthropic.com/en/docs/claude-code). Built in Java 21 using the official [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk).

## Features

38 tools across 7 categories:

### Notes

| Tool | Description |
|------|-------------|
| `note_get` | Get a note by ID — returns metadata, title, type, and attributes |
| `note_get_content` | Get the raw content of a note (HTML or plain text) |
| `note_create` | Create a new note under a parent note |
| `note_update` | Update a note's title and/or type |
| `note_set_content` | Overwrite the content of a note |
| `note_delete` | Delete a note by ID |
| `note_export` | Export a note as Base64-encoded HTML or other format |

### Search

| Tool | Description |
|------|-------------|
| `search_notes` | Search notes using Trilium's query syntax |
| `search_by_label` | Search notes by label name and optional value |
| `search_fulltext` | Full-text search across all note content |

### Attributes

| Tool | Description |
|------|-------------|
| `attribute_list` | List all labels and relations on a note |
| `attribute_create` | Create a new label or relation on a note |
| `attribute_update` | Update the value of an existing attribute |
| `attribute_delete` | Delete an attribute by ID |
| `attribute_bulk_set` | Bulk set labels on a note (create or update) |

### Navigation

| Tool | Description |
|------|-------------|
| `get_children` | Get direct children of a note |
| `get_note_tree` | Recursively traverse the note tree to a given depth |
| `get_related_notes` | Get notes linked via relation attributes |
| `get_backlinks` | Find notes that have a relation pointing to the given note |
| `get_note_path` | Get the breadcrumb path from root to a note |

### Branches

| Tool | Description |
|------|-------------|
| `branch_move` | Move a branch to a new parent note |
| `branch_clone` | Clone a note into an additional parent |
| `branch_reorder` | Change the position of a branch within its parent |

### System

| Tool | Description |
|------|-------------|
| `app_info` | Get Trilium application version and build details |
| `trigger_backup` | Trigger a named backup of the Trilium database |
| `get_day_note` | Get the day note for a given date (defaults to today) |

### Infrastructure Ontology

| Tool | Description |
|------|-------------|
| `list_projects` | List all notes tagged `#entityType=project` with key labels |
| `get_project` | Get full details for a project, including linked domains, repos, and containers |
| `list_domains` | List all domain notes, sorted by SSL expiry date |
| `get_domain` | Get full details for a domain, including linked project |
| `get_ssl_expiring` | List domains with SSL certificates expiring within N days |
| `list_unhealthy` | List containers with health status `unhealthy` or `restarting` |
| `create_deployment` | Record a deployment under the Deployments folder |
| `create_incident` | Open an incident record under the Incidents folder |
| `resolve_incident` | Resolve an open incident with a root cause |
| `update_container_status` | Bulk update container health labels |
| `refresh_dashboard` | Regenerate the Status Overview dashboard note |
| `add_runbook` | Create a runbook note under the Runbooks folder |

## Requirements

- Java 21+
- Maven 3.6+
- A running [TriliumNext Notes](https://github.com/TriliumNext/Notes) instance with ETAPI enabled

## Quick Start

### 1. Build

```bash
git clone https://github.com/wrxck/trilium-mcp.git
cd trilium-mcp
mvn clean package -q
```

This produces a single uber-jar at `target/trilium-mcp-1.0.0.jar`.

### 2. Init

```bash
java -jar target/trilium-mcp-1.0.0.jar --init
```

The init wizard:

1. Prompts for your Trilium URL (default `http://127.0.0.1:8080`) and ETAPI token
2. Validates the connection against `/etapi/app-info`
3. Saves credentials to `~/.trilium-mcp/config.properties` with `600` permissions
4. Registers the server with Claude Code via `claude mcp add`

Generate an ETAPI token in Trilium: **Options → ETAPI → Generate new token**

The init is idempotent — safe to re-run. If config already exists it skips to registration. If already registered it skips that too.

If Claude Code isn't on your `PATH`:

```bash
java -jar target/trilium-mcp-1.0.0.jar --init --claude-binary ~/.local/bin/claude
```

### 3. Verify

Restart Claude Code. Ask: *"What version of Trilium am I running?"* — the `app_info` tool will respond with your instance details.

If auto-registration failed, register manually:

```bash
claude mcp add --scope user --transport stdio trilium -- \
  java -jar /path/to/trilium-mcp-1.0.0.jar
```

## Tool Reference

### Notes

| Tool | Key params | Description |
|------|------------|-------------|
| `note_get` | `noteId` | Fetch note metadata |
| `note_get_content` | `noteId` | Fetch note body (sanitized) |
| `note_create` | `parentNoteId`, `title`, `type`, `content` | Create a note |
| `note_update` | `noteId`, `title?`, `type?` | Update title/type |
| `note_set_content` | `noteId`, `content` | Overwrite note body |
| `note_delete` | `noteId` | Delete a note |
| `note_export` | `noteId`, `format?` | Export as Base64 (default: html) |

### Search

| Tool | Key params | Description |
|------|------------|-------------|
| `search_notes` | `query`, `limit?`, `orderBy?` | Trilium query syntax search |
| `search_by_label` | `name`, `value?` | Find notes by label |
| `search_fulltext` | `query`, `limit?` | Full-text content search |

### Attributes

| Tool | Key params | Description |
|------|------------|-------------|
| `attribute_list` | `noteId` | List all labels and relations |
| `attribute_create` | `noteId`, `type`, `name`, `value` | Add a label or relation |
| `attribute_update` | `attributeId`, `value` | Change an attribute value |
| `attribute_delete` | `attributeId` | Remove an attribute |
| `attribute_bulk_set` | `noteId`, `labels` | Create/update multiple labels at once |

### Navigation

| Tool | Key params | Description |
|------|------------|-------------|
| `get_children` | `noteId` | Direct children (up to 100) |
| `get_note_tree` | `noteId`, `depth?` | Nested tree (default depth 2, max 10) |
| `get_related_notes` | `noteId` | Notes linked via relations |
| `get_backlinks` | `noteId` | Notes that link to this note |
| `get_note_path` | `noteId` | Breadcrumb path from root |

### Branches

| Tool | Key params | Description |
|------|------------|-------------|
| `branch_move` | `branchId`, `newParentNoteId` | Move to new parent |
| `branch_clone` | `noteId`, `parentNoteId` | Add a second parent |
| `branch_reorder` | `branchId`, `position` | Change sort position |

### System

| Tool | Key params | Description |
|------|------------|-------------|
| `app_info` | — | Trilium version and build info |
| `trigger_backup` | `name` | Trigger a named database backup |
| `get_day_note` | `date?` | Get the journal note for a date |

### Infrastructure Ontology

| Tool | Key params | Description |
|------|------------|-------------|
| `list_projects` | — | All `#entityType=project` notes |
| `get_project` | `noteId` | Project with linked domains, repos, containers |
| `list_domains` | — | All `#entityType=domain` notes, sorted by SSL expiry |
| `get_domain` | `noteId` | Domain with linked project |
| `get_ssl_expiring` | `days?` | Domains expiring within N days (default 30) |
| `list_unhealthy` | — | Containers with health `unhealthy` or `restarting` |
| `create_deployment` | `projectNoteId`, `version?`, `commit?`, `branch?`, `status?` | Log a deployment |
| `create_incident` | `projectNoteId`, `severity`, `description` | Open an incident (P1–P4) |
| `resolve_incident` | `noteId`, `rootCause` | Close an incident with root cause |
| `update_container_status` | `statuses` | Bulk-update container health labels |
| `refresh_dashboard` | — | Rewrite the Status Overview note |
| `add_runbook` | `title`, `content` | Create a runbook |

## Ontology Schema

The ontology tools operate on a convention of Trilium labels and relations. Notes are given an `#entityType` label to classify them:

| Entity type | Label | Description |
|-------------|-------|-------------|
| `project` | `#entityType=project` | A deployed application |
| `domain` | `#entityType=domain` | A domain name associated with a project |
| `container` | `#entityType=container` | A Docker container for a project |
| `deployment` | `#entityType=deployment` | A deployment event record |
| `incident` | `#entityType=incident` | An incident record |
| `runbook` | `#entityType=runbook` | An operational runbook |

### Project labels

| Label | Example value | Description |
|-------|---------------|-------------|
| `appName` | `my-app` | Application identifier |
| `appType` | `node`, `java` | Runtime type |
| `status` | `running`, `stopped` | Current status |
| `port` | `3000` | Exposed port |
| `serviceName` | `my-app` | Systemd/Docker service name |
| `composePath` | `docker-compose.yml` | Relative path to compose file |
| `usesSharedDb` | `postgres` | Shared database dependency |

### Relations

| Relation | Direction | Description |
|----------|-----------|-------------|
| `hasDomain` | project → domain | Domain associated with a project |
| `hasRepo` | project → repo note | Git repository link |
| `hasContainer` | project → container | Container for a project |
| `belongsTo` | domain/deployment/incident → project | Reverse link to parent project |

### Folder structure

The write tools expect these folders to exist at the top level of your note tree:

```
Deployments/
Incidents/
Runbooks/
```

The tools search for these by title using `note.title = <name>`.

## Configuration

Config is stored in `~/.trilium-mcp/config.properties` with `600` permissions:

```
~/.trilium-mcp/
  config.properties    # Created by --init
```

| Property | Description |
|----------|-------------|
| `trilium.url` | Base URL of your Trilium instance (e.g. `http://127.0.0.1:8080`) |
| `trilium.token` | ETAPI token generated in Trilium Options |

## Security

Note content is user-created data that passes through the LLM context window. This server applies defense-in-depth mitigations:

- **Random content boundaries** — Each `note_get_content` response generates a cryptographically random boundary string (via `SecureRandom`). Note body content is wrapped with this boundary so the LLM can distinguish note data from system structure. An attacker cannot embed the boundary in a note to escape the content region.
- **Security context preamble** — Every sanitized response includes a leading block instructing the LLM to treat bounded content as data only.
- **Rate limiting** — Client-side sliding window rate limiter prevents runaway API calls.
- **Input validation** — Required parameters are validated before requests are sent. Missing or blank values return a clear error rather than making a malformed API call.
- **Config protection** — The config directory uses `rwx------` permissions; the config file uses `rw-------`. The ETAPI token is never logged.

## Architecture

```
Claude Code  <--stdio (JSON-RPC)-->  trilium-mcp.jar  <--HTTP-->  TriliumNext ETAPI
                                            |
                                    ~/.trilium-mcp/
                                      config.properties
```

- **Transport** — stdio (Claude Code spawns the server as a subprocess)
- **Auth** — static ETAPI token sent as the `Authorization` header
- **Logging** — all logs go to stderr (stdout is reserved for MCP JSON-RPC)

## Development

```bash
# build
mvn clean package -q

# run tests
mvn test

# run init
java -jar target/trilium-mcp-1.0.0.jar --init

# run server (blocks on stdio — use with Claude Code or an MCP client)
java -jar target/trilium-mcp-1.0.0.jar
```

### Project structure

```
src/main/java/com/trilium/mcp/
  TriliumMcpServer.java        # Entry point, server bootstrap, tool registration
  TriliumAuth.java             # Config loading, --init flow, Claude registration
  TriliumClient.java           # ETAPI HTTP client
  ContentSanitizer.java        # Prompt injection mitigations
  RateLimiter.java             # Sliding window rate limiter
  ResultHelper.java            # Response serialisation helpers
  tools/
    NoteTools.java             # note_get, note_get_content, note_create, note_update,
                               #   note_set_content, note_delete, note_export
    SearchTools.java           # search_notes, search_by_label, search_fulltext
    AttributeTools.java        # attribute_list, attribute_create, attribute_update,
                               #   attribute_delete, attribute_bulk_set
    NavigationTools.java       # get_children, get_note_tree, get_related_notes,
                               #   get_backlinks, get_note_path
    BranchTools.java           # branch_move, branch_clone, branch_reorder
    SystemTools.java           # app_info, trigger_backup, get_day_note
    OntologyTools.java         # list_projects, get_project, list_domains, get_domain,
                               #   get_ssl_expiring, list_unhealthy
    OntologyWriteTools.java    # create_deployment, create_incident, resolve_incident,
                               #   update_container_status, refresh_dashboard, add_runbook

src/test/java/com/trilium/mcp/
  TriliumAuthTest.java         # Config loading, path resolution
  TriliumClientTest.java       # HTTP client, request construction
  ContentSanitizerTest.java    # Boundary wrapping, preamble generation
  RateLimiterTest.java         # Rate limit enforcement
  ResultHelperTest.java        # Response serialisation helpers
```

## License

[MIT](LICENSE)
