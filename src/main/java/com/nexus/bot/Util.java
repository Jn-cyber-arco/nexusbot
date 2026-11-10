package com.nexus.bot;

import com.nexus.bot.ai.BotInput;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Util {
    public static Vec3d pos(Entity e) { return new Vec3d(e.getX(), e.getY(), e.getZ()); }
    public static Vec3d center(BlockPos p) { return new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5); }

    public static void aimAt(Entity self, Vec3d target, BotInput in) {
        Vec3d d = target.subtract(self.getEyePos());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        in.yaw = (float) (MathHelper.atan2(d.z, d.x) * 180.0 / Math.PI) - 90f;
        in.pitch = (float) -(MathHelper.atan2(d.y, flat) * 180.0 / Math.PI);
    }

    public static void faceFlat(Entity self, Vec3d target, BotInput in) {
        Vec3d p = pos(self);
        in.yaw = (float) (MathHelper.atan2(target.z - p.z, target.x - p.x) * 180.0 / Math.PI) - 90f;
        in.pitch = 0f;
    }

    /** Executa um comando como se fosse o console (silencioso). */
    public static void run(MinecraftServer server, String cmd) {
        try { server.getCommandManager().getDispatcher().execute(cmd, server.getCommandSource().withSilent()); }
        catch (Exception e) { e.printStackTrace(); }
    }
}