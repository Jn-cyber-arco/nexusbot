package com.nexus.bot.ai;

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
    public String owner = null;          // quem recebe os avisos do modo espiao
    public boolean griefAllowed = false; // desligado por padrao

    public final Set<String> styles = new HashSet<>(List.of("sword", "axe", "shield", "mace", "windcharge"));
    public final Set<String> techniques = new HashSet<>(List.of(
        "wtap", "stap", "strafing", "critchain", "jumpreset", "hitselect", "peakcrit", "blockhit", "stunslam"));

    public boolean style(String s) { return styles.contains(s); }
    public boolean tech(String t) { return techniques.contains(t); }
}
