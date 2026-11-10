package com.nexus.bot.body;

import com.nexus.bot.mixin.ClientConnectionAccessor;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** Conexao falsa: tem um canal "de mentira" e engole os pacotes. */
public class FakeConnection extends ClientConnection {
    private final EmbeddedChannel ch = new EmbeddedChannel();

    public FakeConnection() {
        super(NetworkSide.SERVERBOUND);
        // nomes que o Minecraft usa no pipeline (evita erro caso ele tente trocar/adicionar handlers)
        for (String n : new String[]{"timeout", "splitter", "decoder", "inbound_config", "prepender", "encoder", "outbound_config", "packet_handler"})
            ch.pipeline().addLast(n, new ChannelDuplexHandler());
        ((ClientConnectionAccessor) (Object) this).nexusSetChannel(ch);
    }

    /** Descarta o que foi "escrito" no canal falso (evita acumular memoria). */
    public void drain() {
        try { ch.releaseOutbound(); ch.releaseInbound(); } catch (Exception ignored) {}
    }

    @Override public boolean isOpen() { return true; }
    @Override public void send(Packet<?> packet) {}

    // Sem @Override de proposito: o tipo do parametro muda entre versoes.
    public void send(Packet<?> packet, PacketCallbacks callbacks) {}
    public void send(Packet<?> packet, PacketCallbacks callbacks, boolean flush) {}
    public void send(Packet<?> packet, ChannelFutureListener listener) {}
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {}

    @Override public void disconnect(Text reason) {}
    @Override public void tick() {}
    @Override public SocketAddress getAddress() { return new InetSocketAddress("127.0.0.1", 0); }
}
