package com.nexus.bot;

import com.nexus.bot.ai.*;
import com.nexus.bot.body.FakeBot;
import com.nexus.bot.chat.BotChat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class BotManager {
    private static final String[] NICKS = {"Lobo", "Sombra", "Trovao", "Ninja", "Falcao", "Mestre", "Viking", "Raio", "Pantera", "Cobra"};
    private static final Random R = new Random();

    public static final Map<String, FakeBot> BOTS = new LinkedHashMap<>();
    public static final Map<String, Set<String>> TEAMS = new HashMap<>();
    private static long lastChat = 0;

    public static String randomNick() {
        String n;
        do { n = NICKS[R.nextInt(NICKS.length)] + "_" + (10 + R.nextInt(90)); } while (BOTS.containsKey(n));
        return n;
    }

    public static FakeBot spawn(MinecraftServer server, ServerWorld world, String name, Vec3d pos, ServerPlayerEntity owner) {
        if (name == null) name = randomNick();
        if (BOTS.containsKey(name)) return null;
        FakeBot bot = FakeBot.spawn(server, world, name, pos);
        if (owner != null) bot.config.owner = owner.getName().getString();
        final PvpController pvp = new PvpController();
        final SpyController spy = new SpyController();
        bot.brain = b -> think(server, b, pvp, spy);
        bot.onDeathHook = s -> say(server, b(bot), BotChat.death());
        BOTS.put(name, bot);
        say(server, bot, BotChat.spawn());
        return bot;
    }

    private static FakeBot b(FakeBot x) { return x; }

    public static void remove(MinecraftServer server, String name) {
        FakeBot bot = BOTS.remove(name);
        if (bot != null) { bot.networkHandler.disconnect(Text.literal("removido")); server.getPlayerManager().remove(bot); }
    }

    public static void say(MinecraftServer server, FakeBot bot, String msg) {
        long now = server.getTicks();
        if (now - lastChat < 20) return;   // anti-flood
        lastChat = now;
        server.getPlayerManager().broadcast(Text.literal("<" + bot.getName().getString() + "> " + msg), false);
    }

    private static boolean sameTeam(FakeBot bot, LivingEntity other) {
        String t = bot.config.team;
        return t != null && TEAMS.getOrDefault(t, Set.of()).contains(other.getName().getString());
    }

    private static LivingEntity nearestEnemy(FakeBot bot, double range) {
        LivingEntity best = null; double bd = range;
        for (ServerPlayerEntity p : bot.getEntityWorld().getPlayers()) {
            if (p == bot || p.isSpectator() || p.isCreative() || sameTeam(bot, p)) continue;
            double d = bot.distanceTo(p);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private static ServerPlayerEntity find(MinecraftServer s, String name) {
        return name == null ? null : s.getPlayerManager().getPlayer(name);
    }

    private static void think(MinecraftServer server, FakeBot bot, PvpController pvp, SpyController spy) {
        BotConfig cfg = bot.config;
        switch (cfg.mode) {
            case FOLLOW -> {
                ServerPlayerEntity t = find(server, cfg.followTarget);
                if (t != null) follow(bot, t);
            }
            case SPY -> {
                ServerPlayerEntity t = find(server, cfg.followTarget);
                if (t != null) spy.tick(bot, t, bot.input, find(server, cfg.owner));
            }
            case REVENGE, FFA -> {
                LivingEntity e = nearestEnemy(bot, 24);
                if (cfg.mode == BotConfig.Mode.REVENGE) {
                    LivingEntity a = bot.getAttacker();
                    e = a != null && a.isAlive() ? a : null;
                }
                if (e != null) {
                    pvp.tick(bot, e, bot.input, cfg);
                    if (bot.input.attack) {
                        bot.doAttack(e);
                        if (!e.isAlive()) say(server, bot, BotChat.kill());
                    }
                } else bot.input.releaseMovement();
            }
            default -> bot.input.releaseMovement();   // SURVIVAL: Marco 3
        }
    }

    private static void follow(FakeBot bot, LivingEntity t) {
        BotInput in = bot.input; in.releaseMovement(); in.attack = false; in.use = false; in.hotbarSlot = -1;
        Vec3d d = t.getPos().subtract(bot.getPos());
        in.yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        in.pitch = 0;
        double dist = d.horizontalLength();
        if (dist > 4) { in.forward = true; in.sprint = dist > 8; }
        in.jump = bot.horizontalCollision;
    }

    // ---------- resposta de chat ----------
    public static void onChat(MinecraftServer server, ServerPlayerEntity sender, String msg) {
        String lower = msg.toLowerCase(Locale.ROOT);
        for (FakeBot bot : new ArrayList<>(BOTS.values())) {
            if (bot == sender) continue;
            if (!lower.contains(bot.getName().getString().toLowerCase(Locale.ROOT))) continue;
            String cmd = BotChat.parse(msg);
            switch (cmd) {
                case "follow" -> { bot.config.mode = BotConfig.Mode.FOLLOW; bot.config.followTarget = sender.getName().getString(); say(server, bot, BotChat.follow()); }
                case "stop" -> { bot.config.mode = BotConfig.Mode.SURVIVAL; say(server, bot, BotChat.stop()); }
                case "spy" -> { bot.config.mode = BotConfig.Mode.SPY; if (bot.config.followTarget == null) bot.config.followTarget = sender.getName().getString(); bot.config.owner = sender.getName().getString(); say(server, bot, BotChat.doing("espiao")); }
                case "attack" -> {
                    String n = BotChat.targetName(msg);
                    if (n != null) { bot.config.mode = BotConfig.Mode.FFA; say(server, bot, BotChat.attack(n)); }
                }
                case "doing" -> say(server, bot, BotChat.doing(bot.config.mode.name().toLowerCase()));
                case "hello" -> say(server, bot, BotChat.hello());
                default -> say(server, bot, BotChat.unknown());
            }
        }
    }
}
