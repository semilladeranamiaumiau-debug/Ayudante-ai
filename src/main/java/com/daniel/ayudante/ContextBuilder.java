package com.daniel.ayudante;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Convierte TODO lo que se puede saber del mundo y del jugador en texto,
 * para que la IA "vea" lo que pasa. (Nombres oficiales de Mojang)
 */
public final class ContextBuilder {
    private ContextBuilder() {}

    // Cuánto mira alrededor del jugador (en bloques)
    private static final int RADIO_XZ = 12;
    private static final int RADIO_Y = 10;

    /** Bloques que le interesan al jugador y que vale la pena reportar. */
    private static final Set<String> INTERESANTES = Set.of(
            "chest", "trapped_chest", "ender_chest", "barrel", "spawner", "trial_spawner", "vault",
            "lava", "crafting_table", "furnace", "blast_furnace", "smoker", "enchanting_table",
            "anvil", "brewing_stand", "beacon", "end_portal_frame", "nether_portal", "end_portal",
            "bookshelf", "obsidian", "tnt", "sculk_shrieker", "ancient_debris");

    private static final class Hallazgo {
        int cantidad = 0;
        int x, y, z;
        int mejorDist = Integer.MAX_VALUE;
    }

    public static String build(ServerPlayer player) {
        return build(player, "");
    }

    public static String build(ServerPlayer player, String pregunta) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        StringBuilder sb = new StringBuilder();

        sb.append("[ESTADO DEL JUEGO]\n");

        // ---------- MUNDO ----------
        sb.append("== MUNDO ==\n");
        sb.append("Dimensión: ").append(dimName(level.dimension())).append('\n');
        sb.append("Día número: ").append(level.getGameTime() / 24000L + 1).append('\n');
        sb.append("Dificultad: ").append(level.getDifficulty())
          .append(level.getLevelData().isHardcore() ? " (HARDCORE: si mueres, pierdes el mundo)" : "").append('\n');
        sb.append("Clima: ").append(level.isThundering() ? "tormenta" : (level.isRaining() ? "lluvia" : "despejado")).append('\n');

        // ---------- JUGADOR ----------
        sb.append("== JUGADOR ==\n");
        sb.append("Nombre: ").append(player.getName().getString()).append('\n');
        sb.append("Posición: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append('\n');
        sb.append("Entorno: ")
          .append(level.canSeeSky(pos) ? "a cielo abierto" : "bajo techo o bajo tierra")
          .append(player.isInWater() ? ", dentro del agua" : "")
          .append(player.isInLava() ? ", DENTRO DE LAVA" : "")
          .append(player.isOnFire() ? ", ARDIENDO" : "").append('\n');
        sb.append("Luz del cielo: ").append(level.getBrightness(LightLayer.SKY, pos))
          .append(" | Luz de bloque: ").append(level.getBrightness(LightLayer.BLOCK, pos)).append('\n');
        sb.append("Vida: ").append(Math.round(player.getHealth())).append("/20")
          .append(" | Hambre: ").append(player.getFoodData().getFoodLevel()).append("/20")
          .append(" | Nivel XP: ").append(player.experienceLevel).append('\n');

        StringBuilder efectos = new StringBuilder();
        for (MobEffectInstance fx : player.getActiveEffects()) {
            efectos.append(fx.getEffect().getRegisteredName())
                   .append(" nivel ").append(fx.getAmplifier() + 1)
                   .append(" (").append(fx.getDuration() / 20).append("s), ");
        }
        sb.append("Efectos activos: ").append(efectos.length() == 0 ? "ninguno" : efectos.substring(0, efectos.length() - 2)).append('\n');

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

        player.getLastDeathLocation().ifPresent(g -> sb.append("Última muerte: ")
                .append(g.pos().getX()).append(", ").append(g.pos().getY()).append(", ").append(g.pos().getZ())
                .append(" en ").append(dimName(g.dimension())).append('\n'));

        // ---------- ALREDEDORES ----------
        sb.append("== ALREDEDORES ==\n");
        sb.append("Biomas cercanos (hasta 48 bloques): ").append(nearbyBiomes(level, pos)).append('\n');
        sb.append("Bloques notables a ").append(RADIO_XZ).append(" bloques: ").append(scanBlocks(level, pos)).append('\n');
        appendEntities(sb, level, player);

        String radar = radar(player, pregunta == null ? "" : pregunta.toLowerCase());
        if (!radar.isEmpty()) sb.append("== RADAR (datos reales del mundo) ==\n").append(radar);

        return sb.toString();
    }


