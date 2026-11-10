package com.nexus.bot.ai;

import com.nexus.bot.Util;
import com.nexus.bot.body.FakeBot;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/** Constroi bloco por bloco (anda ate perto, coloca 1 bloco a cada 3 ticks e gasta o item do inventario). */
public class Builder {
    public record Put(BlockPos pos, BlockState state, Predicate<ItemStack> cost) {}

    static final Predicate<ItemStack> COBBLE = s -> s.isIn(ItemTags.STONE_TOOL_MATERIALS);
    static final Predicate<ItemStack> PLANKS = s -> s.isIn(ItemTags.PLANKS);
    static final Predicate<ItemStack> OBS = s -> s.isOf(Items.OBSIDIAN);
    static final BlockState AIR = Blocks.AIR.getDefaultState();

    private List<Put> queue = new ArrayList<>();
    private int idx = 0, timer = 0, stuck = 0;
    private final Nav nav = new Nav();
    public String status = "";

    public boolean active() { return idx < queue.size(); }
    public void start(List<Put> puts) { queue = puts; idx = 0; timer = 0; stuck = 0; status = ""; }

    private static boolean already(ServerWorld w, Put p) {
        BlockState cur = w.getBlockState(p.pos());
        return cur.isOf(p.state().getBlock()) || (cur.isAir() && p.state().isAir());
    }

    /** Retorna true quando terminou (ou abortou por falta de material). */
    public boolean tick(FakeBot bot, BotInput in) {
        ServerWorld w = bot.getEntityWorld();
        while (active() && already(w, queue.get(idx))) idx++;
        if (!active()) return true;
        Put p = queue.get(idx);
        Vec3d c = Util.center(p.pos());
        if (bot.getEyePos().distanceTo(c) > 5.0) {
            nav.goTo(bot, p.pos().up(), 3.0, in);
            if (++stuck > 240) { idx++; stuck = 0; }        // celula inalcancavel: pula
            return false;
        }
        stuck = 0;
        Util.aimAt(bot, c, in);
        if (!p.state().isAir() && (p.pos().equals(bot.getBlockPos()) || p.pos().equals(bot.getBlockPos().up()))) { in.back = true; return false; }
        if (--timer > 0) return false;
        timer = 3;
        if (p.cost() != null && !Inv.take(bot, p.cost(), 1)) { status = "no_material"; idx = queue.size(); return true; }
        w.setBlockState(p.pos(), p.state());
        bot.swingHand(Hand.MAIN_HAND);
        idx++;
        return !active();
    }

    // ---------------- plantas ----------------
    public static BlockPos origin(FakeBot bot, int size) {
        BlockPos p = bot.getBlockPos();
        int x = p.getX(), z = p.getZ(), y = p.getY() - 1;
        switch (bot.getHorizontalFacing()) {
            case EAST -> { x += 3; z -= size / 2; }
            case WEST -> { x -= 3 + size - 1; z -= size / 2; }
            case SOUTH -> { z += 3; x -= size / 2; }
            default -> { z -= 3 + size - 1; x -= size / 2; }
        }
        return new BlockPos(x, y, z);
    }

