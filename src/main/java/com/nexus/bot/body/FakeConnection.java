package com.nexus.bot.body;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** Conexao falsa: engole todos os pacotes. */
public class FakeConnection extends ClientConnection {
    public FakeConnection() { super(NetworkSide.SERVERBOUND); }
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
