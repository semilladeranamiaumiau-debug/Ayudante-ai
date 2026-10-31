package com.daniel.ayudante;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuración en config/ayudante-ia.json */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String apiKey = "";                 // o usa la variable de entorno ANTHROPIC_API_KEY
    public String model = "claude-sonnet-5-5";
    public int maxTokens = 400;
    public int cooldownSeconds = 3;
    public int maxHistory = 20;                // mensajes recordados por jugador
    public String chatPrefix = "ia ";          // escribe "ia como hago una granja" en el chat
    public String nombreAyudante = "Ayudante";

    public String resolveKey() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey.trim();
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env == null ? "" : env.trim();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("ayudante-ia.json");
    }

    public static Config load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) return c;
            }
            Config def = new Config();
            Files.writeString(p, GSON.toJson(def));
            return def;
        } catch (IOException | RuntimeException e) {
            AyudanteIA.LOGGER.error("No se pudo leer la config", e);
            return new Config();
        }
    }
}
