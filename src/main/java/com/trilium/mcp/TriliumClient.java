package com.trilium.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class TriliumClient {

    private static final Logger log = LoggerFactory.getLogger(TriliumClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NOTE_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_]{1,50}");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String token;
    private final RateLimiter rateLimiter;

    TriliumClient(String baseUrl, String token, RateLimiter rateLimiter) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.rateLimiter = rateLimiter;
    }

    String getBaseUrl() {
        return baseUrl;
    }

    // --- notes ---

    Map<String, Object> getNote(String noteId) {
        validateNoteId(noteId);
        return getJson("/etapi/notes/" + noteId);
    }

    String getNoteContent(String noteId) {
        validateNoteId(noteId);
        return getString("/etapi/notes/" + noteId + "/content");
    }

    Map<String, Object> createNote(String parentNoteId, String title, String type, String content) {
        var body = Map.of(
                "parentNoteId", parentNoteId,
                "title", title,
                "type", type,
                "content", content
        );
        return postJson("/etapi/create-note", body);
    }

    Map<String, Object> updateNote(String noteId, Map<String, Object> updates) {
        validateNoteId(noteId);
        return patchJson("/etapi/notes/" + noteId, updates);
    }

    void setNoteContent(String noteId, String content) {
        validateNoteId(noteId);
        putString("/etapi/notes/" + noteId + "/content", content);
    }

    void deleteNote(String noteId) {
        validateNoteId(noteId);
        delete("/etapi/notes/" + noteId);
    }

    byte[] exportNote(String noteId, String format) {
        validateNoteId(noteId);
        return getBytes("/etapi/notes/" + noteId + "/export?format=" + encode(format));
    }

    // --- attributes ---

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> getAttributes(String noteId) {
        validateNoteId(noteId);
        Map<String, Object> resp = getJson("/etapi/notes/" + noteId + "/attributes");
        Object attrs = resp.get("attributes");
        if (attrs instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    Map<String, Object> createAttribute(Map<String, Object> attr) {
        return postJson("/etapi/attributes", attr);
    }

    Map<String, Object> updateAttribute(String attributeId, Map<String, Object> updates) {
        return patchJson("/etapi/attributes/" + encode(attributeId), updates);
    }

    void deleteAttribute(String attributeId) {
        delete("/etapi/attributes/" + encode(attributeId));
    }

    // --- search ---

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> searchNotes(String query, int limit, String orderBy) {
        String path = "/etapi/notes?search=" + encode(query)
                + "&limit=" + limit
                + "&orderBy=" + encode(orderBy);
        Map<String, Object> resp = getJson(path);
        Object results = resp.get("results");
        if (results instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    // --- branches ---

    Map<String, Object> createBranch(Map<String, Object> branch) {
        return postJson("/etapi/branches", branch);
    }

    Map<String, Object> updateBranch(String branchId, Map<String, Object> updates) {
        return patchJson("/etapi/branches/" + encode(branchId), updates);
    }

    void deleteBranch(String branchId) {
        delete("/etapi/branches/" + encode(branchId));
    }

    // --- system ---

    Map<String, Object> getAppInfo() {
        return getJson("/etapi/app-info");
    }

    void triggerBackup(String name) {
        put("/etapi/backup/" + encode(name));
    }

    Map<String, Object> getDayNote(String date) {
        return getJson("/etapi/calendar/days/" + encode(date));
    }

    // --- internal http methods ---

    private Map<String, Object> getJson(String path) {
        rateLimiter.checkAndRecord();
        try {
            var req = request(path).GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            return MAPPER.readValue(resp.body(), new TypeReference<>() {});
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private String getString(String path) {
        rateLimiter.checkAndRecord();
        try {
            var req = request(path).GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            return resp.body();
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private byte[] getBytes(String path) {
        rateLimiter.checkAndRecord();
        try {
            var req = request(path).GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            checkStatus(resp);
            return resp.body();
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private Map<String, Object> postJson(String path, Object body) {
        rateLimiter.checkAndRecord();
        try {
            String json = MAPPER.writeValueAsString(body);
            var req = request(path)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            return MAPPER.readValue(resp.body(), new TypeReference<>() {});
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private Map<String, Object> patchJson(String path, Object body) {
        rateLimiter.checkAndRecord();
        try {
            String json = MAPPER.writeValueAsString(body);
            var req = request(path)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            return MAPPER.readValue(resp.body(), new TypeReference<>() {});
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private void putString(String path, String body) {
        rateLimiter.checkAndRecord();
        try {
            var req = request(path)
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "text/html")
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private void put(String path) {
        rateLimiter.checkAndRecord();
        try {
            var req = request(path)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private void delete(String path) {
        rateLimiter.checkAndRecord();
        try {
            var req = request(path)
                    .DELETE()
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
        } catch (TriliumApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw new TriliumApiException(0, "Cannot connect to Trilium at " + baseUrl);
        } catch (Exception e) {
            throw new TriliumApiException(0, "Request failed: " + e.getMessage());
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", token);
    }

    private <T> void checkStatus(HttpResponse<T> resp) {
        int status = resp.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401 || status == 403) {
            throw new TriliumApiException(status, "Authentication failed — check your ETAPI token");
        }
        throw new TriliumApiException(status, "Trilium API returned HTTP " + status);
    }

    private void validateNoteId(String noteId) {
        if (noteId == null || !NOTE_ID_PATTERN.matcher(noteId).matches()) {
            throw new IllegalArgumentException("Invalid note id: " + noteId);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // --- exception ---

    static final class TriliumApiException extends RuntimeException {
        private final int statusCode;

        TriliumApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        int getStatusCode() {
            return statusCode;
        }
    }
}
