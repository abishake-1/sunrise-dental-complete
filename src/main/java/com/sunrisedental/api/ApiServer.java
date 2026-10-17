package com.sunrisedental.api;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import com.sunrisedental.model.Role;
import com.sunrisedental.service.ClinicFacade;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Assembles and runs the REST server on the JDK's {@link HttpServer}.
 *
 * <p>Filter order on {@code /api/}: access log, security headers, CORS, authentication. Everything outside
 * {@code /api/} is served by the {@link StaticFileHandler} (the browser client) when a web client folder was
 * given, otherwise it answers with a JSON 404.
 */
public final class ApiServer {

    private static final int WORKER_THREADS = 16;

    private final ClinicFacade facade;
    private final ApiSettings settings;
    private final Clock clock;
    private final Path webRoot;
    private HttpServer server;
    private ExecutorService executor;

    /**
     * Creates the server (not started yet).
     *
     * @param facade   the application facade
     * @param settings security settings
     * @param clock    time source
     */
    public ApiServer(ClinicFacade facade, ApiSettings settings, Clock clock) {
        this(facade, settings, clock, null);
    }

    /**
     * Creates the server that also serves the browser client.
     *
     * @param facade   the application facade
     * @param settings security settings
     * @param clock    time source
     * @param webRoot  folder with the HTML/CSS/JavaScript client served at {@code /}, or {@code null} for none
     */
    public ApiServer(ClinicFacade facade, ApiSettings settings, Clock clock, Path webRoot) {
        this.facade = facade;
        this.settings = settings;
        this.clock = clock;
        this.webRoot = webRoot;
    }

    /**
     * Starts listening.
     *
     * @param address the address and port to bind (port 0 picks a free port)
     * @throws IOException if the port cannot be bound
     */
    public synchronized void start(InetSocketAddress address) throws IOException {
        if (server != null) {
            throw new IllegalStateException("Already started");
        }
        SessionManager sessions = new SessionManager();
        RequestValidators validators = new RequestValidators(clock);

        Router router = new Router();
        new AuthHandler(facade::login, sessions, validators).register(router);
        new AppointmentHandler(facade, validators).register(router);
        new PatientHandler(facade, validators).register(router);
        new CatalogHandler(facade).register(router);
        new BillingHandler(facade, validators,
                new ReceiptStore(settings.receiptsDirectory(), settings.receiptFormats(), settings.clinic()),
                settings.clinic()).register(router);
        new ReportHandler(facade, validators).register(router);
        new ReminderHandler(facade, validators).register(router);
        router.add("GET", "/api/health", request -> ApiResponse.ok(Json.object("status", "UP")), Role.values());

        server = HttpServer.create(address, 0);
        HttpContext api = server.createContext("/api/", router);
        api.getFilters().addAll(List.of(new AccessLogFilter(), new SecurityHeadersFilter(), new AuthFilter(sessions)));
        HttpContext root = server.createContext("/", webRoot == null
                ? exchange -> HttpResponder.sendError(exchange, ApiException.notFound("No such endpoint"))
                : new StaticFileHandler(webRoot));
        root.getFilters().addAll(List.of(new AccessLogFilter(), new SecurityHeadersFilter()));

        executor = Executors.newFixedThreadPool(WORKER_THREADS);
        server.setExecutor(executor);
        server.start();
    }

    /** @return the port the server is bound to */
    public synchronized int port() {
        return server.getAddress().getPort();
    }

    /** Stops the server, waiting up to one second for running exchanges. */
    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            executor.shutdown();
            server = null;
        }
    }
}
