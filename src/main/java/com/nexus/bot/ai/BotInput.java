package com.nexus.bot.ai;

public class BotInput {
    public boolean forward, back, left, right, jump, sneak, sprint, attack, use, useMain, throwItem;
    public int hotbarSlot = -1;
    public float yaw, pitch;
    public void releaseMovement() { forward = back = left = right = jump = sneak = sprint = false; }
    public void resetAll() { releaseMovement(); attack = use = useMain = throwItem = false; hotbarSlot = -1; }
}
