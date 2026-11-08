package com.nexus.bot.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Segue o alvo de longe, agachado e fora do campo de visao dele. Avisa so o dono (posicao visivel). */
public class SpyController {
    private int reportTimer = 0;

    public void tick(PlayerEntity self, LivingEntity target, BotInput in, ServerPlayerEntity owner) {
        in.releaseMovement(); in.attack = false; in.use = false; in.hotbarSlot = -1;
        Vec3d toBot = self.getPos().subtract(target.getPos()).multiply(1, 0, 1);
        double dist = toBot.length();
        Vec3d look = target.getRotationVec(1f).multiply(1, 0, 1).normalize();
        boolean inFov = dist > 0.01 && look.dotProduct(toBot.normalize()) > 0.3;

        Vec3d d = target.getEyePos().subtract(self.getEyePos());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        in.yaw = (float) (MathHelper.atan2(d.z, d.x) * 180.0 / Math.PI) - 90f;
        in.pitch = (float) -(MathHelper.atan2(d.y, flat) * 180.0 / Math.PI);

        in.sneak = dist < 14;
        if (inFov) { in.left = true; }
        else if (dist > 12) in.forward = true;
        else if (dist < 8) in.back = true;

        if (owner != null && --reportTimer <= 0) {
            reportTimer = 200;
            owner.sendMessage(Text.literal("[" + self.getName().getString() + "] alvo " + target.getName().getString()
                + " em " + (int) target.getX() + " " + (int) target.getY() + " " + (int) target.getZ()), false);
        }
    }
}
