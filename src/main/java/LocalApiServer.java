import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class LocalApiServer {

    private static final int PORT = 17431;
    private HttpServer server;

    public void start(Supplier<String> masterPasswordSupplier) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/status", e -> handleStatus(e, masterPasswordSupplier));
            server.createContext("/passwords", e -> handlePasswords(e, masterPasswordSupplier));
            server.createContext("/save", e -> handleSave(e, masterPasswordSupplier));
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handleStatus(HttpExchange exchange, Supplier<String> mp) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        sendJson(exchange, "{\"unlocked\":" + (mp.get() != null) + "}");
    }

    private void handlePasswords(HttpExchange exchange, Supplier<String> mpSupplier) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String mp = mpSupplier.get();
        if (mp == null) {
            sendJson(exchange, "{\"error\":\"locked\"}");
            return;
        }

        String siteParam = parseParam(exchange.getRequestURI().getQuery(), "site");
        if (siteParam == null || siteParam.isBlank()) {
            sendJson(exchange, "[]");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        try {
            List<DatabaseManager.PasswordEntry> entries = DatabaseManager.getAllPasswords();
            boolean first = true;
            for (DatabaseManager.PasswordEntry entry : entries) {
                String s = entry.site.toLowerCase();
                String q = siteParam.toLowerCase();
                if (!s.contains(q) && !q.contains(s)) continue;

                String decrypted = DatabaseManager.decryptEntry(entry, mp);
                if (!first) json.append(",");
                first = false;
                json.append("{\"site\":\"").append(escape(entry.site))
                    .append("\",\"username\":\"").append(escape(entry.username))
                    .append("\",\"password\":\"").append(escape(decrypted))
                    .append("\"}");
            }
        } catch (Exception ex) {
            sendJson(exchange, "{\"error\":\"" + escape(ex.getMessage()) + "\"}");
            return;
        }
        json.append("]");
        sendJson(exchange, json.toString());
    }

    private void handleSave(HttpExchange exchange, Supplier<String> mpSupplier) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, "{\"error\":\"method not allowed\"}");
            return;
        }

        String mp = mpSupplier.get();
        if (mp == null) {
            sendJson(exchange, "{\"error\":\"locked\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String site = parseParam(body, "site");
        String username = parseParam(body, "username");
        String password = parseParam(body, "password");

        if (site == null || username == null || password == null || site.isBlank() || username.isBlank()) {
            sendJson(exchange, "{\"error\":\"missing fields\"}");
            return;
        }

        try {
            DatabaseManager.savePassword(site, username, password, mp);
            sendJson(exchange, "{\"success\":true}");
        } catch (Exception ex) {
            sendJson(exchange, "{\"error\":\"" + escape(ex.getMessage()) + "\"}");
        }
    }

    private String parseParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}