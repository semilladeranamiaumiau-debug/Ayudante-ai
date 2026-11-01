package com.daniel.ayudante;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AyudanteIA implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ayudante_ia");

    private Config config;
    private final AiClient client = new AiClient();
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        config = Config.load();

        // /ia <mensaje> | /ia reiniciar | /ia recargar
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("ia")
                        .then(Commands.literal("reiniciar").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            client.reset(p.getUUID());
                            reply(p, "Memoria de la conversación borrada.", ChatFormatting.GRAY);
                            return 1;
                        }))
                        .then(Commands.literal("recargar").executes(ctx -> {
                            config = Config.load();
                            ctx.getSource().sendSuccess(() -> Component.literal("Config recargada."), false);
                            return 1;
                        }))
                        .then(Commands.argument("mensaje", StringArgumentType.greedyString()).executes(ctx -> {
                            handle(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mensaje"));
                            return 1;
                        }))));

        // También por chat: escribe "ia como hago una granja de hierro"
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.decoratedContent().getString();
            if (text.toLowerCase().startsWith(config.chatPrefix.toLowerCase())) {
                handle(sender, text.substring(config.chatPrefix.length()).trim());
            }
        });

        LOGGER.info("Ayudante IA cargado.");
    }

    private void handle(ServerPlayer player, String question) {
        if (question.isBlank()) return;

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUUID());
        if (last != null && now - last < config.cooldownSeconds * 1000L) {
            reply(player, "Espera un momento antes de preguntar otra vez.", ChatFormatting.RED);
            return;
        }
        lastUse.put(player.getUUID(), now);

        // El contexto se lee en el hilo del servidor; la llamada HTTP corre en segundo plano.
        String context = ContextBuilder.build(player, question);
        reply(player, "pensando...", ChatFormatting.GRAY);

        var server = player.level().getServer();
        client.ask(player.getUUID(), context, question, config).whenComplete((answer, err) ->
                server.execute(() -> {
                    if (err != null) {
                        Throwable cause = err.getCause() != null ? err.getCause() : err;
                        LOGGER.error("Error consultando la IA", cause);
                        reply(player, String.valueOf(cause.getMessage()), ChatFormatting.RED);
                    } else {
                        for (String line : answer.split("\n")) {
                            if (!line.isBlank()) reply(player, line, ChatFormatting.WHITE);
                        }
                    }
                }));
    }

    private void reply(ServerPlayer player, String msg, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[" + config.nombreAyudante + "] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(msg).withStyle(color)));
    }
}
