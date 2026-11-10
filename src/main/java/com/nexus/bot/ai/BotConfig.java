package com.nexus.bot.ai;

import com.mojang.authlib.properties.Property;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public class BotConfig {
    public enum Mode { SURVIVAL, FOLLOW, REVENGE, FFA, SPY }
    public enum Difficulty {
        EASY(0.35, 0.10, 8), MEDIUM(0.55, 0.30, 5), HARD(0.75, 0.50, 3), EXPERT(0.90, 0.70, 2), PERFECT(1.0, 0.90, 1);
        public final double hitChance, comboChance; public final int reactionTicks;
        Difficulty(double h, double c, int r) { hitChance = h; comboChance = c; reactionTicks = r; }
    }

    public Mode mode = Mode.FFA;
    public Difficulty difficulty = Difficulty.HARD;
    public String team = null;
    public String followTarget = null;
    public String owner = null;
    public String forcedTarget = null;
    public long forcedUntil = 0;
    public boolean isNull = false;
    public boolean chat = true;
    public boolean griefAllowed = false;   // chave 1 do grief (a chave 2 e o settings "grief")
    public int phaseOrd = 0;               // fase de sobrevivencia salva (sobrevive ao respawn)
    public Property skin = null;
    public Vec3d home = null;

    public final Set<String> off = new HashSet<>();

    public boolean tech(String t) { return Settings.on(t) && !off.contains(t); }
    public boolean style(String s) { return Settings.on(s) && !off.contains(s); }
}
