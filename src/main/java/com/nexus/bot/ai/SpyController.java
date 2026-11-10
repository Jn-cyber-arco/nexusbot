package com.nexus.bot.ai;

import com.nexus.bot.Util;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Segue o alvo de longe, agachado e fora do campo de visao. Avisa so o dono (posicao visivel). */
public class SpyController {
    private int reportTimer = 0;

    public void tick(PlayerEntity self, LivingEntity target, BotInput in, ServerPlayerEntity owner) {
        in.resetAll();
        Vec3d toBot = Util.pos(self).subtract(Util.pos(target)).multiply(1, 0, 1);
        double dist = toBot.length();
        Vec3d look = target.getRotationVec(1f).multiply(1, 0, 1).normalize();
        boolean inFov = dist > 0.01 && look.dotProduct(toBot.normalize()) > 0.3;
        Util.aimAt(self, target.getEyePos(), in);
        in.sneak = dist < 14;
        if (inFov) in.left = true;
        else if (dist > 12) in.forward = true;
        else if (dist < 8) in.back = true;
        if (owner != null && --reportTimer <= 0) {
            reportTimer = 200;
            owner.sendMessage(Text.literal("[" + self.getName().getString() + "] alvo " + target.getName().getString()
                + " em " + (int) target.getX() + " " + (int) target.getY() + " " + (int) target.getZ()), false);
        }
    }
}
