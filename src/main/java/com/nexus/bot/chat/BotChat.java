package com.nexus.bot.chat;

import java.util.*;

public class BotChat {
    private static final Random R = new Random();
    private static String pick(String... a) { return a[R.nextInt(a.length)]; }

    public static String spawn() { return pick("Cheguei!", "Fala galera, tô online.", "Bora que hoje eu ganho.", "Quem quer perder primeiro?", "Salve, time!", "Entrei, sem lag dessa vez.", "Chegou o brabo."); }
    public static String kill() { return pick("Ez.", "Vai treinar mais um pouco.", "Mais um pra conta.", "Gg!", "Nem senti.", "Boa tentativa, mano.", "Tá fácil demais kkk", "Próximo!"); }
    public static String death() { return pick("Eita, me pegaram.", "Foi lag, com certeza.", "Revanche agora!", "Gg, vc jogou bem.", "Isso foi sorte.", "Tá, essa foi justa.", "Vou voltar mais forte."); }
    public static String follow() { return pick("Fechou, tô te seguindo.", "Pode ir que eu vou atrás.", "Na sua cola!", "Bora, lidera aí.", "Tô logo atrás de vc."); }
    public static String stop() { return pick("Parei.", "Ok, fico aqui.", "Tranquilo, tô parado.", "Blz, aguardando."); }
    public static String attack(String n) { return pick("Vou atrás do " + n + "!", "Bora pra cima do " + n + "!", n + " tá na minha mira.", "O " + n + " vai ver só."); }
    public static String doing(String s) { return pick("Agora tô no modo " + s + ".", "Tô no modo " + s + ", mano.", "Modo " + s + " ligado."); }
    public static String hello() { return pick("E aí!", "Opa, fala!", "Salve!", "Oi, tudo certo?", "Fala comigo!", "Eae, bora jogar?"); }
    public static String unknown() { return pick("Não entendi, repete?", "Hm? Fala de novo.", "Pode explicar melhor?", "Perdi, manda de novo.", "Como assim?"); }
    public static String thanks() { return pick("Tmj!", "Valeu!", "É nóis.", "Tamo junto."); }
    public static String trash() { return pick("Fala isso na minha cara no PvP.", "Calma, campeão.", "Vem tirar a prova.", "Hmm, vamos ver."); }
    public static String teamCall(String n) { return pick("Alvo marcado: " + n + "! Todo mundo nele!", "Chifre ouvido! Vamos no " + n + "!", "É o " + n + ", bora!", "Time, foco no " + n + "!"); }
    public static String cantBuild() { return pick("Ainda não aprendi a construir, kk.", "Construção ainda tá em obras.", "Isso eu ainda não sei fazer."); }
    public static String idle() { return pick("Alguém aí?", "Tá quieto demais...", "Qual o plano?", "Alguém viu diamante?", "Esse mundo tá bonito.", "Preciso de um desafio.", "Quem vem pro x1?", "Tô entediado."); }
    public static String reply() { return pick("Concordo.", "Kkkk verdade.", "Boa!", "Hm, pode ser.", "Eu também acho.", "Bora então.", "Que isso, mano."); }

    /** Eventos da sobrevivencia. */
    public static String event(String key) {
        return switch (key) {
            case "wood" -> pick("Cortando uns troncos.", "Madeira, madeira, madeira...", "Esse tronco é meu.", "Derrubando árvore.");
            case "wood_done" -> pick("Madeira pronta! Craftei picareta, espada e machado.", "Mesa de trabalho feita, bora pra pedra.");
            case "stone_done" -> pick("Pedra feita: ferramentas e fornalha prontas.", "Era da pedra concluída!");
            case "food", "hunt" -> pick("Hora de caçar comida.", "Vaca, porco... qualquer coisa serve.", "Bife vem aí.");
            case "cook", "smelt" -> pick("Fornalha ligada, aguenta aí.", "Cozinhando, já volto.", "Fundindo tudo.");
            case "cook_done" -> pick("Tá pronto, cheiro bom!", "Terminei de cozinhar.");
            case "eat" -> pick("Hora de comer.", "Nham.", "Tô com fome, mano.");
            case "food_done" -> pick("Comida estocada, agora é ferro.", "Barriga cheia, bora minerar.");
            case "ore" -> pick("Achei minério!", "Ferro à vista.", "Cavando atrás de ferro.");
            case "craft" -> pick("Craftando equipamento.", "Mais uma peça de ferro pronta.");
            case "iron_done" -> pick("Full ferro! Espada, picareta, armadura e balde.", "Equipado de ferro completo. Próxima parada: diamante.");
            case "lowhp" -> pick("Tô quase morrendo!", "Vida baixa, cuidado.", "Preciso comer uma maçã dourada.");
            case "diamond_done" -> pick("Diamante! Picareta e armadura de diamante prontas.", "Full diamante, agora é o Nether.");
            case "nether_in" -> pick("Tô no Nether, cuidado com o calor.", "Entrei no portal, bora achar debris.");
            case "portal" -> pick("Construindo o portal do Nether.", "Obsidiana pronta, montando o portal.");
            case "ancient_done" -> pick("Achei os detritos antigos!", "Debris na mão, hora de fundir.");
            case "netherite_done" -> pick("Full netherite, tô pronto pra guerra.", "Netherite completo!");
            case "need_template" -> pick("Tenho o lingote, mas falta o molde de netherite (Bastion).", "Sem molde de ferreiro não rola o upgrade.");
            case "grief" -> pick("Fogo no pavio!", "TNT no ar!", "Sai de baixo!");
            case "build" -> pick("Bora construir!", "Começando a obra.", "Deixa que eu monto isso.");
            case "no_material" -> pick("Acabou o material da obra.", "Preciso de mais blocos pra terminar.");
            default -> "";
        };
    }

    public static String parse(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        if (m.contains("me segue") || m.contains("me siga") || m.contains("vem comigo") || m.contains("vem cá")) return "follow";
        if (m.contains("fica aí") || m.contains("fica ai") || m.contains("pare") || (m.contains("para") && m.length() < 28)) return "stop";
        if (m.contains("ataca ") || m.contains("mata ")) return "attack";
        if (m.contains("o que") && m.contains("fazendo")) return "doing";
        if (m.contains("espiona") || m.contains("espião") || m.contains("espiao")) return "spy";
        if (m.contains("sobreviv") || m.contains("minera") || m.contains("farma")) return "survival";
        if (m.contains("constr") || m.contains("casa") || m.contains("base") || m.contains("portal")) return "build";
        if (m.contains("valeu") || m.contains("obrigad") || m.equals("gg") || m.contains("boa ")) return "thanks";
        if (m.contains("noob") || m.contains("lixo") || m.contains("ruim")) return "trash";
        if (m.startsWith("oi") || m.contains("fala") || m.contains("salve") || m.contains("olá") || m.contains("ola") || m.contains("eae")) return "hello";
        return "unknown";
    }

    public static String targetName(String msg) {
        String[] p = msg.trim().split("\\s+");
        for (int i = 0; i < p.length - 1; i++)
            if (p[i].equalsIgnoreCase("ataca") || p[i].equalsIgnoreCase("mata")) return p[i + 1].replaceAll("[^A-Za-z0-9_]", "");
        return null;
    }
}
