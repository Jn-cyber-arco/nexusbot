package com.nexus.bot.gui;

import com.nexus.bot.BotManager;
import com.nexus.bot.body.FakeBot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Painel de time: lista jogadores e bots online; clique para entrar/sair do time. */
public class TeamGui {
    static void fill(Inventory inv, List<String> names, String team) {
        Set<String> members = BotManager.TEAMS.getOrDefault(team, Set.of());
        for (int i = 0; i < 54; i++) {
            if (i < names.size()) {
                boolean in = members.contains(names.get(i));
                ItemStack s = new ItemStack(in ? Items.LIME_DYE : Items.GRAY_DYE);
                s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(names.get(i) + (in ? "  [no time " + team + "]" : "  [fora]")));
                inv.setStack(i, s);
            } else inv.setStack(i, ItemStack.EMPTY);
        }
    }

    public static void open(ServerPlayerEntity p, MinecraftServer server, String team) {
        BotManager.TEAMS.putIfAbsent(team, new HashSet<>());
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) names.add(o.getName().getString());
        if (names.size() > 54) names = names.subList(0, 54);
        final List<String> fn = names;
        SimpleInventory inv = new SimpleInventory(54);
        fill(inv, fn, team);
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, pinv, pl) -> new Handler(syncId, pinv, inv, fn, team), Text.literal("Time: " + team)));
    }

    static class Handler extends GenericContainerScreenHandler {
        private final SimpleInventory inv; private final List<String> names; private final String team;
        Handler(int id, PlayerInventory pi, SimpleInventory inv, List<String> names, String team) {
            super(ScreenHandlerType.GENERIC_9X6, id, pi, inv, 6);
            this.inv = inv; this.names = names; this.team = team;
        }
        @Override
        public void onSlotClick(int slot, int button, SlotActionType type, PlayerEntity player) {
            if (slot >= 0 && slot < names.size()) {
                String n = names.get(slot);
                Set<String> m = BotManager.TEAMS.computeIfAbsent(team, k -> new HashSet<>());
                FakeBot b = BotManager.BOTS.get(n);
                if (m.contains(n)) { m.remove(n); if (b != null) b.config.team = null; }
                else { m.add(n); if (b != null) b.config.team = team; }
                fill(inv, names, team);
            }
            this.syncState();
        }
        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }
}
