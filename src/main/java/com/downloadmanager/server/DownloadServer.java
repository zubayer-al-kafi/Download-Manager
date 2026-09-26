package com.downloadmanager.server;

import com.downloadmanager.controller.MainController;
import com.downloadmanager.model.DownloadRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DownloadServer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static HttpServer server;
    private static MainController mainController;

    public static void start(MainController controller) {
        mainController = controller;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8765), 0);
            server.createContext("/download", DownloadServer::handleDownload);
            server.start();
            System.out.println("Download server started on port 8765.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleDownload(HttpExchange exchange) throws IOException {
        /* Handle CORS */
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            sendResponse(exchange, 204, "");
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        /* Read JSON body */
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        DownloadRequest request = objectMapper.readValue(body, DownloadRequest.class);

        System.out.println("Download received from extension: " + request.getUrl());

        /*
         * THE FIX:
         * Send ALL URLs (both Normal and YouTube) directly to the JavaFX UI Thread.
         * The MainController will automatically detect YouTube links and open the Quality Selector!
         */
        Platform.runLater(() -> {
            mainController.receiveUrlFromExtension(request.getUrl());
        });

        sendResponse(exchange, 200, "Download received");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] data = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
        exchange.sendResponseHeaders(statusCode, data.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("Download server stopped.");
        }
    }
}