package com.nexus.bot.ai;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public class Inv {
    public static int count(PlayerEntity p, Predicate<ItemStack> pr) {
        int c = 0; PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) { ItemStack s = inv.getStack(i); if (!s.isEmpty() && pr.test(s)) c += s.getCount(); }
        return c;
    }
    public static int count(PlayerEntity p, Item item) { return count(p, s -> s.isOf(item)); }

    public static boolean take(PlayerEntity p, Predicate<ItemStack> pr, int n) {
        if (count(p, pr) < n) return false;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size() && n > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && pr.test(s)) { int t = Math.min(n, s.getCount()); s.decrement(t); n -= t; }
        }
        return true;
    }

    /** Devolve o slot da hotbar (0-8) com o item; puxa do inventario se precisar. -1 se nao tem. */
    public static int hotbar(PlayerEntity p, Predicate<ItemStack> pr) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) { ItemStack s = inv.getStack(i); if (!s.isEmpty() && pr.test(s)) return i; }
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && pr.test(s)) {
                ItemStack other = inv.getStack(8);
                inv.setStack(8, s); inv.setStack(i, other);
                return 8;
            }
        }
        return -1;
    }

    public static int best(PlayerEntity p, Item... order) {
        for (Item it : order) { int s = hotbar(p, st -> st.isOf(it)); if (s >= 0) return s; }
        return -1;
    }
}
