package com.nexus.bot.ai;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;

public class Tools {
    public static final Item[] PICKS = {Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE};
    public static final Item[] AXES = {Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE};
    public static final Item[] SWORDS = {Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD};

    public static void select(PlayerEntity p, BlockState st, BotInput in) {
        int slot = -1;
        if (st.isIn(BlockTags.PICKAXE_MINEABLE)) slot = Inv.best(p, PICKS);
        else if (st.isIn(BlockTags.AXE_MINEABLE)) slot = Inv.best(p, AXES);
        if (slot >= 0) in.hotbarSlot = slot;
    }
}