    // ======================================================================= RADAR
    // Solo se activa cuando la pregunta menciona algo que buscar, para no gastar recursos.
    // Solo en un jugador (singleplayer): en servidores multijugador es trampa, así que se desactiva.

    private record Mineral(String patron, String sufijo, String nombre, int minY, int maxY) {}

    private static final List<Mineral> MINERALES = List.of(
            new Mineral("diamantes?|diamonds?", "diamond_ore", "Diamantes", -64, 16),
            new Mineral("esmeraldas?|emeralds?", "emerald_ore", "Esmeraldas", -16, 100),
            new Mineral("oro|gold", "gold_ore", "Oro", -64, 48),
            new Mineral("hierro|iron", "iron_ore", "Hierro", -64, 72),
            new Mineral("redstone", "redstone_ore", "Redstone", -64, 16),
            new Mineral("lapis|lapislazuli", "lapis_ore", "Lapislázuli", -64, 64),
            new Mineral("escombros antiguos|netherita|ancient debris", "ancient_debris", "Escombros antiguos", 0, 40));

    private static boolean pide(String q, String regex) {
        return Pattern.compile("\\b(" + regex + ")\\b").matcher(q).find();
    }

    private static String radar(ServerPlayer player, String q) {
        boolean aldea = pide(q, "aldeas?|villages?|pueblos?|radar");
        boolean fortaleza = pide(q, "fortalezas?|strongholds?");
        boolean mansion = pide(q, "mansion|mansiones|woodland mansion");
        boolean monumento = pide(q, "monumentos?|ocean monument");
        boolean tesoro = pide(q, "tesoros?|treasure");
        List<Mineral> minerales = new ArrayList<>();
        for (Mineral mi : MINERALES) {
            if (pide(q, mi.patron()) || (pide(q, "radar") && mi.sufijo().equals("diamond_ore"))) minerales.add(mi);
        }
        if (!aldea && !fortaleza && !mansion && !monumento && !tesoro && minerales.isEmpty()) return "";

        var server = player.level().getServer();
        if (server == null || !server.isSingleplayer()) {
            return "Radar desactivado: solo funciona en partidas de un jugador (en servidores sería trampa).\n";
        }

        StringBuilder sb = new StringBuilder();
        if (aldea) sb.append(estructura(player, "VILLAGE", "Aldea")).append('\n');
        if (fortaleza) sb.append(estructura(player, "EYE_OF_ENDER_LOCATED", "Fortaleza (stronghold)")).append('\n');
        if (mansion) sb.append(estructura(player, "ON_WOODLAND_EXPLORER_MAPS", "Mansión del bosque")).append('\n');
        if (monumento) sb.append(estructura(player, "ON_OCEAN_EXPLORER_MAPS", "Monumento oceánico")).append('\n');
        if (tesoro) sb.append(estructura(player, "ON_TREASURE_MAPS", "Tesoro enterrado")).append('\n');
        for (Mineral mi : minerales) sb.append(escanearMineral(player, mi)).append('\n');
        return sb.toString();
    }

