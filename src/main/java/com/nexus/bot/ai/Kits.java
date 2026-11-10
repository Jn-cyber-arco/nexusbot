package com.nexus.bot.ai;

import com.nexus.bot.body.FakeBot;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class Kits {
    public static void giveGear(FakeBot b) {
        b.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        b.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        b.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        b.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        b.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        b.getInventory().setStack(0, new ItemStack(Items.NETHERITE_SWORD));
        b.getInventory().setStack(1, new ItemStack(Items.NETHERITE_AXE));
        b.getInventory().setStack(2, new ItemStack(Items.MACE));
        b.getInventory().setStack(3, new ItemStack(Items.WIND_CHARGE, 64));
        b.getInventory().setStack(4, new ItemStack(Items.GOLDEN_APPLE, 32));
    }

    public static void clear(FakeBot b) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND})
            b.equipStack(s, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) b.getInventory().setStack(i, ItemStack.EMPTY);
    }
}
