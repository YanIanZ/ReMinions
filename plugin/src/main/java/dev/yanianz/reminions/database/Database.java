package dev.yanianz.reminions.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.adapters.ItemDataAdapter;
import dev.yanianz.reminions.adapters.ItemStackAdapter;
import dev.yanianz.reminions.adapters.Location3fAdapter;
import dev.yanianz.reminions.adapters.MinionAdapter;
import dev.yanianz.reminions.adapters.MinionInventoryAdapter;
import dev.yanianz.reminions.adapters.MinionStorageAdapter;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.utils.Location3f;
import org.bukkit.inventory.ItemStack;

public abstract class Database {
    protected static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Location3f.class, new Location3fAdapter())
            .registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
            .registerTypeAdapter(MinionInventory.class, new MinionInventoryAdapter())
            .registerTypeAdapter(MinionInventory.ItemData.class, new ItemDataAdapter())
            .registerTypeAdapter(Minion.class, new MinionAdapter(ReMinions.getPlugin()))
            .registerTypeAdapter(MinionStorage.class, new MinionStorageAdapter())
            .create();

    public abstract void connect(DatabaseMeta meta);
    public abstract void disconnect();
    public abstract void createTables();
    public abstract PlayerMinions getPlayerMinions(UUID playerId);
    public abstract List<PlayerMinions> getPlayerMinions();
    public abstract boolean savePlayersMinions(Collection<PlayerMinions> players);
    public abstract boolean savePlayerMinions(PlayerMinions player);
}
