package dev.yanianz.reminions.adapters;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;

public class ItemStackAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

    @Override
    public JsonElement serialize(ItemStack stack, Type type, JsonSerializationContext ctx) {
        try {
            byte[] bytes = stack.serializeAsBytes();
            String encoded = Base64.getEncoder().encodeToString(bytes);
            return new JsonPrimitive(encoded);
        } catch (Exception e) {
            throw new JsonParseException("Failed to serialize ItemStack", e);
        }
    }

    @Override
    public ItemStack deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
        try {
            String encoded = element.getAsString();
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize ItemStack", e);
        }
    }
}
