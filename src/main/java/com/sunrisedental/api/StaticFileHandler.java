package com.sunrisedental.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves the browser client (plain HTML, CSS and JavaScript) from a folder, so the page and the REST API share
 * one origin and no CORS configuration is needed.
 *
 * <p>Security rules:
 * <ul>
 *   <li>only {@code GET} and {@code HEAD};</li>
 *   <li>only a whitelist of file types (html, css, js, svg, png, ico, txt) - everything else is a 404;</li>
 *   <li>the request path is decoded once and rejected if it contains {@code ..}, {@code .}, empty or hidden
 *       segments, backslashes or NUL characters;</li>
 *   <li>the real path of the file (symbolic links resolved) must stay inside the served folder;</li>
 *   <li>a strict Content-Security-Policy: scripts and styles only from this server, no inline code.</li>
 * </ul>
 * {@code /} serves {@code login.html}. Requests below {@code /api/} never reach this handler because the API
 * context is more specific.
 */
public final class StaticFileHandler implements HttpHandler {

    /** Policy for pages: everything from this origin only, no inline scripts or styles, no framing. */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; "
            + "form-action 'self'; frame-ancestors 'none'";

    private static final Logger LOG = Logger.getLogger(StaticFileHandler.class.getName());
    private static final String INDEX_FILE = "login.html";
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_METHODS = List.of("GET", "HEAD");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon",
            "txt", "text/plain; charset=utf-8");

    private final Path root;

    /**
     * Creates the handler.
     *
     * @param directory the folder to serve; if it does not exist every request answers 404
     */
    public StaticFileHandler(Path directory) {
        Path resolved = null;
        try {
            if (directory != null && Files.isDirectory(directory)) {
                resolved = directory.toRealPath();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read the web client folder " + directory, e);
        }
        this.root = resolved;
        if (resolved == null) {
            LOG.warning(() -> "Web client folder not found: " + directory + " (set SUNRISE_FRONTEND_DIR)");
        }
    }

    /** @return the real path of the served folder, or {@code null} if it does not exist */
    public Path root() {
        return root;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            serve(exchange);
        } catch (IOException | RuntimeException e) {
            HttpResponder.sendError(exchange, e);
        }
    }

    private void serve(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!ALLOWED_METHODS.contains(method)) {
            throw ApiException.methodNotAllowed(ALLOWED_METHODS);
        }
        Path file = locate(exchange.getRequestURI().getPath());
        if (file == null) {
            throw ApiException.notFound("No such page");
        }
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            throw ApiException.notFound("No such page");
        }
        String etag = "\"" + Long.toHexString(Files.getLastModifiedTime(file).toMillis()) + "-"
                + Long.toHexString(size) + "\"";

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", CONTENT_TYPES.get(extension(file.getFileName().toString())));
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        headers.set("Cache-Control", "no-cache");
        headers.set("ETag", etag);

        String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        byte[] body = Files.readAllBytes(file);
        if ("HEAD".equals(method)) {
            headers.set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    /**
     * Maps a decoded URL path to a servable file.
     *
     * @param decodedPath the request path, already percent-decoded
     * @return the real file path inside the root, or {@code null} if the request must be answered with 404
     * @throws IOException if the file system cannot be read
     */
    Path locate(String decodedPath) throws IOException {
        if (root == null || decodedPath == null || decodedPath.indexOf('\0') >= 0 || decodedPath.indexOf('\\') >= 0
                || !decodedPath.startsWith("/")) {
            return null;
        }
        String relative = "/".equals(decodedPath) ? INDEX_FILE : decodedPath.substring(1);
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.startsWith(".")) {
                return null;   // empty (// or trailing /), ".", ".." and hidden files
            }
        }
        if (!CONTENT_TYPES.containsKey(extension(relative))) {
            return null;
        }
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            return null;
        }
        Path real = candidate.toRealPath();           // follows symbolic links
        return real.startsWith(root) ? real : null;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
