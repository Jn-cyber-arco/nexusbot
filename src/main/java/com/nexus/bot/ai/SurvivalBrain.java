package com.nexus.bot.ai;

import com.nexus.bot.Util;
import com.nexus.bot.body.FakeBot;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Progressao: madeira -> pedra -> comida -> ferro -> diamante -> portal -> debris -> netherite (simplificado). */
public class SurvivalBrain {
    public enum Phase { WOOD, STONE, FOOD, IRON, DIAMOND, NETHER, ANCIENT, NETHERITE, DONE }
    public Phase phase = Phase.WOOD;

    private final Nav nav = new Nav();
    private final Digger dig = new Digger();
    private final Builder builder = new Builder();
    private final Random rng = new Random();
    private BlockPos target, wanderGoal, portalBase;
    private int scanTimer, stripTimer, wanderTimer, collectTicks, ignoreItem = -1, ignoreUntil, phaseTicks;
    private int cookTimer = -1;
    private boolean portalBuilt = false;
    private Direction stripDir = Direction.NORTH;
    private final List<Item[]> cookPairs = new ArrayList<>();

    static final Predicate<ItemStack> LOGS = s -> s.isIn(ItemTags.LOGS);
    static final Predicate<ItemStack> PLANKS = s -> s.isIn(ItemTags.PLANKS);
    static final Predicate<ItemStack> COBBLE = s -> s.isIn(ItemTags.STONE_TOOL_MATERIALS);
    static final Predicate<ItemStack> STICK = s -> s.isOf(Items.STICK);
    static final Item[][] MEAT = {{Items.BEEF, Items.COOKED_BEEF}, {Items.PORKCHOP, Items.COOKED_PORKCHOP},
        {Items.CHICKEN, Items.COOKED_CHICKEN}, {Items.MUTTON, Items.COOKED_MUTTON}};
    static final Predicate<ItemStack> FOOD = s -> s.isOf(Items.COOKED_BEEF) || s.isOf(Items.COOKED_PORKCHOP)
        || s.isOf(Items.COOKED_CHICKEN) || s.isOf(Items.COOKED_MUTTON) || s.isOf(Items.BREAD) || s.isOf(Items.APPLE);
    static Predicate<ItemStack> is(Item i) { return s -> s.isOf(i); }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Item[][] ARMOR = {
        {Items.NETHERITE_HELMET, Items.DIAMOND_HELMET, Items.IRON_HELMET},
        {Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE},
        {Items.NETHERITE_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.IRON_LEGGINGS},
        {Items.NETHERITE_BOOTS, Items.DIAMOND_BOOTS, Items.IRON_BOOTS}};

    private void setPhase(Phase p, Consumer<String> ev, String key) {
        phase = p; target = null; phaseTicks = 0; stripTimer = 0; portalBuilt = false;
        if (key != null) ev.accept(key);
    }

    public void tick(FakeBot bot, BotInput in, Consumer<String> ev) {
        in.resetAll();
        phaseTicks++;
        if (builder.active()) { builder.tick(bot, in); return; }
        if (cookTimer >= 0) { if (--cookTimer <= 0) finishCook(bot, ev); return; }
        if (bot.getHungerManager().getFoodLevel() <= 14) {
            int s = Inv.hotbar(bot, FOOD);
            if (s >= 0) { in.hotbarSlot = s; in.useMain = true; ev.accept("eat"); return; }
        }
        if (phase != Phase.DONE && collect(bot, in)) return;
        switch (phase) {
            case WOOD -> wood(bot, in, ev);
            case STONE -> stone(bot, in, ev);
            case FOOD -> food(bot, in, ev);
            case IRON -> iron(bot, in, ev);
            case DIAMOND -> diamond(bot, in, ev);
            case NETHER -> nether(bot, in, ev);
            case ANCIENT -> ancient(bot, in, ev);
            case NETHERITE -> netherite(bot, in, ev);
            case DONE -> wander(bot, in);
        }
    }

    // ---------------- fases basicas ----------------
    private void wood(FakeBot bot, BotInput in, Consumer<String> ev) {
        if (Inv.count(bot, Items.WOODEN_PICKAXE) > 0) { setPhase(Phase.STONE, ev, "wood_done"); return; }
        int logs = Inv.count(bot, LOGS), planks = Inv.count(bot, PLANKS);
        if (logs + planks / 4 >= 8) { craftWood(bot); return; }
        int by = bot.getBlockY();
        if (!gather(bot, in, st -> st.isIn(BlockTags.LOGS), 28, 8, true, p -> p.getY() <= by + 3 && p.getY() >= by - 4)) wander(bot, in);
        else ev.accept("wood");
    }

