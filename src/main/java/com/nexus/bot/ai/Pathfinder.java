package com.nexus.bot.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/** A* simples: anda, sobe 1 bloco, desce ate 3. Evita lava, teia, fogo, magma e cacto. */
public class Pathfinder {
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static class Node {
        final BlockPos pos; final Node parent; final double g, f;
        Node(BlockPos p, Node par, double g, double f) { pos = p; parent = par; this.g = g; this.f = f; }
    }

    public static boolean loaded(World w, BlockPos p) { return w.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4); }

    public static boolean passable(World w, BlockPos p) {
        if (!loaded(w, p) || w.isOutOfHeightLimit(p.getY())) return false;
        BlockState s = w.getBlockState(p);
        return s.getCollisionShape(w, p).isEmpty() && s.getFluidState().isEmpty()
            && !s.isOf(Blocks.COBWEB) && !s.isOf(Blocks.FIRE) && !s.isOf(Blocks.SWEET_BERRY_BUSH);
    }

    public static boolean floor(World w, BlockPos p) {
        if (!loaded(w, p) || w.isOutOfHeightLimit(p.getY())) return false;
        BlockState s = w.getBlockState(p);
        return !s.getCollisionShape(w, p).isEmpty() && !s.isOf(Blocks.MAGMA_BLOCK) && !s.isOf(Blocks.CACTUS);
    }

    public static boolean standing(World w, BlockPos p) { return passable(w, p) && passable(w, p.up()) && floor(w, p.down()); }

    private static double h(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    public static List<BlockPos> find(World w, BlockPos start, BlockPos goal, double stopDist, int maxNodes) {
        double stop2 = stopDist * stopDist;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble((Node n) -> n.f));
        Map<Long, Double> bestG = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        open.add(new Node(start, null, 0, h(start, goal)));
        bestG.put(start.asLong(), 0.0);
        int visited = 0;
        while (!open.isEmpty() && visited < maxNodes) {
            Node n = open.poll();
            if (!closed.add(n.pos.asLong())) continue;
            visited++;
            if (n.pos.getSquaredDistance(goal) <= stop2) return build(n);
            for (int[] d : DIRS) {
                BlockPos f = n.pos.add(d[0], 0, d[1]);
                if (standing(w, f)) { push(open, bestG, closed, n, f, 1.0, goal); continue; }
                if (passable(w, f) && passable(w, f.up())) {
                    for (int k = 1; k <= 3; k++) {
                        BlockPos g = f.down(k);
                        if (!passable(w, g)) break;
                        if (floor(w, g.down())) { if (passable(w, g.up())) push(open, bestG, closed, n, g, 1.2 + 0.3 * k, goal); break; }
                    }
                } else if (passable(w, n.pos.up(2)) && standing(w, f.up())) {
                    push(open, bestG, closed, n, f.up(), 1.6, goal);
                }
            }
        }
        return null;
    }

    private static void push(PriorityQueue<Node> open, Map<Long, Double> bestG, Set<Long> closed, Node parent, BlockPos p, double cost, BlockPos goal) {
        long k = p.asLong();
        if (closed.contains(k)) return;
        double g = parent.g + cost;
        Double old = bestG.get(k);
        if (old != null && old <= g) return;
        bestG.put(k, g);
        open.add(new Node(p, parent, g, g + h(p, goal)));
    }

    private static List<BlockPos> build(Node end) {
        LinkedList<BlockPos> out = new LinkedList<>();
        for (Node n = end; n != null && n.parent != null; n = n.parent) out.addFirst(n.pos);
        return new ArrayList<>(out);
    }
}
