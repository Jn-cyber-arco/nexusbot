package com.nexus.bot.ai;

import com.nexus.bot.Util;
import com.nexus.bot.body.FakeBot;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Random;

public class PvpController {
    static final double REACH = 3.0, SPACING_MIN = 3.01, SPACING_MAX = 3.50;
    private final Random rng = new Random();
    private int wTap, sTap, strafeTimer, blockHit, lastHurt, reaction, nearTicks;
    private boolean strafeRight = true, engaged = false;
    private LivingEntity lastTarget;
    private int wmState = 0, wmTimer = 0, wmCooldown = 0;
    private int elState = 0, elTimer = 0, elCd = 0;

    public void tick(FakeBot self, LivingEntity target, BotInput in, BotConfig cfg) {
        in.resetAll();
        if (target != lastTarget) { lastTarget = target; engaged = false; nearTicks = 0; wmState = 0; elState = 0; }
        double dist = self.distanceTo(target);
        Util.aimAt(self, target.getEyePos(), in);
        if (wmCooldown > 0) wmCooldown--;

        if (elytra(self, target, in, cfg, dist)) return;
        if (windMace(self, target, in, cfg, dist)) return;

        // spacing 3.01 - 3.50
        in.sprint = true;
        if (dist > SPACING_MAX) in.forward = true;
        else if (dist < SPACING_MIN) in.back = true;
        else in.forward = true;

        if (wTap > 0) { in.forward = false; in.sprint = false; wTap--; }
        if (sTap > 0) { in.forward = false; in.back = true; sTap--; }

        if (cfg.tech("strafing")) {
            if (--strafeTimer <= 0) { strafeRight = !strafeRight; strafeTimer = 6 + rng.nextInt(8); }
            in.left = !strafeRight; in.right = strafeRight;
        }

        boolean falling = !self.isOnGround() && self.getVelocity().y < 0;
        if (cfg.tech("critchain") && self.isOnGround() && dist < 4.5) in.jump = true;

        // escolha de arma
        int slot = -1;
        if (cfg.tech("maceswap") && cfg.style("mace") && self.fallDistance > 3.0f) slot = firstSlot(self, Items.MACE);
        if (slot < 0 && cfg.style("axe") && target.isBlocking()) slot = firstSlot(self, Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE, Items.STONE_AXE);
        if (slot < 0) slot = firstSlot(self, Tools.SWORDS);
        if (slot >= 0) in.hotbarSlot = slot;

        // totem na mao quando alguem vem de Maca por cima
        if (cfg.tech("totem") && !target.isOnGround() && target.fallDistance > 1.5f && dist < 12 && target.getMainHandStack().isOf(Items.MACE)) {
            int t = slotOf(self, Items.TOTEM_OF_UNDYING);
            if (t >= 0) in.hotbarSlot = t;
        }

        // hit select: so engaja depois de tomar o 1o hit (ou ficar muito tempo perto)
        if (cfg.tech("hitselect")) {
            if (dist < 6) nearTicks++;
            if (!engaged && (self.hurtTime > 0 || nearTicks > 50)) engaged = true;
        } else engaged = true;

        boolean ready = self.getAttackCooldownProgress(0.5f) >= 0.95f;
        boolean critWindow = !cfg.tech("peakcrit") || falling || self.fallDistance > 0f;
        if (engaged && dist <= REACH && ready && critWindow) {
            if (++reaction >= cfg.difficulty.reactionTicks && rng.nextDouble() < cfg.difficulty.hitChance) {
                in.attack = true; reaction = 0;
                if (cfg.tech("wtap")) wTap = 1;
                if (cfg.tech("stap") && rng.nextDouble() < cfg.difficulty.comboChance) sTap = 2;
                if (cfg.tech("blockhit") && cfg.style("shield")) blockHit = 3;
            }
        }
        if (blockHit > 0 && self.getOffHandStack().isOf(Items.SHIELD)) { in.use = true; blockHit--; }

        if (cfg.tech("jumpreset") && self.hurtTime > lastHurt && self.isOnGround()) in.jump = true;
        lastHurt = self.hurtTime;
    }