    private void stone(FakeBot bot, BotInput in, Consumer<String> ev) {
        if (Inv.count(bot, Items.STONE_PICKAXE) > 0 && Inv.count(bot, Items.FURNACE) > 0) { setPhase(Phase.FOOD, ev, "stone_done"); return; }
        if (Inv.count(bot, COBBLE) >= 13) { sticks(bot, 3); craftStone(bot); return; }
        if (!gather(bot, in, st -> st.isOf(Blocks.STONE) || st.isOf(Blocks.DEEPSLATE), 20, 12, false, null)) digDown(bot, in);
    }

    private void food(FakeBot bot, BotInput in, Consumer<String> ev) {
        int cooked = 0, raw = 0;
        for (Item[] m : MEAT) { cooked += Inv.count(bot, m[1]); raw += Inv.count(bot, m[0]); }
        if (cooked >= 6) { setPhase(Phase.IRON, ev, "food_done"); return; }

        AnimalEntity animal = nearestAnimal(bot, 32);
        if (raw >= 4 || (raw >= 1 && animal == null)) {
            if (!hasFuel(bot, raw)) { getFuel(bot, in); return; }
            cookPairs.clear(); cookPairs.addAll(Arrays.asList(MEAT));
            startCook(bot); ev.accept("cook");
            return;
        }
        if (animal == null) { wander(bot, in); return; }
        double d = bot.distanceTo(animal);
        if (d <= 3.0) {
            Util.aimAt(bot, animal.getEyePos(), in);
            int s = Inv.best(bot, Tools.SWORDS); if (s >= 0) in.hotbarSlot = s;
            if (bot.getAttackCooldownProgress(0.5f) >= 0.95f) bot.doAttack(animal);
            ev.accept("hunt");
        } else nav.goTo(bot, animal.getBlockPos(), 2.0, in);
    }

    private void iron(FakeBot bot, BotInput in, Consumer<String> ev) {
        equipArmor(bot);
        boolean all = Inv.count(bot, Items.IRON_SWORD) > 0 && Inv.count(bot, Items.IRON_PICKAXE) > 0
            && Inv.count(bot, Items.IRON_CHESTPLATE) > 0 && Inv.count(bot, Items.IRON_LEGGINGS) > 0
            && Inv.count(bot, Items.IRON_HELMET) > 0 && Inv.count(bot, Items.IRON_BOOTS) > 0 && Inv.count(bot, Items.BUCKET) > 0;
        if (all) { setPhase(Phase.DIAMOND, ev, "iron_done"); return; }

        int raw = Inv.count(bot, Items.RAW_IRON), ingots = Inv.count(bot, Items.IRON_INGOT);
        if (craftIron(bot)) { ev.accept("craft"); return; }
        boolean enough = raw + ingots >= 32 || phaseTicks > 30000;
        if (raw >= 8 || (raw > 0 && enough)) {
            if (hasFuel(bot, raw)) { cookPairs.clear(); cookPairs.add(new Item[]{Items.RAW_IRON, Items.IRON_INGOT}); startCook(bot); ev.accept("smelt"); return; }
            getFuel(bot, in);
            return;
        }
        if (enough) { setPhase(Phase.DIAMOND, ev, "iron_done"); return; }
        if (!gather(bot, in, st -> st.isOf(Blocks.IRON_ORE) || st.isOf(Blocks.DEEPSLATE_IRON_ORE), 24, 24, false, null)) strip(bot, in, 18);
        else ev.accept("ore");
    }

    // ---------------- diamante ----------------
    private void diamond(FakeBot bot, BotInput in, Consumer<String> ev) {
        equipArmor(bot);
        boolean all = Inv.count(bot, Items.DIAMOND_PICKAXE) > 0 && Inv.count(bot, Items.DIAMOND_CHESTPLATE) > 0
            && Inv.count(bot, Items.DIAMOND_LEGGINGS) > 0 && Inv.count(bot, Items.DIAMOND_HELMET) > 0 && Inv.count(bot, Items.DIAMOND_BOOTS) > 0;
        if (craftDiamond(bot)) { ev.accept("craft"); return; }
        boolean pickOk = Inv.count(bot, Items.DIAMOND_PICKAXE) > 0;
        if (all || (pickOk && phaseTicks > 24000)) { setPhase(Phase.NETHER, ev, "diamond_done"); return; }
        if (!gather(bot, in, st -> st.isOf(Blocks.DIAMOND_ORE) || st.isOf(Blocks.DEEPSLATE_DIAMOND_ORE), 24, 40, false, null)) strip(bot, in, -56);
        else ev.accept("ore");
    }

