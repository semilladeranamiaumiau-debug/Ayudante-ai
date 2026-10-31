package com.daniel.ayudante;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Convierte el estado del jugador en texto para que la IA "vea" lo que pasa en el juego. (Nombres oficiales de Mojang) */
public final class ContextBuilder {
    private ContextBuilder() {}

    public static String build(ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();

        String dimension;
        if (level.dimension() == Level.OVERWORLD) dimension = "Overworld";
        else if (level.dimension() == Level.NETHER) dimension = "Nether";
        else if (level.dimension() == Level.END) dimension = "End";
        else dimension = level.dimension().toString();

        String biome = level.getBiome(pos).getRegisteredName();
        String clima = level.isThundering() ? "tormenta" : (level.isRaining() ? "lluvia" : "despejado");

        List<Monster> hostiles = level.getEntitiesOfClass(
                Monster.class, player.getBoundingBox().inflate(16), e -> e.isAlive());

        StringBuilder sb = new StringBuilder();
        sb.append("[ESTADO DEL JUEGO]\n");
        sb.append("Jugador: ").append(player.getName().getString()).append('\n');
        sb.append("Posición: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append('\n');
        sb.append("Dimensión: ").append(dimension).append(" | Bioma: ").append(biome).append('\n');
        sb.append("Clima: ").append(clima).append('\n');
        sb.append("Luz del cielo: ").append(level.getBrightness(LightLayer.SKY, pos))
          .append(" | Luz de bloque: ").append(level.getBrightness(LightLayer.BLOCK, pos)).append('\n');
        sb.append("Vida: ").append(Math.round(player.getHealth())).append("/20")
          .append(" | Hambre: ").append(player.getFoodData().getFoodLevel()).append("/20")
          .append(" | Nivel XP: ").append(player.experienceLevel).append('\n');

        ItemStack hand = player.getMainHandItem();
        sb.append("En la mano: ").append(hand.isEmpty() ? "nada" : hand.getCount() + "x " + hand.getHoverName().getString()).append('\n');

        sb.append("Armadura: ");
        boolean any = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = player.getItemBySlot(slot);
            if (!a.isEmpty()) { sb.append(a.getHoverName().getString()).append("; "); any = true; }
        }
        sb.append(any ? "" : "ninguna").append('\n');

        sb.append("Inventario: ").append(summarizeInventory(player)).append('\n');
        sb.append("Mobs hostiles a 16 bloques: ").append(hostiles.size());
        if (!hostiles.isEmpty()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Monster h : hostiles) counts.merge(h.getName().getString(), 1, Integer::sum);
            sb.append(" (").append(counts).append(')');
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String summarizeInventory(ServerPlayer player) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) totals.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
        }
        if (totals.isEmpty()) return "vacío";
        StringBuilder sb = new StringBuilder();
        totals.forEach((k, v) -> sb.append(v).append("x ").append(k).append(", "));
        return sb.substring(0, sb.length() - 2);
    }
}
