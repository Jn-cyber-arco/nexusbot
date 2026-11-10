package com.nexus.bot.ai;

import com.nexus.bot.Util;
import com.nexus.bot.body.FakeBot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Segue um caminho do Pathfinder escrevendo nas "teclas" do bot. */
public class Nav {
    private List<BlockPos> path = null;
    private int idx = 0, replan = 0, stuck = 0;
    private BlockPos goal = null;
    private Vec3d lastPos = Vec3d.ZERO;

    /** Retorna true quando chegou perto o bastante (stopDist). */
    public boolean goTo(FakeBot bot, BlockPos g, double stopDist, BotInput in) {
        Vec3d bp = Util.pos(bot);
        if (bp.distanceTo(Util.center(g)) <= stopDist) return true;

        if (goal == null || goal.getManhattanDistance(g) > 3 || --replan <= 0 || path == null) {
            goal = g;
            replan = 40 + (bot.getId() % 20);
            path = Pathfinder.find(bot.getEntityWorld(), bot.getBlockPos(), g, Math.max(1.0, stopDist - 0.5), 2500);
            idx = 0;
        }

        if (path == null || idx >= path.size()) {   // sem caminho: anda reto e pula obstaculos
            Util.faceFlat(bot, Util.center(g), in);
            in.forward = true; in.sprint = true; in.jump = bot.horizontalCollision;
            return false;
        }

        BlockPos n = path.get(idx);
        Vec3d c = new Vec3d(n.getX() + 0.5, n.getY(), n.getZ() + 0.5);
        double hd = Math.hypot(c.x - bp.x, c.z - bp.z);
        if (hd < 0.45 && Math.abs(bp.y - n.getY()) < 1.5) {
            idx++;
            if (idx >= path.size()) return false;
            n = path.get(idx);
            c = new Vec3d(n.getX() + 0.5, n.getY(), n.getZ() + 0.5);
        }
        in.yaw = (float) (MathHelper.atan2(c.z - bp.z, c.x - bp.x) * 180.0 / Math.PI) - 90f;
        in.pitch = 0f;
        in.forward = true; in.sprint = true;
        in.jump = n.getY() > bot.getBlockY() || bot.horizontalCollision;

        if (++stuck % 20 == 0) {
            if (bp.distanceTo(lastPos) < 0.4) { path = null; in.jump = true; }
            lastPos = bp;
        }
        return false;
    }
}
