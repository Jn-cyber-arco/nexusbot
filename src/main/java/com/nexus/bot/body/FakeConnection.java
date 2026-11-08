package com.nexus.bot.body;

import io.netty.channel.embedded.EmbeddedChannel;
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
    @Override public void send(Packet<?> packet, PacketCallbacks callbacks) {}
    @Override public void send(Packet<?> packet, PacketCallbacks callbacks, boolean flush) {}
    @Override public void disconnect(Text reason) {}
    @Override public void tick() {}
    @Override public SocketAddress getAddress() { return new InetSocketAddress("127.0.0.1", 0); }
}
