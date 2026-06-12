package dev.yanianz.reminions.adapters;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import dev.yanianz.reminions.core.minion.MinionInventory;

public class MinionInventoryAdapter implements JsonSerializer<MinionInventory>, JsonDeserializer<MinionInventory> {

    @Override
    public JsonElement serialize(MinionInventory inventory, Type type, JsonSerializationContext ctx) {
        JsonObject obj = new JsonObject();
        JsonArray items = new JsonArray();
        for (MinionInventory.ItemData item : inventory.getSnapshot()) {
            items.add(ctx.serialize(item));
        }
        obj.add("items", items);
        obj.addProperty("collected", inventory.getCollected());
        return obj;
    }

    @Override
    public MinionInventory deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
        JsonObject obj = element.getAsJsonObject();
        ArrayList<MinionInventory.ItemData> items = new ArrayList<>();
        for (JsonElement entry : obj.getAsJsonArray("items")) {
            items.add(ctx.deserialize(entry, MinionInventory.ItemData.class));
        }
        long collected = obj.get("collected").getAsLong();
        return new MinionInventory(items, collected);
    }
}
