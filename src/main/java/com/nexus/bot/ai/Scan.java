package com.nexus.bot.ai;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Predicate;

public class Scan {
    public static BlockPos nearest(World w, BlockPos c, int rx, int ry, Predicate<BlockState> pr, Predicate<BlockPos> extra) {
        BlockPos best = null; double bd = Double.MAX_VALUE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -rx; x <= rx; x++) for (int z = -rx; z <= rx; z++) {
            int px = c.getX() + x, pz = c.getZ() + z;
            if (!w.getChunkManager().isChunkLoaded(px >> 4, pz >> 4)) continue;
            for (int y = -ry; y <= ry; y++) {
                int py = c.getY() + y;
                if (w.isOutOfHeightLimit(py)) continue;
                m.set(px, py, pz);
                if (!pr.test(w.getBlockState(m))) continue;
                BlockPos im = m.toImmutable();
                if (extra != null && !extra.test(im)) continue;
                double d = im.getSquaredDistance(c);
                if (d < bd) { bd = d; best = im; }
            }
        }
        return best;
    }
}
