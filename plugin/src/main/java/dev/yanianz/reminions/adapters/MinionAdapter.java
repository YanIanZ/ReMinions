package dev.yanianz.reminions.adapters;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.StorageConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.minion.MinionStatus;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.minion.MinionType;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.utils.Location3f;

public class MinionAdapter implements JsonSerializer<Minion>, JsonDeserializer<Minion> {
    private final ReMinions plugin;

    public MinionAdapter(ReMinions plugin) {
        this.plugin = plugin;
    }

    @Override
    public JsonElement serialize(Minion minion, Type type, JsonSerializationContext ctx) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", minion.getId().toString());
        obj.addProperty("name", minion.getName());
        obj.addProperty("owner", minion.getOwner().toString());
        obj.addProperty("type", minion.getType().name());
        obj.add("inventory", ctx.serialize(minion.getInventory()));
        obj.add("loc", ctx.serialize(minion.getLoc(), Location3f.class));
        obj.addProperty("level", minion.getLevel());
        obj.addProperty("status", minion.getStatus() == null ? MinionStatus.IDLE.name() : minion.getStatus().name());
        obj.add("skin", minion.getSkin() == null ? JsonNull.INSTANCE : new JsonPrimitive(minion.getSkin()));
        obj.addProperty("skinLevel", minion.getSkinLevel());
        obj.addProperty("lastGenerated", minion.getLastGenerated());
        obj.addProperty("lastConnection", System.currentTimeMillis());
        obj.add("productionRemainders", ctx.serialize(minion.getProductionRemainders(),
                new TypeToken<Map<String, Double>>() {}.getType()));
        obj.add("storage", minion.getStorage() == null
                ? JsonNull.INSTANCE : ctx.serialize(minion.getStorage(), MinionStorage.class));
        obj.add("modifiers", ctx.serialize(minion.getModifiers(),
                new TypeToken<List<MinionModifierData>>() {}.getType()));
        return obj;
    }

    @Override
    public Minion deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
        JsonObject obj = element.getAsJsonObject();
        UUID id = UUID.fromString(obj.get("id").getAsString());
        String name = obj.get("name").getAsString();
        MinionConfig minionConfig = this.plugin.getMinionManager().get(name);
        if (minionConfig == null) return null;

        UUID owner = UUID.fromString(obj.get("owner").getAsString());
        int level = obj.get("level").getAsInt();
        MinionType minionType = MinionType.valueOf(obj.get("type").getAsString());
        MinionUpgrade upgrade = minionConfig.getUpgrade(level);
        if (upgrade == null) return null;

        MinionInventory inventory = ctx.deserialize(obj.get("inventory"), MinionInventory.class);
        inventory.setMaxSlots(upgrade.maxStorage());

        Location3f loc = ctx.deserialize(obj.getAsJsonObject("loc"), Location3f.class);
        Minion minion = new Minion(id, name, owner, minionType, inventory, minionConfig, loc);
        minion.setLevel(level);
        minion.setStatus(MinionStatus.valueOf(obj.get("status").getAsString()));
        minion.setCacheProductionSpeed(upgrade.productionSpeed());
        minion.setSkin(obj.has("skin") && !obj.get("skin").isJsonNull() ? obj.get("skin").getAsString() : null);
        minion.setSkinLevel(obj.get("skinLevel").getAsString());

        long lastConnection = obj.has("lastConnection") ? obj.get("lastConnection").getAsLong() : System.currentTimeMillis();
        long lastGenerated  = obj.has("lastGenerated")  ? obj.get("lastGenerated").getAsLong()  : lastConnection;
        if (lastGenerated <= 0L) {
            lastGenerated = lastConnection > 0L ? lastConnection : System.currentTimeMillis();
        }
        minion.setLastGenerated(lastGenerated);
        minion.setLastConnection(lastConnection);

        if (obj.has("productionRemainders") && !obj.get("productionRemainders").isJsonNull()) {
            Map<String, Double> remainders = ctx.deserialize(obj.get("productionRemainders"),
                    new TypeToken<Map<String, Double>>() {}.getType());
            if (remainders != null) minion.getProductionRemainders().putAll(remainders);
        }

        MinionStorage storage = null;
        if (obj.has("storage") && !obj.get("storage").isJsonNull()) {
            storage = ctx.deserialize(obj.get("storage"), MinionStorage.class);
            StorageConfig storageConfig = this.plugin.getStorageManager().get(storage.name());
            if (storageConfig != null) storage.inventory().setMaxSlots(storageConfig.maxStorage());
        }
        minion.setStorage(storage);

        List<MinionModifierData> modifiers = ctx.deserialize(obj.get("modifiers"),
                new TypeToken<List<MinionModifierData>>() {}.getType());
        minion.getModifiers().addAll(modifiers);
        return minion;
    }
}