    // ---------------- portal do Nether (estilo speedrun) ----------------
    private void nether(FakeBot bot, BotInput in, Consumer<String> ev) {
        ServerWorld w = bot.getEntityWorld();
        if (w.getRegistryKey() == World.NETHER) { setPhase(Phase.ANCIENT, ev, "nether_in"); return; }
        fireProtect(bot);
        if (portalBuilt) {                                   // entra no portal e espera o teleporte
            nav.goTo(bot, portalBase.add(1, 1, 0), 0.9, in);
            return;
        }
        int obs = Inv.count(bot, Items.OBSIDIAN);
        if (obs >= 10) {
            portalBase = bot.getBlockPos().offset(bot.getHorizontalFacing(), 3);
            portalBuilt = true;
            builder.start(Builder.portal(portalBase));
            ev.accept("portal");
            return;
        }
        if (gather(bot, in, st -> st.isOf(Blocks.OBSIDIAN), 32, 32, false, null)) return;
        BlockPos lava = Scan.nearest(w, bot.getBlockPos(), 40, 40, st -> st.isOf(Blocks.LAVA) && st.getFluidState().isStill(), null);
        if (lava != null) {
            if (bot.getEyePos().distanceTo(Util.center(lava)) > 5.5) nav.goTo(bot, lava, 4.0, in);
            else castObsidian(w, lava, 10 - obs);            // "balde de agua" simulado: lava vira obsidiana
            return;
        }
        strip(bot, in, 8);
    }

    private void castObsidian(ServerWorld w, BlockPos c, int need) {
        for (int dx = -3; dx <= 3 && need > 0; dx++) for (int dy = -3; dy <= 3 && need > 0; dy++) for (int dz = -3; dz <= 3 && need > 0; dz++) {
            BlockPos p = c.add(dx, dy, dz);
            BlockState st = w.getBlockState(p);
            if (st.isOf(Blocks.LAVA) && st.getFluidState().isStill()) { w.setBlockState(p, Blocks.OBSIDIAN.getDefaultState()); need--; }
        }
    }

    // ---------------- Nether: debris e netherite ----------------
    private void ancient(FakeBot bot, BotInput in, Consumer<String> ev) {
        fireProtect(bot);
        int debris = Inv.count(bot, Items.ANCIENT_DEBRIS);
        if (debris >= 4 || (phaseTicks > 24000 && debris > 0)) { setPhase(Phase.NETHERITE, ev, "ancient_done"); return; }
        if (!gather(bot, in, st -> st.isOf(Blocks.ANCIENT_DEBRIS), 24, 24, false, null)) strip(bot, in, 15);
        else ev.accept("ore");
    }

