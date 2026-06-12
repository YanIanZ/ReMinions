package dev.yanianz.reminions.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import dev.yanianz.reminions.core.minion.MinionInventory;
import org.bukkit.inventory.ItemStack;

public class ItemDataAdapter implements JsonSerializer<MinionInventory.ItemData>, JsonDeserializer<MinionInventory.ItemData> {

    @Override
    public JsonElement serialize(MinionInventory.ItemData itemData, Type type, JsonSerializationContext ctx) {
        JsonObject obj = new JsonObject();
        obj.add("item", ctx.serialize(itemData.getItem(), ItemStack.class));
        obj.addProperty("amount", itemData.getAmount());
        return obj;
    }

    @Override
    public MinionInventory.ItemData deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
        JsonObject obj = element.getAsJsonObject();
        ItemStack item = ctx.deserialize(obj.get("item"), ItemStack.class);
        int amount = obj.get("amount").getAsInt();
        return MinionInventory.ItemData.of(item, amount);
    }
}
