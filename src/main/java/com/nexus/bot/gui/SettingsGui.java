package com.nexus.bot.gui;

import com.nexus.bot.ai.Settings;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/** Painel de configuracoes: bau de 27 slots, clique para ligar/desligar. Funciona so no servidor. */
public class SettingsGui {
    private static List<String> keys() { return Settings.all(); }

    static void fill(Inventory inv) {
        List<String> k = keys();
        for (int i = 0; i < 27; i++) {
            if (i < k.size()) {
                boolean on = Settings.on(k.get(i));
                ItemStack s = new ItemStack(on ? Items.LIME_DYE : Items.GRAY_DYE);
                s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(k.get(i) + ": " + (on ? "LIGADO" : "DESLIGADO")));
                inv.setStack(i, s);
            } else inv.setStack(i, ItemStack.EMPTY);
        }
    }

    public static void open(ServerPlayerEntity p) {
        SimpleInventory inv = new SimpleInventory(27);
        fill(inv);
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, pinv, pl) -> new Handler(syncId, pinv, inv), Text.literal("NexusBot - Configurações")));
    }

    static class Handler extends GenericContainerScreenHandler {
        private final SimpleInventory inv;
        Handler(int id, PlayerInventory pi, SimpleInventory inv) {
            super(ScreenHandlerType.GENERIC_9X3, id, pi, inv, 3);
            this.inv = inv;
        }
        @Override
        public void onSlotClick(int slot, int button, SlotActionType type, PlayerEntity player) {
            List<String> k = keys();
            if (slot >= 0 && slot < k.size()) { Settings.set(k.get(slot), !Settings.on(k.get(slot))); fill(inv); }
            this.syncState();
        }
        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }
}