    private void netherite(FakeBot bot, BotInput in, Consumer<String> ev) {
        fireProtect(bot);
        int deb = Inv.count(bot, Items.ANCIENT_DEBRIS);
        if (deb > 0) {
            if (hasFuel(bot, deb)) { cookPairs.clear(); cookPairs.add(new Item[]{Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP}); startCook(bot); ev.accept("smelt"); return; }
            getFuel(bot, in); return;
        }
        craft(bot, Items.GOLD_INGOT, 1, is(Items.GOLD_NUGGET), 9);
        if (craft(bot, Items.NETHERITE_INGOT, 1, is(Items.NETHERITE_SCRAP), 4, is(Items.GOLD_INGOT), 4)) { ev.accept("craft"); return; }
        if (smith(bot)) { ev.accept("craft"); return; }

        int ingots = Inv.count(bot, Items.NETHERITE_INGOT);
        boolean noTemplate = Inv.count(bot, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 0;
        boolean full = true;
        for (int i = 0; i < 4; i++) if (!bot.getEquippedStack(SLOTS[i]).isOf(ARMOR[i][0])) full = false;
        if (full || (ingots > 0 && noTemplate) || phaseTicks > 36000) {
            setPhase(Phase.DONE, ev, ingots > 0 && noTemplate ? "need_template" : "netherite_done");
            return;
        }
        int gold = Inv.count(bot, Items.GOLD_INGOT) + Inv.count(bot, Items.GOLD_NUGGET) / 9;
        if (gold < 4 && !gather(bot, in, st -> st.isOf(Blocks.NETHER_GOLD_ORE), 24, 24, false, null)) strip(bot, in, 40);
        else if (gold >= 4) wander(bot, in);
    }

    private boolean smith(FakeBot b) {
        if (Inv.count(b, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 0) return false;
        Item[][] pairs = {{Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE}, {Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
            {Items.DIAMOND_HELMET, Items.NETHERITE_HELMET}, {Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS}};
        int[] slot = {1, 2, 0, 3};
        for (int i = 0; i < 4; i++) {
            if (Inv.count(b, Items.NETHERITE_INGOT) == 0) return false;
            EquipmentSlot s = SLOTS[slot[i]];
            if (b.getEquippedStack(s).isOf(pairs[i][0])) {
                Inv.take(b, is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 1);
                Inv.take(b, is(Items.NETHERITE_INGOT), 1);
                b.equipStack(s, new ItemStack(pairs[i][1]));
                return true;
            }
        }
        return false;
    }

    private void fireProtect(FakeBot b) {
        if (b.age % 200 == 0) b.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 400));
    }

    // ---------------- movimento / coleta ----------------
    private boolean gather(FakeBot bot, BotInput in, Predicate<BlockState> pred, int rx, int ry, boolean useNav, Predicate<BlockPos> extra) {
        ServerWorld w = bot.getEntityWorld();
        if (target != null && !pred.test(w.getBlockState(target))) target = null;
        if (target == null && --scanTimer <= 0) { scanTimer = 20; target = Scan.nearest(w, bot.getBlockPos(), rx, ry, pred, extra); }
        if (target == null) return false;
        double d = bot.getEyePos().distanceTo(Util.center(target));
        if (d <= 4.2) { dig.mine(bot, target, in); return true; }
        if (useNav) nav.goTo(bot, target, 3.0, in); else dig.stepToward(bot, target, in);
        if (dig.blocked) { stripDir = stripDir.rotateYClockwise(); dig.blocked = false; target = null; }
        return true;
    }

    private void getFuel(FakeBot bot, BotInput in) {
        if (gather(bot, in, st -> st.isOf(Blocks.COAL_ORE) || st.isOf(Blocks.DEEPSLATE_COAL_ORE), 24, 16, false, null)) return;
        if (gather(bot, in, st -> st.isIn(BlockTags.LOGS), 28, 8, true, null)) return;
        wander(bot, in);
    }

    private void digDown(FakeBot bot, BotInput in) {
        if (bot.getBlockY() < -50) { wander(bot, in); return; }
        dig.stepToward(bot, bot.getBlockPos().offset(stripDir, 3).down(3), in);
        if (dig.blocked) { stripDir = stripDir.rotateYClockwise(); dig.blocked = false; }
    }

    /** Desce em escada ate a altura y e depois faz um tunel reto procurando minerio. */
    private void strip(FakeBot bot, BotInput in, int y) {
        if (bot.getBlockY() > y + 2) { digDown(bot, in); return; }
        dig.stepToward(bot, bot.getBlockPos().offset(stripDir, 6), in);
        if (dig.blocked || ++stripTimer > 300) { stripDir = stripDir.rotateYClockwise(); stripTimer = 0; dig.blocked = false; }
    }

    private void wander(FakeBot bot, BotInput in) {
        ServerWorld w = bot.getEntityWorld();
        if (wanderGoal == null || bot.getBlockPos().getManhattanDistance(wanderGoal) < 3 || --wanderTimer <= 0) {
            int x = bot.getBlockX() + rng.nextInt(41) - 20, z = bot.getBlockZ() + rng.nextInt(41) - 20;
            if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return;
            wanderGoal = new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
            wanderTimer = 300;
        }
        nav.goTo(bot, wanderGoal, 2.0, in);
    }

    private boolean collect(FakeBot bot, BotInput in) {
        ServerWorld w = bot.getEntityWorld();
        Box box = new Box(bot.getX() - 8, bot.getY() - 4, bot.getZ() - 8, bot.getX() + 8, bot.getY() + 4, bot.getZ() + 8);
        ItemEntity best = null; double bd = 1e9;
        for (ItemEntity it : w.getEntitiesByClass(ItemEntity.class, box, e -> e.isAlive())) {
            if (it.getId() == ignoreItem && bot.age < ignoreUntil) continue;
            double d = bot.distanceTo(it);
            if (d < bd) { bd = d; best = it; }
        }
        if (best == null) { collectTicks = 0; return false; }
        if (++collectTicks > 80) { ignoreItem = best.getId(); ignoreUntil = bot.age + 600; collectTicks = 0; return false; }
        nav.goTo(bot, best.getBlockPos(), 0.7, in);
        return true;
    }

    private AnimalEntity nearestAnimal(FakeBot bot, double r) {
        ServerWorld w = bot.getEntityWorld();
        Box box = new Box(bot.getX() - r, bot.getY() - 10, bot.getZ() - r, bot.getX() + r, bot.getY() + 10, bot.getZ() + r);
        AnimalEntity best = null; double bd = 1e9;
        for (AnimalEntity a : w.getEntitiesByClass(AnimalEntity.class, box, e -> e.isAlive() && !e.isBaby()
                && (e instanceof CowEntity || e instanceof PigEntity || e instanceof SheepEntity || e instanceof ChickenEntity))) {
            double d = bot.distanceTo(a);
            if (d < bd) { bd = d; best = a; }
        }
        return best;
    }

    // ---------------- craft simulado ----------------
    @SuppressWarnings("unchecked")
    private boolean craft(FakeBot b, Item out, int n, Object... req) {
        for (int i = 0; i < req.length; i += 2) if (Inv.count(b, (Predicate<ItemStack>) req[i]) < (Integer) req[i + 1]) return false;
        for (int i = 0; i < req.length; i += 2) Inv.take(b, (Predicate<ItemStack>) req[i], (Integer) req[i + 1]);
        b.giveItemStack(new ItemStack(out, n));
        return true;
    }

    private void sticks(FakeBot b, int n) {
        while (Inv.count(b, STICK) < n) {
            if (Inv.count(b, PLANKS) < 2) {
                if (Inv.count(b, LOGS) < 1) return;
                Inv.take(b, LOGS, 1); b.giveItemStack(new ItemStack(Items.OAK_PLANKS, 4));
            }
            Inv.take(b, PLANKS, 2); b.giveItemStack(new ItemStack(Items.STICK, 4));
        }
    }

    private void craftWood(FakeBot b) {
        while (Inv.count(b, PLANKS) < 16 && Inv.count(b, LOGS) > 0) { Inv.take(b, LOGS, 1); b.giveItemStack(new ItemStack(Items.OAK_PLANKS, 4)); }
        if (Inv.take(b, PLANKS, 4)) placeNear(b, Blocks.CRAFTING_TABLE);
        sticks(b, 8);
        craft(b, Items.WOODEN_PICKAXE, 1, PLANKS, 3, STICK, 2);
        craft(b, Items.WOODEN_SWORD, 1, PLANKS, 2, STICK, 1);
        craft(b, Items.WOODEN_AXE, 1, PLANKS, 3, STICK, 2);
    }

    private void craftStone(FakeBot b) {
        craft(b, Items.STONE_PICKAXE, 1, COBBLE, 3, STICK, 2);
        craft(b, Items.STONE_SWORD, 1, COBBLE, 2, STICK, 1);
        craft(b, Items.FURNACE, 1, COBBLE, 8);
    }

    private boolean craftIron(FakeBot b) {
        Predicate<ItemStack> I = is(Items.IRON_INGOT);
        if (Inv.count(b, I) == 0) return false;
        sticks(b, 3);
        if (Inv.count(b, Items.IRON_SWORD) == 0 && craft(b, Items.IRON_SWORD, 1, I, 2, STICK, 1)) return true;
        if (Inv.count(b, Items.IRON_PICKAXE) == 0 && craft(b, Items.IRON_PICKAXE, 1, I, 3, STICK, 2)) return true;
        if (Inv.count(b, Items.IRON_CHESTPLATE) == 0 && craft(b, Items.IRON_CHESTPLATE, 1, I, 8)) return true;
        if (Inv.count(b, Items.IRON_LEGGINGS) == 0 && craft(b, Items.IRON_LEGGINGS, 1, I, 7)) return true;
        if (Inv.count(b, Items.IRON_HELMET) == 0 && craft(b, Items.IRON_HELMET, 1, I, 5)) return true;
        if (Inv.count(b, Items.IRON_BOOTS) == 0 && craft(b, Items.IRON_BOOTS, 1, I, 4)) return true;
        if (Inv.count(b, Items.BUCKET) == 0 && craft(b, Items.BUCKET, 1, I, 3)) return true;
        return false;
    }

    private boolean craftDiamond(FakeBot b) {
        Predicate<ItemStack> D = is(Items.DIAMOND);
        if (Inv.count(b, D) == 0) return false;
        sticks(b, 2);
        if (Inv.count(b, Items.DIAMOND_PICKAXE) == 0 && craft(b, Items.DIAMOND_PICKAXE, 1, D, 3, STICK, 2)) return true;
        if (Inv.count(b, Items.DIAMOND_CHESTPLATE) == 0 && craft(b, Items.DIAMOND_CHESTPLATE, 1, D, 8)) return true;
        if (Inv.count(b, Items.DIAMOND_LEGGINGS) == 0 && craft(b, Items.DIAMOND_LEGGINGS, 1, D, 7)) return true;
        if (Inv.count(b, Items.DIAMOND_HELMET) == 0 && craft(b, Items.DIAMOND_HELMET, 1, D, 5)) return true;
        if (Inv.count(b, Items.DIAMOND_BOOTS) == 0 && craft(b, Items.DIAMOND_BOOTS, 1, D, 4)) return true;
        if (Inv.count(b, Items.DIAMOND_SWORD) == 0 && craft(b, Items.DIAMOND_SWORD, 1, D, 2, STICK, 1)) return true;
        return false;
    }

    /** Veste a melhor armadura que tiver no inventario (netherite > diamante > ferro). */
    private void equipArmor(FakeBot b) {
        for (int i = 0; i < 4; i++) {
            if (b.getEquippedStack(SLOTS[i]).isOf(Items.ELYTRA)) continue;
            for (int j = 0; j < ARMOR[i].length; j++) {
                Item item = ARMOR[i][j];
                if (b.getEquippedStack(SLOTS[i]).isOf(item)) break;
                if (Inv.take(b, is(item), 1)) {
                    ItemStack old = b.getEquippedStack(SLOTS[i]).copy();
                    b.equipStack(SLOTS[i], new ItemStack(item));
                    if (!old.isEmpty()) b.giveItemStack(old);
                    break;
                }
            }
        }
    }

    private boolean placeNear(FakeBot b, Block block) {
        ServerWorld w = b.getEntityWorld();
        BlockPos p = b.getBlockPos();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            BlockPos q = p.add(dx, 0, dz);
            if (w.getBlockState(q).isAir() && !w.getBlockState(q.down()).isAir()) {
                w.setBlockState(q, block.getDefaultState());
                b.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    // ---------------- fornalha simulada ----------------
    private boolean hasFuel(FakeBot b, int items) {
        double units = Inv.count(b, Items.COAL) * 8.0 + Inv.count(b, Items.CHARCOAL) * 8.0 + (Inv.count(b, LOGS) + Inv.count(b, PLANKS)) * 1.5;
        return units >= Math.max(1, items);
    }

    private void consumeFuel(FakeBot b, int items) {
        double need = items;
        for (Item coal : new Item[]{Items.COAL, Items.CHARCOAL}) while (need > 0 && Inv.take(b, is(coal), 1)) need -= 8;
        while (need > 0 && Inv.take(b, PLANKS, 1)) need -= 1.5;
        while (need > 0 && Inv.take(b, LOGS, 1)) need -= 1.5;
    }

    private void startCook(FakeBot b) {
        int n = 0;
        for (Item[] p : cookPairs) n += Inv.count(b, p[0]);
        if (n == 0) return;
        if (Inv.take(b, is(Items.FURNACE), 1) || (Inv.count(b, COBBLE) >= 8 && Inv.take(b, COBBLE, 8))) placeNear(b, Blocks.FURNACE);
        cookTimer = 40 + 25 * n;
    }

    private void finishCook(FakeBot b, Consumer<String> ev) {
        int total = 0;
        for (Item[] p : cookPairs) {
            int c = Inv.count(b, p[0]);
            if (c > 0) { Inv.take(b, is(p[0]), c); b.giveItemStack(new ItemStack(p[1], c)); total += c; }
        }
        consumeFuel(b, total);
        cookTimer = -1;
        ev.accept("cook_done");
    }
}
