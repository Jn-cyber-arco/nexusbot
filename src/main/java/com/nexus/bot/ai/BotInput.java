package com.nexus.bot.ai;

/** "Teclas" que a IA escreve; FakeBot aplica no corpo todo tick. */
public class BotInput {
    public boolean forward, back, left, right, jump, sneak, sprint, attack, use;
    public int hotbarSlot = -1;
    public float yaw, pitch;
    public void releaseMovement() { forward = back = left = right = jump = sneak = sprint = false; }
}
