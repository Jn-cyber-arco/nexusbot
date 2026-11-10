package com.nexus.bot.ai;

import com.nexus.bot.Util;
import com.nexus.bot.body.FakeBot;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** Quebra blocos (com tempo de mineracao) e cava escadas/tuneis em direcao a um alvo. */
public class Digger {
    private static final int UNSET = -1000;
    private BlockPos mining = null;
    private int remaining = UNSET;
    public boolean blocked = false;

    /** Minera um bloco. Retorna true quando terminou (quebrou ou ja era ar). */
    public boolean mine(FakeBot bot, BlockPos pos, BotInput in) {
        ServerWorld w = bot.getEntityWorld();
        BlockState st = w.getBlockState(pos);
        if (st.isAir()) { mining = null; return true; }
        float hardness = st.getHardness(w, pos);
        if (hardness < 0) { blocked = true; mining = null; return true; }

        Tools.select(bot, st, in);
        Util.aimAt(bot, Util.center(pos), in);

        if (!pos.equals(mining)) { mining = pos; remaining = UNSET; return false; }   // 1o tick: so equipa a ferramenta
        if (remaining == UNSET) {
            float speed = Math.max(1f, bot.getBlockBreakingSpeed(st));
            remaining = Math.max(4, (int) Math.ceil(hardness * 30f / speed));
        }
        remaining--;
        if (remaining % 4 == 0) bot.swingHand(Hand.MAIN_HAND);
        if (remaining <= 0) {
            bot.interactionManager.tryBreakBlock(pos);
            mining = null; remaining = UNSET;
            return true;
        }
        return false;
    }

    private boolean solid(ServerWorld w, BlockPos p) { return !w.getBlockState(p).getCollisionShape(w, p).isEmpty(); }
    private boolean lava(ServerWorld w, BlockPos p) { return w.getFluidState(p).isIn(FluidTags.LAVA); }

    /** Um passo em direcao ao alvo: limpa o caminho (cavando) e anda. */
    public void stepToward(FakeBot bot, BlockPos target, BotInput in) {
        ServerWorld w = bot.getEntityWorld();
        BlockPos p = bot.getBlockPos();
        int dx = target.getX() - p.getX(), dy = target.getY() - p.getY(), dz = target.getZ() - p.getZ();

        Direction d;
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) d = dx > 0 ? Direction.EAST : Direction.WEST;
        else if (dz != 0) d = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        else d = bot.getHorizontalFacing();
        int v = dy < 0 ? -1 : (dy > 0 ? 1 : 0);

        BlockPos nf = p.offset(d, 1).up(v);
        List<BlockPos> clear = new ArrayList<>();
        if (v == 1) clear.add(p.up(2));
        clear.add(nf); clear.add(nf.up());
        if (v == -1) clear.add(p.offset(d, 1).up());

        if (!Pathfinder.loaded(w, nf)) return;
        for (BlockPos c : clear) if (lava(w, c)) { blocked = true; return; }
        if (lava(w, nf.down())) { blocked = true; return; }

        for (BlockPos c : clear) {
            if (solid(w, c)) {
                if (mine(bot, c, in)) { /* quebrou: continua no proximo tick */ }
                return;
            }
        }
        Util.faceFlat(bot, Util.center(nf), in);
        in.forward = true;
        in.jump = v == 1 || bot.horizontalCollision;
    }
}
