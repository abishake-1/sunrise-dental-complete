package com.sunrisedental.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunrisedental.dto.ClinicProfile;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** The browser client is served from a folder at "/", safely, next to the protected API. */
class StaticFilesTest {

    private static final String LOGIN_PAGE = "<!DOCTYPE html><title>Login</title>";

    /** A web folder with one file outside it that must never be reachable. */
    private record Site(Path root, Path outsideSecret) {

        static Site create() throws IOException {
            Path parent = Files.createTempDirectory("sunrise-web");
            Path root = Files.createDirectory(parent.resolve("frontend"));
            Files.createDirectories(root.resolve("css"));
            Files.createDirectories(root.resolve("js"));
            Files.writeString(root.resolve("login.html"), LOGIN_PAGE, StandardCharsets.UTF_8);
            Files.writeString(root.resolve("css/styles.css"), "body{color:#000}", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("js/app.js"), "export const x = 1;", StandardCharsets.UTF_8);
            Files.writeString(root.resolve(".hidden.html"), "hidden", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("data.json"), "{}", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("notes.mjs"), "export {};", StandardCharsets.UTF_8);
            Path secret = Files.writeString(parent.resolve("secret.txt"), "TOP SECRET", StandardCharsets.UTF_8);
            return new Site(root, secret);
        }
    }

    private interface Body {
        void run(String base, Site site) throws Exception;
    }

    private static void withServer(Body body) throws Exception {
        Site site = Site.create();
        MutableClock clock = new MutableClock(Instant.parse("2026-10-11T08:00:00Z"));
        ApiSettings settings = new ApiSettings(site.root().resolveSibling("receipts"),
                EnumSet.allOf(ReceiptStore.Format.class),
                new ClinicProfile("Sunrise Dental Clinic", "12 Main Street", "0112345678", "LKR"));
        ApiServer server = new ApiServer(new FakeFacade(clock), settings, clock, site.root());
        server.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        try {
            body.run("http://localhost:" + server.port(), site);
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> send(String method, String url, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody());
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return HttpClient.newHttpClient().send(request.build(), BodyHandlers.ofString());
    }

    @Test
    void rootServesTheLoginPageWithAStrictPolicy() throws Exception {
        withServer((base, site) -> {
            HttpResponse<String> reply = send("GET", base + "/");
            assertEquals(200, reply.statusCode());
            assertEquals(LOGIN_PAGE, reply.body());
            assertEquals("text/html; charset=utf-8", reply.headers().firstValue("Content-Type").orElse(null));
            String csp = reply.headers().firstValue("Content-Security-Policy").orElse("");
            assertTrue(csp.contains("script-src 'self'"));
            assertFalse(csp.contains("unsafe-inline"));
            assertFalse(csp.contains("default-src 'none'"));
            assertEquals("nosniff", reply.headers().firstValue("X-Content-Type-Options").orElse(null));
            assertEquals("DENY", reply.headers().firstValue("X-Frame-Options").orElse(null));
        });
    }

    @Test
    void scriptsAndStylesGetTheirOwnContentTypes() throws Exception {
        withServer((base, site) -> {
            assertEquals("text/javascript; charset=utf-8",
                    send("GET", base + "/js/app.js").headers().firstValue("Content-Type").orElse(null));
            assertEquals("text/css; charset=utf-8",
                    send("GET", base + "/css/styles.css").headers().firstValue("Content-Type").orElse(null));
            assertEquals("body{color:#000}", send("GET", base + "/css/styles.css").body());
        });
    }

    @Test
    void conditionalRequestsAnswer304AndHeadHasNoBody() throws Exception {
        withServer((base, site) -> {
            HttpResponse<String> first = send("GET", base + "/js/app.js");
            String etag = first.headers().firstValue("ETag").orElse(null);
            assertNotNull(etag);
            assertEquals(304, send("GET", base + "/js/app.js", "If-None-Match", etag).statusCode());
            HttpResponse<String> head = send("HEAD", base + "/js/app.js");
            assertEquals(200, head.statusCode());
            assertEquals("", head.body());
        });
    }

    @Test
    void onlyGetAndHeadAreAllowed() throws Exception {
        withServer((base, site) -> {
            HttpResponse<String> reply = send("POST", base + "/login.html");
            assertEquals(405, reply.statusCode());
            assertEquals("GET, HEAD", reply.headers().firstValue("Allow").orElse(null));
        });
    }

    @Test
    void traversalHiddenFilesUnknownTypesAndDirectoriesAreAll404() throws Exception {
        withServer((base, site) -> {
            for (String path : new String[] {
                "/..%2fsecret.txt", "/%2e%2e/secret.txt", "/css/..%2f..%2fsecret.txt", "/%2e%2e%2fsecret.txt",
                "/..%5csecret.txt", "/css%5Cstyles.css", "/.hidden.html", "/data.json", "/notes.mjs", "/css/",
                "//css/styles.css", "/css", "/js/%00.js", "/missing.html"}) {
                HttpResponse<String> reply = send("GET", base + path);
                assertEquals(404, reply.statusCode(), path);
                assertFalse(reply.body().contains("TOP SECRET"), path);
            }
        });
    }

    @Test
    void symbolicLinkOutOfTheFolderIsNotFollowed() throws Exception {
        withServer((base, site) -> {
            try {
                Files.createSymbolicLink(site.root().resolve("leak.txt"), site.outsideSecret());
            } catch (UnsupportedOperationException | IOException | SecurityException e) {
                return;   // symbolic links are not available here (for example Windows without privilege)
            }
            assertEquals(404, send("GET", base + "/leak.txt").statusCode());
        });
    }

    @Test
    void apiStaysProtectedAndSeparate() throws Exception {
        withServer((base, site) -> {
            HttpResponse<String> reply = send("GET", base + "/api/dentists");
            assertEquals(401, reply.statusCode());
            assertTrue(reply.body().contains("\"error\""));
            assertEquals(401, send("GET", base + "/api/nothing").statusCode());
        });
    }

    @Test
    void browserPostsWithAnOriginHeaderAreAccepted() throws Exception {
        withServer((base, site) -> {
            String login = "{\"username\":\"admin\",\"password\":\"admin123\"}";
            HttpRequest.Builder own = HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                    .header("Content-Type", "application/json").header("Origin", base)
                    .POST(HttpRequest.BodyPublishers.ofString(login));
            assertEquals(200, HttpClient.newHttpClient().send(own.build(), BodyHandlers.ofString()).statusCode());
        });
    }

    @Test
    void missingFolderServesNothing() throws Exception {
        StaticFileHandler handler = new StaticFileHandler(Path.of("definitely-not-here"));
        assertNull(handler.root());
        assertNull(handler.locate("/"));
        assertNull(handler.locate("/login.html"));
    }

    @Test
    void locateRejectsEverythingOutsideTheWhitelist() throws Exception {
        Site site = Site.create();
        StaticFileHandler handler = new StaticFileHandler(site.root());
        assertNotNull(handler.locate("/"));
        assertNotNull(handler.locate("/js/app.js"));
        assertNull(handler.locate("/../secret.txt"));
        assertNull(handler.locate("/js/../../secret.txt"));
        assertNull(handler.locate("/data.json"));
        assertNull(handler.locate("relative.html"));
        assertNull(handler.locate(null));
    }
}