    public static List<Put> house(BlockPos o, int n, boolean furnished) {
        List<Put> l = new ArrayList<>();
        BlockState wall = Blocks.COBBLESTONE.getDefaultState(), plank = Blocks.OAK_PLANKS.getDefaultState();
        for (int y = 0; y <= 4; y++) for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) {
            BlockPos p = o.add(x, y, z);
            boolean edge = x == 0 || z == 0 || x == n - 1 || z == n - 1;
            boolean corner = (x == 0 || x == n - 1) && (z == 0 || z == n - 1);
            if (y == 0 || y == 4) l.add(new Put(p, plank, PLANKS));
            else if (edge) {
                boolean door = z == 0 && x == n / 2 && y <= 2;
                boolean window = y == 2 && !corner && !door && ((x + z) % 3 == 0);
                l.add(door || window ? new Put(p, AIR, null) : new Put(p, wall, COBBLE));
            } else l.add(new Put(p, AIR, null));
        }
        if (furnished) {
            l.add(new Put(o.add(1, 1, 1), Blocks.CRAFTING_TABLE.getDefaultState(), null));
            l.add(new Put(o.add(2, 1, 1), Blocks.FURNACE.getDefaultState(), null));
            l.add(new Put(o.add(n - 2, 1, 1), Blocks.CHEST.getDefaultState(), null));
            l.add(new Put(o.add(n - 2, 1, n - 2), Blocks.TORCH.getDefaultState(), null));
        }
        return l;
    }

    public static List<Put> tower(BlockPos o) {
        List<Put> l = new ArrayList<>();
        BlockState wall = Blocks.COBBLESTONE.getDefaultState(), plank = Blocks.OAK_PLANKS.getDefaultState();
        for (int y = 0; y <= 10; y++) for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            BlockPos p = o.add(x, y, z);
            boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
            if (y == 0 || y == 10) l.add(new Put(p, plank, PLANKS));
            else if (edge) l.add((z == 0 && x == 2 && y <= 2) ? new Put(p, AIR, null) : new Put(p, wall, COBBLE));
            else l.add(new Put(p, AIR, null));
        }
        return l;
    }

    public static List<Put> bridge(FakeBot bot, int len) {
        List<Put> l = new ArrayList<>();
        BlockPos p = bot.getBlockPos().down();
        Direction f = bot.getHorizontalFacing(), side = f.rotateYClockwise();
        BlockState plank = Blocks.OAK_PLANKS.getDefaultState();
        for (int i = 1; i <= len; i++) for (int k = -1; k <= 1; k++) l.add(new Put(p.offset(f, i).offset(side, k), plank, PLANKS));
        return l;
    }

    public static List<Put> pyramid(BlockPos o) {
        List<Put> l = new ArrayList<>();
        BlockState wall = Blocks.COBBLESTONE.getDefaultState();
        for (int layer = 0; layer < 5; layer++) {
            int size = 9 - 2 * layer;
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) l.add(new Put(o.add(x + layer, layer, z + layer), wall, COBBLE));
        }
        return l;
    }

    /** Portal do Nether (eixo X): limpa o espaco, 10 obsidianas de moldura e o interior aceso. */
    public static List<Put> portal(BlockPos b) {
        List<Put> l = new ArrayList<>();
        BlockState obs = Blocks.OBSIDIAN.getDefaultState();
        BlockState portal = Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, Direction.Axis.X);
        for (int x = 0; x <= 3; x++) for (int y = 0; y <= 4; y++) for (int z = -1; z <= 1; z++) l.add(new Put(b.add(x, y, z), AIR, null));
        for (int x = 1; x <= 2; x++) { l.add(new Put(b.add(x, 0, 0), obs, OBS)); l.add(new Put(b.add(x, 4, 0), obs, OBS)); }
        for (int y = 1; y <= 3; y++) { l.add(new Put(b.add(0, y, 0), obs, OBS)); l.add(new Put(b.add(3, y, 0), obs, OBS)); }
        for (int x = 1; x <= 2; x++) for (int y = 1; y <= 3; y++) l.add(new Put(b.add(x, y, 0), portal, null));
        return l;
    }

    public static List<Put> blueprint(FakeBot bot, String kind) {
        String k = kind == null ? "random" : kind.toLowerCase();
        if (k.equals("random")) { String[] all = {"house", "base", "tower", "bridge", "pyramid"}; k = all[new Random().nextInt(all.length)]; }
        return switch (k) {
            case "house", "casa" -> house(origin(bot, 7), 7, false);
            case "base" -> house(origin(bot, 9), 9, true);
            case "tower", "torre" -> tower(origin(bot, 5));
            case "bridge", "ponte" -> bridge(bot, 24);
            case "pyramid", "piramide" -> pyramid(origin(bot, 9));
            default -> house(origin(bot, 7), 7, false);
        };
    }
}
