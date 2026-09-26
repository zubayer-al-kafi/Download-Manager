package com.downloadmanager.controller;


import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class ExtensionServer {

    public static void start(MainController controller) {
        try {
            // Create a server listening on localhost:8001
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8001), 0);

            server.createContext("/download", exchange -> {
                // 1. Handle CORS (Crucial so Chrome doesn't block the request)
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");

                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                // 2. Read the URL sent by the browser extension
                InputStream is = exchange.getRequestBody();
                String url = new String(is.readAllBytes());

                // 3. Send a "Success" response back to the browser
                String response = "URL Received";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();

                // 4. Send the URL to the JavaFX UI Thread!
                Platform.runLater(() -> {
                    controller.receiveUrlFromExtension(url);
                });
            });

            // Start the background server
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
            server.start();
            System.out.println("Browser Extension Listener running on port 8001");

        } catch (Exception e) {
            System.out.println("Could not start extension server. Port 8001 might be in use.");
        }
    }
}