package dev.yanianz.reminions.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.UUID;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.utils.Location3f;

public class MinionStorageAdapter implements JsonSerializer<MinionStorage>, JsonDeserializer<MinionStorage> {

    @Override
    public JsonElement serialize(MinionStorage storage, Type type, JsonSerializationContext ctx) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", storage.name());
        obj.addProperty("owner", storage.owner().toString());
        obj.add("inventory", ctx.serialize(storage.inventory(), MinionInventory.class));
        obj.add("loc", ctx.serialize(storage.location(), Location3f.class));
        return obj;
    }

    @Override
    public MinionStorage deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
        JsonObject obj = element.getAsJsonObject();
        String name = obj.get("name").getAsString();
        UUID owner = UUID.fromString(obj.get("owner").getAsString());
        MinionInventory inventory = ctx.deserialize(obj.get("inventory"), MinionInventory.class);
        Location3f loc = ctx.deserialize(obj.getAsJsonObject("loc"), Location3f.class);
        return new MinionStorage(name, owner, inventory, loc);
    }
}
