package com.nexus.bot.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class PvpController {
    static final double REACH = 3.0, SPACING_MIN = 3.01, SPACING_MAX = 3.50;
    private final Random rng = new Random();
    private int wTap, sTap, strafeTimer, blockHit, lastHurt, reaction;
    private boolean strafeRight = true;

    public void tick(PlayerEntity self, LivingEntity target, BotInput in, BotConfig cfg) {
        in.releaseMovement(); in.attack = false; in.use = false; in.hotbarSlot = -1;
        double dist = self.distanceTo(target);
        aim(self, target, in);

        in.sprint = true;
        if (dist > SPACING_MAX) in.forward = true;
        else if (dist < SPACING_MIN) in.back = true;
        else in.forward = true;

        if (wTap > 0) { in.forward = false; in.sprint = false; wTap--; }
        if (sTap > 0) { in.forward = false; in.back = true; sTap--; }

        if (cfg.tech("strafing") && --strafeTimer <= 0) { strafeRight = !strafeRight; strafeTimer = 6 + rng.nextInt(8); }
        if (cfg.tech("strafing")) { in.left = !strafeRight; in.right = strafeRight; }

        boolean falling = !self.isOnGround() && self.getVelocity().y < 0;
        if (cfg.tech("critchain") && self.isOnGround() && dist < 4.5) in.jump = true;

        Item want = Items.NETHERITE_SWORD;
        if (cfg.style("mace") && self.fallDistance > 3.0f) want = Items.MACE;
        int slot = slotOf(self, want);
        if (slot < 0 && want != Items.NETHERITE_SWORD) slot = slotOf(self, Items.NETHERITE_SWORD);
        if (slot < 0) slot = slotOf(self, Items.DIAMOND_SWORD);
        if (slot >= 0) in.hotbarSlot = slot;

        boolean ready = self.getAttackCooldownProgress(0.5f) >= 0.95f;
        boolean critWindow = !cfg.tech("peakcrit") || falling || self.fallDistance > 0f;
        if (dist <= REACH && ready && critWindow) {
            if (++reaction >= cfg.difficulty.reactionTicks && rng.nextDouble() < cfg.difficulty.hitChance) {
                in.attack = true; reaction = 0;
                if (cfg.tech("wtap")) wTap = 1;
                if (cfg.tech("stap") && rng.nextDouble() < cfg.difficulty.comboChance) sTap = 2;
                if (cfg.tech("blockhit")) blockHit = 3;
            }
        }
        if (blockHit > 0 && self.getOffHandStack().isOf(Items.SHIELD)) { in.use = true; blockHit--; }

        if (cfg.tech("jumpreset") && self.hurtTime > lastHurt && self.isOnGround()) in.jump = true;
        lastHurt = self.hurtTime;
    }

    private void aim(PlayerEntity self, LivingEntity t, BotInput in) {
        Vec3d d = t.getEyePos().subtract(self.getEyePos());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        in.yaw = (float) (MathHelper.atan2(d.z, d.x) * 180.0 / Math.PI) - 90f;
        in.pitch = (float) -(MathHelper.atan2(d.y, flat) * 180.0 / Math.PI);
    }

    static int slotOf(PlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
}