    /** Busca la estructura más cercana igual que /locate. Usa reflexión para no romperse si cambian nombres. */
    private static BlockPos buscarEstructura(ServerPlayer player, String campoTag) {
        try {
            Class<?> tags = Class.forName("net.minecraft.tags.StructureTags");
            Object tag = tags.getField(campoTag).get(null);
            Object level = player.level();
            for (Method m : level.getClass().getMethods()) {
                if (m.getName().equals("findNearestMapStructure") && m.getParameterCount() == 4) {
                    Object r = m.invoke(level, tag, player.blockPosition(), 64, false);
                    if (r instanceof BlockPos p) return p;
                    return null;
                }
            }
        } catch (Throwable t) {
            AyudanteIA.LOGGER.warn("No se pudo buscar la estructura {}: {}", campoTag, t.toString());
        }
        return null;
    }

    private static String estructura(ServerPlayer player, String campo, String nombre) {
        BlockPos p = buscarEstructura(player, campo);
        if (p == null) {
            return nombre + ": no encontrada en unos 1000 bloques a la redonda en esta dimensión (o la búsqueda no está disponible).";
        }
        BlockPos yo = player.blockPosition();
        double dx = p.getX() - yo.getX();
        double dz = p.getZ() - yo.getZ();
        return nombre + " más cercana: X " + p.getX() + " Z " + p.getZ()
                + " (a unos " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " bloques; la altura Y exacta no se sabe)";
    }

