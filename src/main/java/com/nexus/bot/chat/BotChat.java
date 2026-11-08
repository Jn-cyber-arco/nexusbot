package com.nexus.bot.chat;

import java.util.*;

public class BotChat {
    private static final Random R = new Random();
    private static String pick(String... a) { return a[R.nextInt(a.length)]; }

    public static String spawn()  { return pick("Cheguei!", "Fala galera, to online.", "Bora que hoje eu ganho.", "Quem quer perder primeiro?"); }
    public static String kill()   { return pick("Ez.", "Vai treinar mais um pouco.", "Mais um pra conta.", "Gg!"); }
    public static String death()  { return pick("Eita, me pegaram.", "Foi lag, com certeza.", "Revanche agora!", "Gg, vc jogou bem."); }
    public static String follow() { return pick("Fechou, to te seguindo.", "Pode ir que eu vou atras.", "Na sua cola!"); }
    public static String stop()   { return pick("Parei.", "Ok, fico aqui.", "Tranquilo, to parado."); }
    public static String attack(String n) { return pick("Vou atras do " + n + "!", "Bora pra cima do " + n + "!", n + " ta na minha mira."); }
    public static String doing(String s)  { return "Agora eu to no modo " + s + "."; }
    public static String hello()  { return pick("E ai!", "Opa, fala!", "Salve!", "Oi, tudo certo?"); }
    public static String unknown(){ return pick("Nao entendi, repete?", "Hm? Fala de novo.", "Pode explicar melhor?"); }

    public static String parse(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        if (m.contains("me segue") || m.contains("me siga") || m.contains("vem comigo")) return "follow";
        if (m.contains("fica ai") || m.contains("pare") || (m.contains("para") && m.length() < 25)) return "stop";
        if (m.contains("ataca ") || m.contains("mata ")) return "attack";
        if (m.contains("o que") && m.contains("fazendo")) return "doing";
        if (m.contains("espiona") || m.contains("espiao")) return "spy";
        if (m.startsWith("oi") || m.contains("fala") || m.contains("salve") || m.contains("ola")) return "hello";
        return "unknown";
    }

    public static String targetName(String msg) {
        String[] p = msg.trim().split("\\s+");
        for (int i = 0; i < p.length - 1; i++)
            if (p[i].equalsIgnoreCase("ataca") || p[i].equalsIgnoreCase("mata")) return p[i + 1].replaceAll("[^A-Za-z0-9_]", "");
        return null;
    }
}
