package com.nexus.bot.body;

import com.mojang.authlib.GameProfile;
import com.nexus.bot.ai.BotConfig;
import com.nexus.bot.ai.BotInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;

/** Jogador falso no servidor (estilo Carpet). */
public class FakeBot extends ServerPlayerEntity {
    public final BotInput input = new BotInput();
    public final BotConfig config = new BotConfig();
    public Consumer<FakeBot> brain = b -> {};
    public Consumer<DamageSource> onDeathHook = s -> {};

    private FakeBot(MinecraftServer server, ServerWorld world, GameProfile profile) {
        super(server, world, profile, SyncedClientOptions.createDefault());
    }

    public static FakeBot spawn(MinecraftServer server, ServerWorld world, String name, Vec3d pos) {
        GameProfile profile = new GameProfile(Uuids.getOfflinePlayerUuid(name), name);
        FakeBot bot = new FakeBot(server, world, profile);
        bot.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0f, 0f);
        server.getPlayerManager().onPlayerConnect(new FakeConnection(), bot, ConnectedClientData.createDefault(profile, false));
        bot.setHealth(20f);
        return bot;
    }

    @Override
    public void tick() {
        brain.accept(this);
        applyInput();
        try { super.tick(); this.playerTick(); } catch (NullPointerException ignored) {}
    }

    private void applyInput() {
        BotInput in = input;
        this.forwardSpeed = (in.forward ? 1f : 0f) - (in.back ? 1f : 0f);
        this.sidewaysSpeed = (in.left ? 1f : 0f) - (in.right ? 1f : 0f);
        this.setJumping(in.jump);
        this.setSneaking(in.sneak);
        this.setSprinting(in.sprint && in.forward && !in.sneak);
        this.setYaw(in.yaw); this.setPitch(in.pitch);
        this.setHeadYaw(in.yaw); this.setBodyYaw(in.yaw);
        if (in.hotbarSlot >= 0 && in.hotbarSlot < 9) this.getInventory().setSelectedSlot(in.hotbarSlot);
        if (in.use) { if (!this.isUsingItem()) this.setCurrentHand(Hand.OFF_HAND); }
        else if (this.isUsingItem()) this.clearActiveItem();
    }

    public void doAttack(Entity target) {
        this.attack(target);
        this.swingHand(Hand.MAIN_HAND);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        onDeathHook.accept(source);
    }
}
