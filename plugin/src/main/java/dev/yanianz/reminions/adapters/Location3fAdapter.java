package dev.yanianz.reminions.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import dev.yanianz.reminions.utils.Location3f;

public class Location3fAdapter implements JsonDeserializer<Location3f>, JsonSerializer<Location3f> {

    @Override
    public Location3f deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
        JsonObject obj = element.getAsJsonObject();
        String world = obj.get("world").getAsString();
        double x = obj.get("x").getAsDouble();
        double y = obj.get("y").getAsDouble();
        double z = obj.get("z").getAsDouble();
        float yaw   = obj.has("yaw")   ? obj.get("yaw").getAsFloat()   : 0.0F;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0.0F;
        return new Location3f(x, y, z, yaw, pitch, world);
    }

    @Override
    public JsonElement serialize(Location3f loc, Type type, JsonSerializationContext ctx) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", loc.getWorldName() == null ? "world" : loc.getWorldName());
        obj.addProperty("x", loc.getX());
        obj.addProperty("y", loc.getY());
        obj.addProperty("z", loc.getZ());
        obj.addProperty("yaw", loc.getYaw());
        obj.addProperty("pitch", loc.getPitch());
        return obj;
    }
}
