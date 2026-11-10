package com.nexus.bot.ai;

import java.util.Random;
import java.util.Set;

/** Nicks de null: _____h___, iags.jCk, gTVlJoXe, H.jokC820h, aV5_64G ... */
public class NullNames {
    private static final String LET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALN = LET + "0123456789";

    private static char c(Random r, String s) { return s.charAt(r.nextInt(s.length())); }

    private static String underscore(Random r, int len) {          // ____h____
        len = Math.max(6, len);
        int letters = r.nextInt(5) == 0 ? 2 : 1;
        int rest = len - letters;
        int left = 2 + r.nextInt(Math.max(1, rest - 3));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < left; i++) sb.append('_');
        for (int i = 0; i < letters; i++) sb.append(c(r, LET));
        while (sb.length() < len) sb.append('_');
        return sb.toString();
    }

    private static String dot(Random r, int len) {                  // iags.jCk / H.jokC820h
        len = Math.max(5, len);
        int dotAt = 1 + r.nextInt(Math.min(5, len - 3));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i == dotAt) sb.append('.');
            else sb.append(i > len - 4 && r.nextBoolean() ? c(r, ALN) : c(r, LET));
        }
        return sb.toString();
    }

    private static String random(Random r, int len) {               // gTVlJoXe / aV5_64G
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char ch = (r.nextInt(4) == 0) ? c(r, ALN) : c(r, LET);
            if (i > 0 && i < len - 1 && r.nextInt(14) == 0) ch = '_';
            sb.append(ch);
        }
        return sb.toString();
    }

    public static String generate(Random r, int min, int max, String style, Set<String> used) {
        min = Math.max(3, Math.min(16, min));
        max = Math.max(min, Math.min(16, max));
        for (int t = 0; t < 80; t++) {
            int len = min + r.nextInt(max - min + 1);
            String s = style == null ? "mixed" : style;
            if (s.equals("mixed")) { int k = r.nextInt(10); s = k < 3 ? "underscore" : (k < 6 ? "dot" : "random"); }
            String n = switch (s) { case "underscore" -> underscore(r, len); case "dot" -> dot(r, len); default -> random(r, len); };
            if (n.length() > 16) n = n.substring(0, 16);
            if (!used.contains(n)) return n;
        }
        return "Null" + r.nextInt(99999);
    }
}
