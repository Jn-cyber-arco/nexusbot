package com.nexus.bot;

import com.mojang.authlib.properties.Property;
import com.nexus.bot.ai.*;
import com.nexus.bot.auth.AuthManager;
import com.nexus.bot.body.FakeBot;
import com.nexus.bot.body.Skins;
import com.nexus.bot.chat.BotChat;
import com.nexus.bot.kit.KitManager;
import net.minecraft.block.TntBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class BotManager {
    private static final String[] NICKS = {"Lobo", "Sombra", "Trovao", "Ninja", "Falcao", "Mestre", "Viking", "Raio", "Pantera", "Cobra"};
    private static final Random R = new Random();

    public static final Map<String, FakeBot> BOTS = new LinkedHashMap<>();
    public static final Map<String, Set<String>> TEAMS = new HashMap<>();
    private static final Map<String, Brain> BRAINS = new HashMap<>();
    private static final Set<String> PENDING_NAMES = new HashSet<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long lastChat = 0;

    record Task(long due, Runnable run) {}

    static class Brain {
        final PvpController pvp = new PvpController();
        final SpyController spy = new SpyController();
        final SurvivalBrain survival = new SurvivalBrain();
        final Builder builder = new Builder();
        final Digger dig = new Digger();
        final Nav nav = new Nav();
        final Map<String, Long> lastEv = new HashMap<>();
        int idle = 2400 + new Random().nextInt(4000), pearlCd = 0, tntCd = 0;
    }

    // ---------------- agenda / tick global ----------------
    public static void schedule(MinecraftServer server, long delay, Runnable r) { PENDING.add(new Task(server.getTicks() + delay, r)); }

    public static void tickAll(MinecraftServer server) {
        long now = server.getTicks();
        for (Task t : new ArrayList<>(PENDING)) {
            if (t.due() <= now) {
                PENDING.remove(t);
                try { t.run().run(); } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    // ---------------- spawn / remove ----------------
    public static String randomNick() {
        String n;
        do { n = NICKS[R.nextInt(NICKS.length)] + "_" + (10 + R.nextInt(90)); } while (BOTS.containsKey(n) || PENDING_NAMES.contains(n));
        return n;
    }

    private static FakeBot spawnInternal(MinecraftServer server, ServerWorld world, String name, Vec3d pos, BotConfig cfg, Property skin) {
        FakeBot bot = FakeBot.spawn(server, world, Skins.profile(name, skin), pos);
        bot.config = cfg;
        cfg.skin = skin;
        if (cfg.home == null) cfg.home = pos;
        final Brain br = new Brain();
        int ord = Math.max(0, Math.min(cfg.phaseOrd, SurvivalBrain.Phase.values().length - 1));
        br.survival.phase = SurvivalBrain.Phase.values()[ord];
        bot.brain = b -> { try { think(server, b, br); } catch (Exception e) { e.printStackTrace(); } };
        bot.onDeathHook = s -> onBotDeath(server, bot, world);
        BOTS.put(name, bot);
        BRAINS.put(name, br);
        AuthManager.onJoin(server, bot, !cfg.isNull);
        return bot;
    }

    private static void applyKit(MinecraftServer server, FakeBot bot, String kit) {
        if (kit == null) return;
        schedule(server, 3, () -> {
            if (!KitManager.give(server, bot, kit)) Kits.giveGear(bot);
        });
    }

    /** Retorna o nome do bot (ou null se nome em uso / limite). A skin da conta real e buscada de forma assincrona. */
    public static String spawn(MinecraftServer server, ServerWorld world, String name, Vec3d pos, ServerPlayerEntity owner,
                               BotConfig.Mode mode, String kit) {
        if (BOTS.size() + PENDING_NAMES.size() >= Settings.maxBots) return null;
        if (name == null) name = randomNick();
        if (BOTS.containsKey(name) || PENDING_NAMES.contains(name)) return null;
        BotConfig cfg = new BotConfig();
        cfg.mode = mode; cfg.home = pos;
        if (owner != null) cfg.owner = owner.getName().getString();
        final String fname = name;
        if (Settings.on("skins") && Skins.lookupable(name)) {
            PENDING_NAMES.add(name);
            Skins.lookup(name).orTimeout(6, TimeUnit.SECONDS).exceptionally(e -> null)
                .thenAccept(prop -> server.execute(() -> { PENDING_NAMES.remove(fname); finishSpawn(server, world, fname, pos, cfg, prop, kit); }));
        } else finishSpawn(server, world, name, pos, cfg, null, kit);
        return name;
    }

    private static void finishSpawn(MinecraftServer server, ServerWorld world, String name, Vec3d pos, BotConfig cfg, Property skin, String kit) {
        if (BOTS.containsKey(name)) return;
        FakeBot bot = spawnInternal(server, world, name, pos, cfg, skin);
        applyKit(server, bot, kit);
        say(server, bot, BotChat.spawn());
    }

    /** Portal de nulls: nick estilo _____h___ / iags.jCk, skin preta (resource), time "null", sem chat. */
    public static int spawnNullPortal(MinecraftServer server, ServerWorld world, Vec3d center, int count, ServerPlayerEntity owner) {
        int n = Math.max(0, Math.min(count, Settings.maxBots - BOTS.size()));
        TEAMS.computeIfAbsent("null", k -> new HashSet<>());
        for (int i = 0; i < n; i++) {
            schedule(server, i * 3L, () -> {
                if (BOTS.size() >= Settings.maxBots) return;
                Set<String> used = new HashSet<>(BOTS.keySet()); used.addAll(PENDING_NAMES);
                String name = NullNames.generate(R, Settings.nullNickMin, Settings.nullNickMax, Settings.nullNickStyle, used);
                double ang = R.nextDouble() * Math.PI * 2, rad = 1.5 + R.nextDouble() * 2.5;
                Vec3d p = new Vec3d(center.x + Math.cos(ang) * rad, center.y, center.z + Math.sin(ang) * rad);
                BotConfig cfg = new BotConfig();
                cfg.mode = BotConfig.Mode.FFA; cfg.team = "null"; cfg.isNull = true; cfg.chat = false;
                cfg.difficulty = BotConfig.Difficulty.EXPERT; cfg.home = p;
                if (owner != null) cfg.owner = owner.getName().getString();
                TEAMS.get("null").add(name);
                FakeBot b = spawnInternal(server, world, name, p, cfg, null);
                applyKit(server, b, Settings.nullKit);
                world.spawnParticles(ParticleTypes.PORTAL, p.x, p.y + 1, p.z, 40, 0.4, 0.9, 0.4, 0.3);
            });
        }
        return n;
    }

    public static void remove(MinecraftServer server, String name) {
        FakeBot bot = BOTS.remove(name);
        BRAINS.remove(name);
        if (bot == null) return;
        if (bot.config.isNull) { Set<String> t = TEAMS.get("null"); if (t != null) t.remove(name); }
        try { bot.networkHandler.disconnect(Text.literal("removido")); } catch (Exception ignored) {}
        server.getPlayerManager().remove(bot);
    }

    private static void onBotDeath(MinecraftServer server, FakeBot bot, ServerWorld world) {
        String name = bot.getName().getString();
        BotConfig cfg = bot.config;
        Vec3d home = cfg.home != null ? cfg.home : Util.pos(bot);
        say(server, bot, BotChat.death());
        schedule(server, 5, () -> remove(server, name));
        if (Settings.on("respawn") && !cfg.isNull) {
            schedule(server, 80, () -> {
                if (BOTS.containsKey(name)) return;
                FakeBot nb = spawnInternal(server, world, name, home, cfg, cfg.skin);
                if (cfg.mode != BotConfig.Mode.SURVIVAL) applyKit(server, nb, Settings.defaultKit);
            });
        }
    }

    // ---------------- construcao ----------------
    public static boolean build(FakeBot bot, String kind) {
        Brain br = BRAINS.get(bot.getName().getString());
        if (br == null) return false;
        br.builder.start(Builder.blueprint(bot, kind));
        return true;
    }

    // ---------------- chat ----------------
    public static void forceSay(MinecraftServer server, FakeBot bot, String msg) {
        server.getPlayerManager().broadcast(Text.literal("<" + bot.getName().getString() + "> " + msg), false);
    }

    public static void say(MinecraftServer server, FakeBot bot, String msg) { say(server, bot, msg, true); }

    private static void say(MinecraftServer server, FakeBot bot, String msg, boolean chain) {
        if (msg == null || msg.isEmpty()) return;
        if (!Settings.on("chat") || !bot.config.chat) return;
        long now = server.getTicks();
        if (now - lastChat < 12) return;
        lastChat = now;
        forceSay(server, bot, msg);
        if (chain && Settings.on("botchat") && R.nextInt(5) == 0) {
            List<FakeBot> others = new ArrayList<>();
            for (FakeBot o : BOTS.values()) if (o != bot && o.config.chat && !o.config.isNull) others.add(o);
            if (!others.isEmpty()) {
                FakeBot o = others.get(R.nextInt(others.size()));
                schedule(server, 40 + R.nextInt(60), () -> say(server, o, BotChat.reply(), false));
            }
        }
    }

    private static void event(MinecraftServer server, FakeBot bot, Brain br, String key) {
        long now = server.getTicks();
        Long last = br.lastEv.get(key);
        if (last != null && now - last < 1200) return;
        br.lastEv.put(key, now);
        say(server, bot, BotChat.event(key));
    }

    public static void onChat(MinecraftServer server, ServerPlayerEntity sender, String msg) {
        String lower = msg.toLowerCase(Locale.ROOT);
        for (FakeBot bot : new ArrayList<>(BOTS.values())) {
            if (bot == sender || bot.config.isNull) continue;
            if (!lower.contains(bot.getName().getString().toLowerCase(Locale.ROOT))) continue;
            String me = sender.getName().getString();
            switch (BotChat.parse(msg)) {
                case "follow" -> { bot.config.mode = BotConfig.Mode.FOLLOW; bot.config.followTarget = me; say(server, bot, BotChat.follow()); }
                case "stop" -> { bot.config.mode = BotConfig.Mode.SURVIVAL; say(server, bot, BotChat.stop()); }
                case "survival" -> { bot.config.mode = BotConfig.Mode.SURVIVAL; say(server, bot, BotChat.doing("sobrevivência")); }
                case "spy" -> { bot.config.mode = BotConfig.Mode.SPY; if (bot.config.followTarget == null) bot.config.followTarget = me; bot.config.owner = me; say(server, bot, BotChat.doing("espião")); }
                case "attack" -> {
                    String n = BotChat.targetName(msg);
                    if (n != null) { bot.config.mode = BotConfig.Mode.FFA; bot.config.forcedTarget = n; bot.config.forcedUntil = server.getTicks() + 600; say(server, bot, BotChat.attack(n)); }
                }
                case "build" -> {
                    String kind = lower.contains("torre") ? "torre" : lower.contains("ponte") ? "ponte" : lower.contains("pir") ? "piramide"
                        : lower.contains("base") ? "base" : lower.contains("casa") ? "casa" : "random";
                    build(bot, kind);
                    say(server, bot, BotChat.event("build"));
                }
                case "doing" -> say(server, bot, BotChat.doing(bot.config.mode.name().toLowerCase()));
                case "thanks" -> say(server, bot, BotChat.thanks());
                case "trash" -> say(server, bot, BotChat.trash());
                case "hello" -> say(server, bot, BotChat.hello());
                default -> say(server, bot, BotChat.unknown());
            }
        }
    }

    // ---------------- times / chifre de cabra ----------------
    public static void callTeam(MinecraftServer server, String team, String targetName) {
        for (FakeBot b : BOTS.values()) {
            if (!team.equals(b.config.team)) continue;
            b.config.mode = BotConfig.Mode.FFA;
            b.config.forcedTarget = targetName;
            b.config.forcedUntil = server.getTicks() + 600;
        }
        for (FakeBot b : BOTS.values()) if (team.equals(b.config.team) && !b.config.isNull) { say(server, b, BotChat.teamCall(targetName)); break; }
    }

    public static void onHorn(MinecraftServer server, ServerPlayerEntity sp) {
        String pn = sp.getName().getString();
        String team = null;
        for (Map.Entry<String, Set<String>> e : TEAMS.entrySet()) if (e.getValue().contains(pn)) { team = e.getKey(); break; }
        if (team == null) return;
        Vec3d look = sp.getRotationVec(1f);
        ServerPlayerEntity aimed = null, nearest = null; double bestDot = 0.9, nd = 1e9;
        for (ServerPlayerEntity p : sp.getEntityWorld().getPlayers()) {
            if (p == sp || p.isSpectator() || TEAMS.get(team).contains(p.getName().getString())) continue;
            double d = sp.distanceTo(p);
            if (d > 64) continue;
            double dot = look.dotProduct(p.getEyePos().subtract(sp.getEyePos()).normalize());
            if (dot > bestDot) { bestDot = dot; aimed = p; }
            if (d < nd) { nd = d; nearest = p; }
        }
        ServerPlayerEntity t = aimed != null ? aimed : nearest;
        if (t == null) return;
        sp.sendMessage(Text.literal("Time " + team + " chamou o alvo: " + t.getName().getString()), false);
        callTeam(server, team, t.getName().getString());
    }

    // ---------------- IA ----------------
    private static boolean sameTeam(FakeBot bot, LivingEntity other) {
        String t = bot.config.team;
        return t != null && TEAMS.getOrDefault(t, Set.of()).contains(other.getName().getString());
    }

    private static ServerPlayerEntity player(MinecraftServer s, String name) { return name == null ? null : s.getPlayerManager().getPlayer(name); }

    private static LivingEntity nearestEnemy(FakeBot bot, double range) {
        LivingEntity best = null; double bd = range;
        for (ServerPlayerEntity p : bot.getEntityWorld().getPlayers()) {
            if (p == bot || p.isSpectator() || sameTeam(bot, p)) continue;
            double d = bot.distanceTo(p);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private static LivingEntity pickTarget(MinecraftServer server, FakeBot bot, double range) {
        BotConfig c = bot.config;
        if (c.forcedTarget != null && server.getTicks() < c.forcedUntil) {
            ServerPlayerEntity p = player(server, c.forcedTarget);
            if (p != null && p.isAlive() && !p.isSpectator() && !sameTeam(bot, p)) return p;
        }
        return nearestEnemy(bot, range);
    }

    private static LivingEntity nearbyHostile(FakeBot bot, double r) {
        Box box = new Box(bot.getX() - r, bot.getY() - 3, bot.getZ() - r, bot.getX() + r, bot.getY() + 3, bot.getZ() + r);
        LivingEntity best = null; double bd = 1e9;
        for (HostileEntity h : bot.getEntityWorld().getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive())) {
            double d = bot.distanceTo(h);
            if (d < bd) { bd = d; best = h; }
        }
        return best;
    }

    private static void think(MinecraftServer server, FakeBot bot, Brain br) {
        BotConfig cfg = bot.config; BotInput in = bot.input;
        in.resetAll();
        if (bot.isDead() || bot.getHealth() <= 0f) return;
        if (!cfg.isNull && --br.idle <= 0) { br.idle = 3000 + R.nextInt(5000); say(server, bot, BotChat.idle()); }
        if (br.pearlCd > 0) br.pearlCd--;
        if (br.tntCd > 0) br.tntCd--;

        LivingEntity target = null;
        switch (cfg.mode) {
            case SPY -> {
                ServerPlayerEntity t = player(server, cfg.followTarget);
                if (t != null && t != bot) br.spy.tick(bot, t, in, player(server, cfg.owner));
                return;
            }
            case FOLLOW -> {
                ServerPlayerEntity t = player(server, cfg.followTarget);
                if (t != null && t != bot) br.nav.goTo(bot, t.getBlockPos(), 3.5, in);
                return;
            }
            case REVENGE -> { LivingEntity a = bot.getAttacker(); if (a != null && a.isAlive() && !sameTeam(bot, a)) target = a; }
            case FFA -> target = pickTarget(server, bot, 48);
            case SURVIVAL -> {
                LivingEntity a = bot.getAttacker();
                if (a instanceof PlayerEntity && a.isAlive() && !sameTeam(bot, a) && bot.distanceTo(a) < 30) target = a;
                else target = nearbyHostile(bot, 5);
            }
        }

        if (target == null) {
            if (br.builder.active()) {
                br.builder.tick(bot, in);
                if ("no_material".equals(br.builder.status)) { br.builder.status = ""; event(server, bot, br, "no_material"); }
                return;
            }
            if (cfg.mode == BotConfig.Mode.SURVIVAL && Settings.on("survival")) {
                br.survival.tick(bot, in, key -> event(server, bot, br, key));
                cfg.phaseOrd = br.survival.phase.ordinal();
            }
            return;
        }

        double dist = bot.distanceTo(target);
        boolean grief = cfg.griefAllowed && Settings.on("grief");

        if (dist > 10) {
            // pérola: se o alvo esta longe, joga uma pérola na direcao dele
            if (cfg.tech("pearl") && br.pearlCd <= 0 && dist > 14) {
                int s = Inv.hotbar(bot, st -> st.isOf(Items.ENDER_PEARL));
                if (s >= 0) {
                    Util.aimAt(bot, target.getEyePos(), in);
                    in.pitch -= (float) Math.min(30.0, dist * 0.6);
                    in.hotbarSlot = s; in.throwItem = true;
                    br.pearlCd = 160;
                    return;
                }
            }
            if (grief) {   // atravessa paredes cavando e joga TNT de longe
                br.dig.stepToward(bot, target.getBlockPos(), in);
                if (br.tntCd <= 0 && dist > 6 && dist < 22 && Inv.take(bot, st -> st.isOf(Items.TNT), 1)) {
                    TntBlock.primeTnt(bot.getEntityWorld(), target.getBlockPos());
                    br.tntCd = 100;
                    event(server, bot, br, "grief");
                }
            } else br.nav.goTo(bot, target.getBlockPos(), 6.0, in);
            return;
        }

        br.pvp.tick(bot, target, in, cfg);

        boolean heal = cfg.tech("smarthealing") && bot.getHealth() <= 8f && Inv.count(bot, Items.GOLDEN_APPLE) > 0;
        if (heal) {
            int s = Inv.hotbar(bot, st -> st.isOf(Items.GOLDEN_APPLE));
            if (s >= 0) {
                in.hotbarSlot = s; in.useMain = true; in.use = false; in.attack = false;
                in.sprint = false; in.forward = false; in.back = dist < 4;
                event(server, bot, br, "lowhp");
            }
        }
        if (in.attack) {
            bot.doAttack(target);
            if (!target.isAlive() && target instanceof PlayerEntity) say(server, bot, BotChat.kill());
        }
    }
}
