package com.sunrisedental.client;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point of the Swing desktop client (tier 1). It is a separate program from the server and only
 * talks to it over HTTP. Set {@code SUNRISE_API_URL} to use a server other than {@code http://localhost:8080}.
 */
public final class ClientApplication {

    private static final String DEFAULT_URL = "http://localhost:8080";

    private ClientApplication() {
    }

    /**
     * Starts the client.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        String url = System.getenv("SUNRISE_API_URL");
        String baseUrl = url == null || url.isBlank() ? DEFAULT_URL : url.trim();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look and feel is fine
        }
        SwingUtilities.invokeLater(() -> new LoginFrame(new ApiClient(baseUrl)).setVisible(true));
    }
}
