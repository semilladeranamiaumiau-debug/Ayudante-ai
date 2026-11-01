package com.daniel.ayudante;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Cliente HTTP asíncrono (Anthropic u OpenAI-compatible) con memoria de conversación por jugador. */
public class AiClient {
    private static final URI ANTHROPIC_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    private static final String SYSTEM_PROMPT = """
            Eres un ayudante de IA que vive dentro de Minecraft y acompaña al jugador.
            Recibes el estado actual del juego (posición, vida, inventario, bioma, mobs cercanos) antes de cada pregunta.
            Reglas:
            - Responde en el idioma del jugador (por defecto español), de forma amistosa y clara.
            - Tus respuestas salen en el chat de Minecraft: sé breve (máximo 4-5 líneas), sin Markdown, sin emojis raros.
            - Usa el estado del juego cuando ayude: si tiene poca vida o hay mobs cerca, avisa primero.
            - Ayuda con crafteos, supervivencia, redstone, construcción, exploración, encantamientos, pociones, combate y estrategia.
            - Si no estás seguro de una receta o mecánica de la versión actual, dilo en vez de inventar.
            - Cuando des coordenadas, usa el formato X Y Z.
            """;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<UUID, Deque<JsonObject>> history = new ConcurrentHashMap<>();

    public void reset(UUID player) {
        history.remove(player);
    }

    public CompletableFuture<String> ask(UUID player, String gameContext, String userMessage, Config cfg) {
        String key = cfg.resolveKey();
        if (key.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Falta la API key. Ponla en config/ayudante-ia.json (campo apiKey)."));
        }

        final boolean anthropic = cfg.isAnthropic();
        final Deque<JsonObject> hist = history.computeIfAbsent(player, k -> new ArrayDeque<>());

        HttpRequest req;
        try {
            JsonArray messages = new JsonArray();
            if (!anthropic) messages.add(message("system", SYSTEM_PROMPT));
            synchronized (hist) {
                for (JsonObject m : hist) messages.add(m);
            }
            messages.add(message("user", gameContext + "\n[PREGUNTA]\n" + userMessage));

            JsonObject body = new JsonObject();
            body.addProperty("model", cfg.model);
            body.addProperty("max_tokens", cfg.maxTokens);
            if (anthropic) body.addProperty("system", SYSTEM_PROMPT);
            body.add("messages", messages);

            HttpRequest.Builder rb;
            if (anthropic) {
                rb = HttpRequest.newBuilder(ANTHROPIC_ENDPOINT)
                        .header("x-api-key", key)
                        .header("anthropic-version", "2023-06-01");
            } else {
                String base = cfg.baseUrl == null ? "" : cfg.baseUrl.trim();
                while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                rb = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                        .header("Authorization", "Bearer " + key);
            }
            req = rb.timeout(Duration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Config inválida (revisa baseUrl y model): " + e.getMessage()));
        }

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            int code = res.statusCode();
            if (code == 401 || code == 403) {
                throw new RuntimeException("La API key no es válida o no tiene permiso (" + code + ").");
            }
            if (code == 429) {
                throw new RuntimeException("Llegaste al límite del servicio. Espera un momento e intenta otra vez.");
            }
            if (code != 200) {
                throw new RuntimeException("Error de la API (" + code + "): " + truncate(res.body(), 200));
            }
            String text = anthropic ? extractAnthropic(res.body()) : extractOpenAi(res.body());
            if (text.isBlank()) {
                throw new RuntimeException("La IA no devolvió texto. Prueba subir maxTokens en la config.");
            }
            remember(hist, userMessage, text, cfg.maxHistory);
            return text;
        });
    }

    private static void remember(Deque<JsonObject> hist, String user, String assistant, int max) {
        synchronized (hist) {
            hist.addLast(message("user", user));
            hist.addLast(message("assistant", assistant));
            while (hist.size() > Math.max(2, max)) { // en pares, para mantener user/assistant alternados
                hist.removeFirst();
                hist.removeFirst();
            }
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static String extractAnthropic(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : root.getAsJsonArray("content")) {
            JsonObject block = el.getAsJsonObject();
            if ("text".equals(block.get("type").getAsString())) sb.append(block.get("text").getAsString());
        }
        return sb.toString().trim();
    }

    private static String extractOpenAi(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return "";
        JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (msg == null) return "";
        JsonElement content = msg.get("content");
        return (content == null || content.isJsonNull()) ? "" : content.getAsString().trim();
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
