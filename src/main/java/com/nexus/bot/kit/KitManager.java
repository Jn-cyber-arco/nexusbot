package com.nexus.bot.kit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexus.bot.Util;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Kits em .json na pasta nexusbotkit (dentro da pasta do jogo/servidor). */
public class KitManager {
    public record Entry(String slot, String item, int count) {}
    public record Kit(boolean clear, List<Entry> items) {}

    private static final Map<String, Kit> KITS = new TreeMap<>();

    public static Path dir() { return FabricLoader.getInstance().getGameDir().resolve("nexusbotkit"); }
    public static List<String> names() { return new ArrayList<>(KITS.keySet()); }
    public static boolean exists(String n) { return n != null && KITS.containsKey(n.toLowerCase()); }

    public static void load() {
        try {
            Path d = dir();
            Files.createDirectories(d);
            boolean any;
            try (var s = Files.list(d)) { any = s.anyMatch(p -> p.toString().endsWith(".json")); }
            if (!any) writeDefaults(d);
            KITS.clear();
            try (var s = Files.list(d)) {
                for (Path p : s.toList()) if (p.toString().endsWith(".json")) parse(p);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void parse(Path p) {
        try {
            String fn = p.getFileName().toString();
            String name = fn.substring(0, fn.length() - 5).toLowerCase();
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            boolean clear = !o.has("clear") || o.get("clear").getAsBoolean();
            List<Entry> items = new ArrayList<>();
            JsonArray arr = o.getAsJsonArray("items");
            if (arr != null) for (JsonElement e : arr) {
                JsonObject it = e.getAsJsonObject();
                items.add(new Entry(it.get("slot").getAsString(), it.get("item").getAsString(), it.has("count") ? it.get("count").getAsInt() : 1));
            }
            KITS.put(name, new Kit(clear, items));
        } catch (Exception e) { System.err.println("[NexusBot] kit invalido: " + p + " -> " + e.getMessage()); }
    }

    /** Aplica o kit em jogador ou bot usando os comandos /clear e /item replace do proprio Minecraft. */
    public static boolean give(MinecraftServer server, ServerPlayerEntity target, String kitName) {
        Kit k = kitName == null ? null : KITS.get(kitName.toLowerCase());
        if (k == null) return false;
        String id = target.getUuidAsString();
        if (k.clear()) Util.run(server, "clear " + id);
        for (Entry e : k.items())
            Util.run(server, "item replace entity " + id + " " + e.slot() + " with " + e.item() + " " + Math.max(1, e.count()));
        return true;
    }

    // ---------- kits de exemplo (criados so se a pasta estiver vazia) ----------
    private static String json(String... rows) {
        StringBuilder sb = new StringBuilder("{\n  \"clear\": true,\n  \"items\": [\n");
        for (int i = 0; i < rows.length; i++) {
            String[] p = rows[i].split("\\|");
            sb.append("    {\"slot\": \"").append(p[0]).append("\", \"item\": \"").append(p[1]).append("\"");
            if (p.length > 2) sb.append(", \"count\": ").append(p[2]);
            sb.append("}").append(i < rows.length - 1 ? "," : "").append("\n");
        }
        return sb.append("  ]\n}\n").toString();
    }

    private static void writeDefaults(Path d) throws Exception {
        String prot = "[enchantments={protection:4,unbreaking:3,mending:1}]";
        String[] gear = {
            "armor.head|netherite_helmet" + prot, "armor.chest|netherite_chestplate" + prot,
            "armor.legs|netherite_leggings" + prot, "armor.feet|netherite_boots[enchantments={protection:4,feather_falling:4,unbreaking:3,mending:1}]",
            "weapon.offhand|shield",
            "hotbar.0|netherite_sword[enchantments={sharpness:5,unbreaking:3,fire_aspect:2,mending:1}]",
            "hotbar.1|netherite_axe[enchantments={sharpness:5,unbreaking:3,mending:1}]",
            "hotbar.2|mace[enchantments={density:5,unbreaking:3,mending:1}]",
            "hotbar.3|wind_charge|64", "hotbar.4|golden_apple|32", "hotbar.5|totem_of_undying", "hotbar.6|ender_pearl|16"
        };
        Files.writeString(d.resolve("pvp.json"), json(gear));
        Files.writeString(d.resolve("nulls.json"), json(gear));

        List<String> el = new ArrayList<>(Arrays.asList(gear));
        el.set(1, "armor.chest|elytra[enchantments={unbreaking:3,mending:1}]");
        el.add("hotbar.7|firework_rocket|64");
        Files.writeString(d.resolve("elytra.json"), json(el.toArray(new String[0])));

        Files.writeString(d.resolve("builder.json"), json(
            "hotbar.0|cobblestone|64", "hotbar.1|cobblestone|64", "hotbar.2|oak_planks|64", "hotbar.3|oak_planks|64",
            "hotbar.4|obsidian|16", "hotbar.5|torch|64", "inventory.0|cobblestone|64", "inventory.1|cobblestone|64",
            "inventory.2|cobblestone|64", "inventory.3|oak_planks|64", "inventory.4|oak_planks|64"));

        List<String> gr = new ArrayList<>(Arrays.asList(gear));
        gr.add("hotbar.7|tnt|64"); gr.add("hotbar.8|tnt|64");
        Files.writeString(d.resolve("grief.json"), json(gr.toArray(new String[0])));
    }
}
