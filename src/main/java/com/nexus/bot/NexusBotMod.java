package com.nexus.bot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nexus.bot.ai.BotConfig;
import com.nexus.bot.body.FakeBot;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class NexusBotMod implements ModInitializer {
    public static MinecraftServer SERVER;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));
        ServerLifecycleEvents.SERVER_STARTING.register(srv -> SERVER = srv);
        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> { SERVER = null; BotManager.BOTS.clear(); });
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (SERVER != null) BotManager.onChat(SERVER, sender, message.getSignedContent());
        });
    }

    private static int reply(CommandContext<ServerCommandSource> c, String msg) {
        c.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static int doSpawn(CommandContext<ServerCommandSource> c, String name, BlockPos pos) {
        ServerCommandSource src = c.getSource();
        Vec3d p = pos != null ? Vec3d.ofBottomCenter(pos) : src.getPosition();
        ServerPlayerEntity owner = src.getPlayer();
        FakeBot bot = BotManager.spawn(src.getServer(), src.getWorld(), name, p, owner);
        return reply(c, bot == null ? "Ja existe um bot com esse nome." : "Bot " + bot.getName().getString() + " criado.");
    }

    private static FakeBot bot(CommandContext<ServerCommandSource> c) { return BotManager.BOTS.get(StringArgumentType.getString(c, "bot")); }

    private void register(CommandDispatcher<ServerCommandSource> d) {
        var root = literal("nexusbot");

        // spawn [nome] [x y z]
        root.then(literal("spawn")
            .executes(c -> doSpawn(c, null, null))
            .then(argument("name", StringArgumentType.word())
                .executes(c -> doSpawn(c, StringArgumentType.getString(c, "name"), null))
                .then(argument("pos", BlockPosArgumentType.blockPos())
                    .executes(c -> doSpawn(c, StringArgumentType.getString(c, "name"), BlockPosArgumentType.getBlockPos(c, "pos")))))
            .then(argument("pos", BlockPosArgumentType.blockPos())
                .executes(c -> doSpawn(c, null, BlockPosArgumentType.getBlockPos(c, "pos")))));

        root.then(literal("remove").then(argument("bot", StringArgumentType.word()).executes(c -> {
            String n = StringArgumentType.getString(c, "bot");
            if (n.equals("all")) new HashSet<>(BotManager.BOTS.keySet()).forEach(k -> BotManager.remove(c.getSource().getServer(), k));
            else BotManager.remove(c.getSource().getServer(), n);
            return reply(c, "Removido.");
        })));

        root.then(literal("list").executes(c -> reply(c, "Bots: " + String.join(", ", BotManager.BOTS.keySet()))));

        // mode <bot> <modo> [alvo]
        root.then(literal("mode").then(argument("bot", StringArgumentType.word())
            .then(argument("modo", StringArgumentType.word())
                .executes(c -> setMode(c, null))
                .then(argument("alvo", StringArgumentType.word()).executes(c -> setMode(c, StringArgumentType.getString(c, "alvo")))))));

        // difficulty <bot> <nivel>
        root.then(literal("difficulty").then(argument("bot", StringArgumentType.word())
            .then(argument("nivel", StringArgumentType.word()).executes(c -> {
                FakeBot b = bot(c); if (b == null) return reply(c, "Bot nao encontrado.");
                try { b.config.difficulty = BotConfig.Difficulty.valueOf(StringArgumentType.getString(c, "nivel").toUpperCase()); }
                catch (Exception e) { return reply(c, "Use: easy, medium, hard, expert, perfect"); }
                return reply(c, "Dificuldade: " + b.config.difficulty);
            }))));

        // style / tech <bot> <nome> on|off
        for (String kind : new String[]{"style", "tech"}) {
            root.then(literal(kind).then(argument("bot", StringArgumentType.word())
                .then(argument("nome", StringArgumentType.word())
                    .then(argument("on", StringArgumentType.word()).executes(c -> {
                        FakeBot b = bot(c); if (b == null) return reply(c, "Bot nao encontrado.");
                        var set = kind.equals("style") ? b.config.styles : b.config.techniques;
                        String n = StringArgumentType.getString(c, "nome").toLowerCase();
                        if (StringArgumentType.getString(c, "on").equalsIgnoreCase("on")) set.add(n); else set.remove(n);
                        return reply(c, kind + " " + n + " = " + set.contains(n));
                    })))));
        }

        // team
        root.then(literal("team")
            .then(literal("create").then(argument("time", StringArgumentType.word()).executes(c -> {
                BotManager.TEAMS.putIfAbsent(StringArgumentType.getString(c, "time"), new HashSet<>());
                return reply(c, "Time criado."); })))
            .then(literal("add").then(argument("time", StringArgumentType.word()).then(argument("nome", StringArgumentType.word()).executes(c -> {
                String t = StringArgumentType.getString(c, "time"), n = StringArgumentType.getString(c, "nome");
                BotManager.TEAMS.computeIfAbsent(t, k -> new HashSet<>()).add(n);
                FakeBot b = BotManager.BOTS.get(n); if (b != null) b.config.team = t;
                return reply(c, n + " entrou em " + t); }))))
            .then(literal("remove").then(argument("time", StringArgumentType.word()).then(argument("nome", StringArgumentType.word()).executes(c -> {
                String t = StringArgumentType.getString(c, "time"), n = StringArgumentType.getString(c, "nome");
                var s = BotManager.TEAMS.get(t); if (s != null) s.remove(n);
                FakeBot b = BotManager.BOTS.get(n); if (b != null) b.config.team = null;
                return reply(c, n + " saiu de " + t); }))))
            .then(literal("list").executes(c -> reply(c, "Times: " + BotManager.TEAMS))));

        // say
        root.then(literal("say").then(argument("bot", StringArgumentType.word())
            .then(argument("msg", StringArgumentType.greedyString()).executes(c -> {
                FakeBot b = bot(c); if (b == null) return reply(c, "Bot nao encontrado.");
                BotManager.say(c.getSource().getServer(), b, StringArgumentType.getString(c, "msg"));
                return 1; }))));

        d.register(root);
    }

    private int setMode(CommandContext<ServerCommandSource> c, String alvo) {
        FakeBot b = bot(c); if (b == null) return reply(c, "Bot nao encontrado.");
        try { b.config.mode = BotConfig.Mode.valueOf(StringArgumentType.getString(c, "modo").toUpperCase()); }
        catch (Exception e) { return reply(c, "Modos: survival, follow, revenge, ffa, spy"); }
        ServerPlayerEntity me = c.getSource().getPlayer();
        b.config.followTarget = alvo != null ? alvo : (me != null ? me.getName().getString() : null);
        if (me != null) b.config.owner = me.getName().getString();
        return reply(c, "Modo: " + b.config.mode + (b.config.followTarget != null ? " (alvo " + b.config.followTarget + ")" : ""));
    }
}
