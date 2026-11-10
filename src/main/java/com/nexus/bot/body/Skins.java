package com.nexus.bot.body;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.util.Uuids;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Skin da conta real com o mesmo nick do bot (busca nos servidores da Mojang, fora da thread do jogo). */
public class Skins {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public static boolean lookupable(String name) { return name != null && name.matches("[A-Za-z0-9_]{3,16}"); }

    private static String get(String url) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? res.body() : null;
    }

    public static CompletableFuture<Property> lookup(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String r1 = get("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
                if (r1 == null) return null;
                String id = JsonParser.parseString(r1).getAsJsonObject().get("id").getAsString();
                String r2 = get("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
                if (r2 == null) return null;
                JsonArray props = JsonParser.parseString(r2).getAsJsonObject().getAsJsonArray("properties");
                for (JsonElement e : props) {
                    JsonObject p = e.getAsJsonObject();
                    if ("textures".equals(p.get("name").getAsString()))
                        return new Property("textures", p.get("value").getAsString(), p.has("signature") ? p.get("signature").getAsString() : null);
                }
            } catch (Exception ignored) {}
            return null;
        });
    }

    public static GameProfile profile(String name, Property textures) {
        UUID id = Uuids.getOfflinePlayerUuid(name);
        if (textures == null) return new GameProfile(id, name);
        return new GameProfile(id, name, new PropertyMap(ImmutableMultimap.of("textures", textures)));
    }
}
