package com.nexus.bot.ai;

import java.util.*;

/** Configuracoes globais. Tudo vem LIGADO de fabrica, exceto "grief" (destrutivo). */
public class Settings {
    public static final List<String> TECHNIQUES = List.of("wtap", "stap", "strafing", "critchain", "jumpreset",
        "hitselect", "peakcrit", "blockhit", "maceswap", "windmace", "smarthealing", "totem", "pearl", "elytra");
    public static final List<String> STYLES = List.of("sword", "axe", "shield", "mace");
    public static final List<String> FEATURES = List.of("chat", "botchat", "respawn", "survival", "autologin", "skins", "grief");

    private static final Set<String> OFF = new HashSet<>(Set.of("grief"));
    public static int nullNickMin = 6, nullNickMax = 12, maxBots = 60;
    public static String nullNickStyle = "mixed";   // mixed | underscore | dot | random
    public static String defaultKit = "pvp", nullKit = "nulls";

    public static boolean on(String key) { return !OFF.contains(key); }
    public static void set(String key, boolean value) { if (value) OFF.remove(key); else OFF.add(key); }
    public static boolean exists(String key) { return TECHNIQUES.contains(key) || STYLES.contains(key) || FEATURES.contains(key); }
    public static List<String> all() {
        List<String> l = new ArrayList<>(TECHNIQUES); l.addAll(STYLES); l.addAll(FEATURES); return l;
    }
}
