package com.nexus.bot.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Permite colocar um canal falso na conexao do bot (evita NullPointerException no canal). */
@Mixin(ClientConnection.class)
public interface ClientConnectionAccessor {
    @Accessor("channel")
    void nexusSetChannel(Channel channel);
}
