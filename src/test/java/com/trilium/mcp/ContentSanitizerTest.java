package com.trilium.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentSanitizerTest {

    @Test
    void generateBoundary_uniqueEachCall() {
        String b1 = ContentSanitizer.generateBoundary();
        String b2 = ContentSanitizer.generateBoundary();
        assertNotEquals(b1, b2);
        assertTrue(b1.startsWith("----UNTRUSTED_CONTENT_"));
        assertTrue(b2.startsWith("----UNTRUSTED_CONTENT_"));
    }

    @Test
    void wrapContent_addsBoundaryMarkers() {
        String boundary = "----UNTRUSTED_CONTENT_abc123";
        String content = "This is some note content.";

        String wrapped = ContentSanitizer.wrapContent(content, boundary);

        assertTrue(wrapped.startsWith(boundary));
        assertTrue(wrapped.endsWith(boundary));
        assertTrue(wrapped.contains(content));
    }

    @Test
    void buildSecurityContext_containsRules() {
        String boundary = "----UNTRUSTED_CONTENT_testboundary";
        String context = ContentSanitizer.buildSecurityContext(boundary);

        assertTrue(context.contains("UNTRUSTED"));
        assertTrue(context.contains(boundary));
        assertTrue(context.contains("NEVER"));
    }
}