    /** Wind charge no chao -> sobe -> cai com a Maca. */
    private boolean windMace(FakeBot self, LivingEntity target, BotInput in, BotConfig cfg, double dist) {
        if (!(cfg.tech("windmace") && cfg.style("mace"))) { wmState = 0; return false; }
        int wind = slotOf(self, Items.WIND_CHARGE), mace = slotOf(self, Items.MACE);
        if (wmState == 0) {
            if (self.isOnGround() && wmCooldown == 0 && wind >= 0 && mace >= 0 && dist > 3 && dist < 12) { wmState = 1; wmTimer = 0; }
            else return false;
        }
        switch (wmState) {
            case 1 -> { in.hotbarSlot = wind; in.pitch = 90f; in.throwItem = true; wmState = 2; wmTimer = 0; return true; }
            case 2 -> {
                in.hotbarSlot = mace; in.forward = true; in.sprint = true;
                if (!self.isOnGround() && self.getVelocity().y < 0 && self.fallDistance > 1.0f) wmState = 3;
                else if (++wmTimer > 50) { wmState = 0; wmCooldown = 60; }
                return true;
            }
            case 3 -> {
                in.hotbarSlot = mace; in.forward = true; in.sprint = true;
                if (dist <= 3.5 && self.getAttackCooldownProgress(0.5f) >= 0.95f) in.attack = true;
                if (self.isOnGround() || ++wmTimer > 80) { wmState = 0; wmCooldown = 100; }
                return true;
            }
            default -> { wmState = 0; return false; }
        }
    }

    /** Elytra + foguete -> mergulho -> Maca (precisa de Elytra no peito, foguete e Maca na hotbar). */
    private boolean elytra(FakeBot self, LivingEntity target, BotInput in, BotConfig cfg, double dist) {
        if (!(cfg.tech("elytra") && cfg.style("mace"))) { elState = 0; return false; }
        int fw = slotOf(self, Items.FIREWORK_ROCKET), mace = slotOf(self, Items.MACE);
        if (!self.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) || fw < 0 || mace < 0) { elState = 0; return false; }
        if (elCd > 0) elCd--;
        if (elState == 0) {
            if (self.isOnGround() && elCd == 0 && dist > 14 && dist < 90) { elState = 1; elTimer = 0; }
            else return false;
        }
        switch (elState) {
            case 1 -> {
                in.hotbarSlot = fw; in.jump = true;
                if (!self.isOnGround() && self.getVelocity().y < 0.1) { self.startGliding(); elState = 2; elTimer = 0; }
                else if (++elTimer > 20) { elState = 0; elCd = 100; }
                return true;
            }
            case 2 -> {
                in.hotbarSlot = fw;
                Util.aimAt(self, target.getEyePos().add(0, Math.min(18, dist * 0.25), 0), in);
                if (elTimer++ % 30 == 0) in.throwItem = true;
                if (!self.isGliding()) { elState = 0; elCd = 100; return true; }
                if (dist < 22) elState = 3;
                return true;
            }
            case 3 -> {
                in.hotbarSlot = mace;
                Util.aimAt(self, target.getEyePos(), in);
                if (dist <= 3.5 && self.getAttackCooldownProgress(0.5f) >= 0.95f) in.attack = true;
                if (self.isOnGround() || (!self.isGliding() && ++elTimer > 40)) { elState = 0; elCd = 200; }
                return true;
            }
            default -> { elState = 0; return false; }
        }
    }

    static int slotOf(FakeBot p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    static int firstSlot(FakeBot p, Item... items) {
        for (Item it : items) { int s = slotOf(p, it); if (s >= 0) return s; }
        return -1;
    }
}
