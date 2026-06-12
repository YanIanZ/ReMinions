package dev.yanianz.reminions.managers.importer.old;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

public class MinionAdapterOld implements JsonDeserializer<MinionOld> {

    @Override
    @SuppressWarnings("unchecked")
    public MinionOld deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
        JsonObject obj = element.getAsJsonObject();
        UUID id       = UUID.fromString(obj.get("internal").getAsString());
        UUID owner    = UUID.fromString(obj.get("owner").getAsString());
        String minionId = obj.get("id").getAsString();
        int level     = obj.get("level").getAsInt();
        double speed  = obj.get("timeBetweenAction").getAsDouble();
        int radius    = obj.get("productionRadius").getAsInt();
        int maxStorage= obj.get("maxStorage").getAsInt();
        int maxSlots  = obj.get("maxSlots").getAsInt();
        String skin   = obj.get("skin").getAsString();
        double resource = obj.get("resourceGenerated").getAsDouble();

        JsonObject posObj = obj.get("position").getAsJsonObject();
        Map<String, Object> position = new HashMap<>();
        position.put("world", posObj.get("world").getAsString());
        position.put("x", posObj.get("x").getAsDouble());
        position.put("y", posObj.get("y").getAsDouble());
        position.put("z", posObj.get("z").getAsDouble());

        JsonArray storageArr = obj.getAsJsonArray("storage");
        List<ItemStack> storage = new ArrayList<>();
        for (JsonElement entry : storageArr) {
            storage.add(deserializeItemStack(entry.getAsString()));
        }

        boolean lastPositionCorrectly = true;
        JsonElement posFlag = obj.get("isLastPositionCorrectly");
        if (posFlag != null) lastPositionCorrectly = posFlag.getAsBoolean();

        Map<String, List<ModifierDataOld>> modifiers;
        try {
            modifiers = ctx.deserialize(obj.get("modifiers"),
                    new TypeToken<Map<String, List<ModifierDataOld>>>() {}.getType());
        } catch (JsonParseException e) {
            Map<String, List<String>> rawMods = ctx.deserialize(obj.get("modifiers"),
                    new TypeToken<Map<String, List<String>>>() {}.getType());
            Map<String, List<ModifierDataOld>> converted = new HashMap<>();
            rawMods.forEach((key, ids) -> {
                List<ModifierDataOld> list = new ArrayList<>();
                for (String modId : ids) list.add(new ModifierDataOld(modId, -1L));
                converted.put(key, list);
            });
            modifiers = converted;
        }

        Map<String, Integer> unlockMinions = new HashMap<>();
        JsonElement unlockElem = obj.get("unlockMinions");
        if (unlockElem != null && unlockElem.isJsonObject()) {
            unlockMinions = ctx.deserialize(unlockElem, new TypeToken<Map<String, Integer>>() {}.getType());
        }

        return new MinionOld(id, owner, minionId, level, speed, radius, maxStorage, maxSlots,
                resource, position, storage, modifiers, skin, lastPositionCorrectly, unlockMinions);
    }

    public static ItemStack deserializeItemStack(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream bois = new BukkitObjectInputStream(bis);
            ItemStack stack = (ItemStack) bois.readObject();
            bois.close();
            return stack;
        } catch (ClassNotFoundException | IOException e) {
            throw new JsonParseException("Unable to deserialize ItemStack", e);
        }
    }
}
