package com.nexus.bot.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexus.bot.BotManager;
import com.nexus.bot.Util;
import com.nexus.bot.ai.Settings;
import com.nexus.bot.body.FakeBot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** /register e /login automaticos quando o servidor tem esses comandos (SimpleLogin etc.). */
public class AuthManager {
    private static final Map<String, String> PASS = new HashMap<>();
    private static final Random R = new Random();
    private static final String CH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nexusbot-auth.json"); }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, String> m = new Gson().fromJson(Files.readString(f), new TypeToken<Map<String, String>>() {}.getType());
                if (m != null) PASS.putAll(m);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void save() {
        try { Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(PASS)); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public static String password(String name) { return PASS.get(name); }

    private static String randomPw() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(CH.charAt(R.nextInt(CH.length())));
        return sb.toString();
    }

    public static boolean serverHasAuth(MinecraftServer s) {
        var root = s.getCommandManager().getDispatcher().getRoot();
        return root.getChild("register") != null && root.getChild("login") != null;
    }

    /** persist=false para os nulls (senha descartavel, nao salva). */
    public static void onJoin(MinecraftServer server, FakeBot bot, boolean persist) {
        if (!Settings.on("autologin") || !serverHasAuth(server)) return;
        String name = bot.getName().getString(), uuid = bot.getUuidAsString();
        String pw = persist ? PASS.get(name) : null;
        if (pw != null) {
            final String p = pw;
            BotManager.schedule(server, 20, () -> Util.run(server, "execute as " + uuid + " run login " + p));
            return;
        }
        final String np = randomPw();
        if (persist) { PASS.put(name, np); save(); }
        BotManager.schedule(server, 20, () -> Util.run(server, "execute as " + uuid + " run register " + np + " " + np));
        BotManager.schedule(server, 70, () -> Util.run(server, "execute as " + uuid + " run login " + np));
    }
}
