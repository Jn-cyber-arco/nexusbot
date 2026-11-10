package com.nexus.bot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nexus.bot.ai.BotConfig;
import com.nexus.bot.ai.Settings;
import com.nexus.bot.auth.AuthManager;
import com.nexus.bot.body.FakeBot;
import com.nexus.bot.gui.SettingsGui;
import com.nexus.bot.gui.TeamGui;
import com.nexus.bot.kit.KitManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class NexusBotMod implements ModInitializer {
    public static MinecraftServer SERVER;

    private static final SuggestionProvider<ServerCommandSource> KIT_SUGGEST =
        (ctx, builder) -> CommandSource.suggestMatching(KitManager.names(), builder);
    private static final SuggestionProvider<ServerCommandSource> BOT_SUGGEST =
        (ctx, builder) -> CommandSource.suggestMatching(BotManager.BOTS.keySet(), builder);

    @Override
    public void onInitialize() {
        KitManager.load();
        AuthManager.load();
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));
        ServerLifecycleEvents.SERVER_STARTING.register(srv -> SERVER = srv);
        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> { SERVER = null; BotManager.BOTS.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(BotManager::tickAll);
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (SERVER != null) BotManager.onChat(SERVER, sender, message.getSignedContent());
        });
        // chifre de cabra: chama o alvo do time
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity sp && sp.getStackInHand(hand).isOf(Items.GOAT_HORN) && SERVER != null)
                BotManager.onHorn(SERVER, sp);
            return ActionResult.PASS;
        });
    }

    private static int reply(CommandContext<ServerCommandSource> c, String msg) {
        c.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static FakeBot bot(CommandContext<ServerCommandSource> c) { return BotManager.BOTS.get(StringArgumentType.getString(c, "bot")); }

    private static int doSpawn(CommandContext<ServerCommandSource> c, String name, BlockPos pos, BotConfig.Mode mode, boolean useKit) {
        ServerCommandSource src = c.getSource();
        Vec3d p = pos != null ? Vec3d.ofBottomCenter(pos) : src.getPosition();
        String kit = useKit ? Settings.defaultKit : null;
        String n = BotManager.spawn(src.getServer(), src.getWorld(), name, p, src.getPlayer(), mode, kit);
        return reply(c, n == null ? "Nome já em uso ou limite de bots atingido." : "Criando bot " + n + "...");
    }

    private static int doNulls(CommandContext<ServerCommandSource> c, BlockPos pos) {
        ServerCommandSource src = c.getSource();
        int count = IntegerArgumentType.getInteger(c, "count");
        Vec3d p = pos != null ? Vec3d.ofBottomCenter(pos) : src.getPosition();
        int n = BotManager.spawnNullPortal(src.getServer(), src.getWorld(), p, count, src.getPlayer());
        return reply(c, n + " nulls saindo do portal...");
    }

    private static LiteralArgumentBuilder<ServerCommandSource> spawnNode(String lit, BotConfig.Mode mode, boolean useKit) {
        return literal(lit)
            .executes(c -> doSpawn(c, null, null, mode, useKit))
            .then(argument("name", StringArgumentType.word())
                .executes(c -> doSpawn(c, StringArgumentType.getString(c, "name"), null, mode, useKit))
                .then(argument("pos", BlockPosArgumentType.blockPos())
                    .executes(c -> doSpawn(c, StringArgumentType.getString(c, "name"), BlockPosArgumentType.getBlockPos(c, "pos"), mode, useKit))))
            .then(argument("pos", BlockPosArgumentType.blockPos())
                .executes(c -> doSpawn(c, null, BlockPosArgumentType.getBlockPos(c, "pos"), mode, useKit)));
    }

    private static String listSettings() {
        StringBuilder sb = new StringBuilder("Configurações (ON = ligado):");
        for (String k : Settings.all()) sb.append("\n ").append(k).append(": ").append(Settings.on(k) ? "ON" : "OFF");
        sb.append("\n nullnick: ").append(Settings.nullNickMin).append("-").append(Settings.nullNickMax).append(" estilo ").append(Settings.nullNickStyle);
        sb.append("\n maxbots: ").append(Settings.maxBots);
        sb.append("\n defaultkit: ").append(Settings.defaultKit).append("  nullkit: ").append(Settings.nullKit);
        return sb.toString();
    }

    private void register(CommandDispatcher<ServerCommandSource> d) {
        var root = literal("nexusbot");

        root.then(spawnNode("spawn", BotConfig.Mode.FFA, true));
        root.then(spawnNode("spawnsurvival", BotConfig.Mode.SURVIVAL, false));

        // /nexusbot spawnullportal [x y z] <quantidade>
        root.then(literal("spawnullportal")
            .then(argument("count", IntegerArgumentType.integer(1, 100)).executes(c -> doNulls(c, null)))
            .then(argument("pos", BlockPosArgumentType.blockPos())
                .then(argument("count", IntegerArgumentType.integer(1, 100))
                    .executes(c -> doNulls(c, BlockPosArgumentType.getBlockPos(c, "pos"))))));

        root.then(literal("remove").then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST).executes(c -> {
            String n = StringArgumentType.getString(c, "bot");
            MinecraftServer s = c.getSource().getServer();
            if (n.equals("all")) new HashSet<>(BotManager.BOTS.keySet()).forEach(k -> BotManager.remove(s, k));
            else BotManager.remove(s, n);
            return reply(c, "Removido.");
        })));

        root.then(literal("list").executes(c -> reply(c, "Bots (" + BotManager.BOTS.size() + "): " + String.join(", ", BotManager.BOTS.keySet()))));

        root.then(literal("mode").then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST)
            .then(argument("modo", StringArgumentType.word())
                .executes(c -> setMode(c, null))
                .then(argument("alvo", StringArgumentType.word()).executes(c -> setMode(c, StringArgumentType.getString(c, "alvo")))))));

        root.then(literal("difficulty").then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST)
            .then(argument("nivel", StringArgumentType.word()).executes(c -> {
                FakeBot b = bot(c); if (b == null) return reply(c, "Bot não encontrado.");
                try { b.config.difficulty = BotConfig.Difficulty.valueOf(StringArgumentType.getString(c, "nivel").toUpperCase()); }
                catch (Exception e) { return reply(c, "Use: easy, medium, hard, expert, perfect"); }
                return reply(c, "Dificuldade: " + b.config.difficulty);
            }))));

        // ---- kits (pasta nexusbotkit/*.json) ----
        root.then(literal("givekit")
            .then(argument("kit", StringArgumentType.word()).suggests(KIT_SUGGEST)
                .then(argument("alvo", StringArgumentType.word()).executes(c -> {
                    MinecraftServer s = c.getSource().getServer();
                    String kit = StringArgumentType.getString(c, "kit"), alvo = StringArgumentType.getString(c, "alvo");
                    ServerPlayerEntity t = s.getPlayerManager().getPlayer(alvo);
                    if (t == null) return reply(c, "Jogador/bot não encontrado: " + alvo);
                    if (!KitManager.give(s, t, kit)) return reply(c, "Kit não existe. Kits: " + String.join(", ", KitManager.names()));
                    return reply(c, "Kit " + kit + " entregue para " + alvo + ".");
                }))));
        root.then(literal("kits").executes(c -> reply(c, "Kits (" + KitManager.dir() + "): " + String.join(", ", KitManager.names()))));
        root.then(literal("reloadkits").executes(c -> { KitManager.load(); return reply(c, "Kits recarregados: " + String.join(", ", KitManager.names())); }));

        // ---- construcao e grief ----
        root.then(literal("build").then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST)
            .then(argument("tipo", StringArgumentType.word()).executes(c -> {
                FakeBot b = bot(c); if (b == null) return reply(c, "Bot não encontrado.");
                BotManager.build(b, StringArgumentType.getString(c, "tipo"));
                return reply(c, "Construindo (casa, base, torre, ponte, piramide, random). O bot precisa de blocos: /nexusbot givekit builder " + b.getName().getString());
            }))));
        root.then(literal("grief").then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST)
            .then(argument("on", StringArgumentType.word()).executes(c -> {
                FakeBot b = bot(c); if (b == null) return reply(c, "Bot não encontrado.");
                b.config.griefAllowed = StringArgumentType.getString(c, "on").equalsIgnoreCase("on");
                return reply(c, "Grief do bot: " + b.config.griefAllowed + (Settings.on("grief") ? "" : "  (ATENÇÃO: também precisa de /nexusbot settings grief on)"));
            }))));

        // ---- login automatico ----
        root.then(literal("auth").then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST).executes(c -> {
            String n = StringArgumentType.getString(c, "bot");
            String pw = AuthManager.password(n);
            return reply(c, pw == null ? "Sem senha salva para " + n : "Senha de " + n + ": " + pw);
        })));

        // ligar/desligar tecnica ou estilo so para UM bot
        for (String kind : new String[]{"style", "tech"}) {
            root.then(literal(kind).then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST)
                .then(argument("nome", StringArgumentType.word())
                    .then(argument("on", StringArgumentType.word()).executes(c -> {
                        FakeBot b = bot(c); if (b == null) return reply(c, "Bot não encontrado.");
                        String n = StringArgumentType.getString(c, "nome").toLowerCase();
                        boolean on = StringArgumentType.getString(c, "on").equalsIgnoreCase("on");
                        if (on) b.config.off.remove(n); else b.config.off.add(n);
                        return reply(c, kind + " " + n + " = " + (on ? "ON" : "OFF") + " (só para " + b.getName().getString() + ")");
                    })))));
        }

        // configuracoes globais
        root.then(literal("settings")
            .executes(c -> reply(c, listSettings()))
            .then(literal("nullnick").then(argument("min", IntegerArgumentType.integer(3, 16))
                .then(argument("max", IntegerArgumentType.integer(3, 16)).executes(c -> {
                    Settings.nullNickMin = IntegerArgumentType.getInteger(c, "min");
                    Settings.nullNickMax = IntegerArgumentType.getInteger(c, "max");
                    return reply(c, "Nick dos nulls: " + Settings.nullNickMin + " a " + Settings.nullNickMax + " caracteres.");
                }))))
            .then(literal("nullnickstyle").then(argument("estilo", StringArgumentType.word()).executes(c -> {
                String s = StringArgumentType.getString(c, "estilo").toLowerCase();
                if (!Set.of("mixed", "underscore", "dot", "random").contains(s)) return reply(c, "Use: mixed, underscore, dot, random");
                Settings.nullNickStyle = s;
                return reply(c, "Estilo de nick: " + s);
            })))
            .then(literal("maxbots").then(argument("n", IntegerArgumentType.integer(1, 200)).executes(c -> {
                Settings.maxBots = IntegerArgumentType.getInteger(c, "n");
                return reply(c, "Máximo de bots: " + Settings.maxBots);
            })))
            .then(literal("defaultkit").then(argument("kit", StringArgumentType.word()).suggests(KIT_SUGGEST).executes(c -> {
                Settings.defaultKit = StringArgumentType.getString(c, "kit"); return reply(c, "Kit padrão: " + Settings.defaultKit); })))
            .then(literal("nullkit").then(argument("kit", StringArgumentType.word()).suggests(KIT_SUGGEST).executes(c -> {
                Settings.nullKit = StringArgumentType.getString(c, "kit"); return reply(c, "Kit dos nulls: " + Settings.nullKit); })))
            .then(argument("nome", StringArgumentType.word())
                .then(argument("on", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "nome").toLowerCase();
                    if (!Settings.exists(n)) return reply(c, "Não existe. Use /nexusbot settings para ver a lista.");
                    boolean on = StringArgumentType.getString(c, "on").equalsIgnoreCase("on");
                    Settings.set(n, on);
                    return reply(c, n + " = " + (on ? "ON" : "OFF"));
                }))));

        root.then(literal("ui").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayer();
            if (p == null) return reply(c, "Só jogadores podem abrir o painel.");
            SettingsGui.open(p);
            return 1;
        }));

        root.then(literal("teamui").then(argument("time", StringArgumentType.word()).executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayer();
            if (p == null) return reply(c, "Só jogadores podem abrir o painel.");
            TeamGui.open(p, c.getSource().getServer(), StringArgumentType.getString(c, "time"));
            return 1;
        })));

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
                Set<String> s = BotManager.TEAMS.get(t); if (s != null) s.remove(n);
                FakeBot b = BotManager.BOTS.get(n); if (b != null) b.config.team = null;
                return reply(c, n + " saiu de " + t); }))))
            .then(literal("call").then(argument("time", StringArgumentType.word()).then(argument("alvo", StringArgumentType.word()).executes(c -> {
                BotManager.callTeam(c.getSource().getServer(), StringArgumentType.getString(c, "time"), StringArgumentType.getString(c, "alvo"));
                return reply(c, "Alvo chamado."); }))))
            .then(literal("list").executes(c -> reply(c, "Times: " + BotManager.TEAMS))));

        root.then(literal("say").then(argument("bot", StringArgumentType.word()).suggests(BOT_SUGGEST)
            .then(argument("msg", StringArgumentType.greedyString()).executes(c -> {
                FakeBot b = bot(c); if (b == null) return reply(c, "Bot não encontrado.");
                BotManager.forceSay(c.getSource().getServer(), b, StringArgumentType.getString(c, "msg"));
                return 1; }))));

        d.register(root);
    }

    private int setMode(CommandContext<ServerCommandSource> c, String alvo) {
        FakeBot b = bot(c); if (b == null) return reply(c, "Bot não encontrado.");
        try { b.config.mode = BotConfig.Mode.valueOf(StringArgumentType.getString(c, "modo").toUpperCase()); }
        catch (Exception e) { return reply(c, "Modos: survival, follow, revenge, ffa, spy"); }
        ServerPlayerEntity me = c.getSource().getPlayer();
        b.config.followTarget = alvo != null ? alvo : (me != null ? me.getName().getString() : null);
        if (me != null) b.config.owner = me.getName().getString();
        return reply(c, "Modo: " + b.config.mode + (b.config.followTarget != null ? " (alvo " + b.config.followTarget + ")" : ""));
    }
}