    /** Escanea los chunks ya cargados cerca del jugador buscando un mineral. */
    private static String escanearMineral(ServerPlayer player, Mineral mi) {
        Level level = player.level();
        BlockPos yo = player.blockPosition();
        final int RADIO_CHUNKS = 5;
        int pcx = yo.getX() >> 4;
        int pcz = yo.getZ() >> 4;

        List<int[]> hallados = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int cx = pcx - RADIO_CHUNKS; cx <= pcx + RADIO_CHUNKS; cx++) {
            for (int cz = pcz - RADIO_CHUNKS; cz <= pcz + RADIO_CHUNKS; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = mi.minY(); y <= mi.maxY(); y++) {
                            int wx = (cx << 4) + x;
                            int wz = (cz << 4) + z;
                            m.set(wx, y, wz);
                            BlockState st = chunk.getBlockState(m);
                            if (st.isAir()) continue;
                            if (!st.getBlock().getDescriptionId().endsWith(mi.sufijo())) continue;
                            int ddx = wx - yo.getX(), ddy = y - yo.getY(), ddz = wz - yo.getZ();
                            hallados.add(new int[]{wx, y, wz, ddx * ddx + ddy * ddy + ddz * ddz});
                        }
                    }
                }
            }
        }

        if (hallados.isEmpty()) {
            return mi.nombre() + ": ninguno en los chunks cargados (unos " + (RADIO_CHUNKS * 16)
                    + " bloques a la redonda, entre Y " + mi.minY() + " y " + mi.maxY()
                    + "). Más lejos hay que explorar; no se sabe.";
        }
        hallados.sort(Comparator.comparingInt(a -> a[3]));
        StringBuilder sb = new StringBuilder();
        sb.append(mi.nombre()).append(": ").append(hallados.size()).append(" bloques en chunks cargados. Más cercanos: ");
        int n = 0;
        for (int[] h : hallados) {
            if (n++ >= 5) break;
            sb.append("X ").append(h[0]).append(" Y ").append(h[1]).append(" Z ").append(h[2])
              .append(" (a ").append(Math.round(Math.sqrt(h[3]))).append("); ");
        }
        return sb.substring(0, sb.length() - 2);
    }

    // ---------------------------------------------------------------- helpers

    private static String dimName(ResourceKey<Level> key) {
        if (key == Level.OVERWORLD) return "Overworld";
        if (key == Level.NETHER) return "Nether";
        if (key == Level.END) return "End";
        return key.toString();
    }

    private static String nearbyBiomes(Level level, BlockPos pos) {
        Set<String> biomas = new LinkedHashSet<>();
        for (int dx = -48; dx <= 48; dx += 16) {
            for (int dz = -48; dz <= 48; dz += 16) {
                biomas.add(level.getBiome(pos.offset(dx, 0, dz)).getRegisteredName());
            }
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String b : biomas) {
            if (n++ >= 8) break;
            sb.append(b).append(", ");
        }
        return sb.length() == 0 ? "desconocidos" : sb.substring(0, sb.length() - 2);
    }

    /** Cuenta minerales, cofres, lava, bases de crafteo, troncos... y anota el más cercano de cada uno. */
    private static String scanBlocks(Level level, BlockPos pos) {
        Map<String, Hallazgo> hallazgos = new TreeMap<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int dx = -RADIO_XZ; dx <= RADIO_XZ; dx++) {
            for (int dy = -RADIO_Y; dy <= RADIO_Y; dy++) {
                for (int dz = -RADIO_XZ; dz <= RADIO_XZ; dz++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState st = level.getBlockState(m);
                    if (st.isAir()) continue;

                    String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    String clave = clasificar(id);
                    if (clave == null) continue;

                    Hallazgo h = hallazgos.computeIfAbsent(clave, k -> new Hallazgo());
                    h.cantidad++;
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d < h.mejorDist) {
                        h.mejorDist = d;
                        h.x = m.getX();
                        h.y = m.getY();
                        h.z = m.getZ();
                    }
                }
            }
        }

        if (hallazgos.isEmpty()) return "nada especial";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Hallazgo> e : hallazgos.entrySet()) {
            if (n++ >= 16) break;
            Hallazgo h = e.getValue();
            sb.append(e.getKey()).append(" x").append(h.cantidad)
              .append(" (más cercano en ").append(h.x).append(' ').append(h.y).append(' ').append(h.z).append("); ");
        }
        return sb.substring(0, sb.length() - 2);
    }

    private static String clasificar(String id) {
        if (id.endsWith("_ore")) return id.replace("deepslate_", ""); // junta las versiones de piedra y deepslate
        if (id.endsWith("_bed")) return "bed";
        if (id.endsWith("_log")) return "logs";
        if (INTERESANTES.contains(id)) return id;
        return null;
    }

    private static void appendEntities(StringBuilder sb, Level level, ServerPlayer player) {
        var box = player.getBoundingBox().inflate(24);

        List<LivingEntity> vivos = level.getEntitiesOfClass(
                LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof Player));

        Map<String, Integer> hostiles = new LinkedHashMap<>();
        Map<String, Integer> pacificos = new LinkedHashMap<>();
        for (LivingEntity e : vivos) {
            String nombre = e.getName().getString();
            if (e instanceof Monster) hostiles.merge(nombre, 1, Integer::sum);
            else pacificos.merge(nombre, 1, Integer::sum);
        }
        sb.append("Mobs hostiles a 24 bloques: ").append(hostiles.isEmpty() ? "ninguno" : hostiles.toString()).append('\n');
        sb.append("Animales/aldeanos/otros a 24 bloques: ").append(pacificos.isEmpty() ? "ninguno" : pacificos.toString()).append('\n');

        List<ItemEntity> items = level.getEntitiesOfClass(
                ItemEntity.class, player.getBoundingBox().inflate(16), e -> e.isAlive());
        Map<String, Integer> suelo = new LinkedHashMap<>();
        for (ItemEntity it : items) {
            suelo.merge(it.getItem().getHoverName().getString(), it.getItem().getCount(), Integer::sum);
        }
        sb.append("Objetos tirados en el suelo (16 bloques): ").append(suelo.isEmpty() ? "ninguno" : suelo.toString()).append('\n');

        StringBuilder otros = new StringBuilder();
        for (Player p : level.players()) {
            if (p == player) continue;
            otros.append(p.getName().getString()).append(" (a ").append(Math.round(p.distanceTo(player))).append(" bloques), ");
        }
        sb.append("Otros jugadores en esta dimensión: ").append(otros.length() == 0 ? "ninguno" : otros.substring(0, otros.length() - 2)).append('\n');
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
