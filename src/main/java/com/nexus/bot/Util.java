package com.nexus.bot;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class Util {
    public static Vec3d pos(Entity e) { return new Vec3d(e.getX(), e.getY(), e.getZ()); }
}
