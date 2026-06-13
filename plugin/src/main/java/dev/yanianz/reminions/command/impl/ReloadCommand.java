package dev.yanianz.reminions.command.impl;

import java.util.List;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.config.StorageConfig;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.RecipeManager;
import dev.yanianz.reminions.managers.SkinManager;
import dev.yanianz.reminions.managers.StorageManager;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends CommandHolder {

    public ReloadCommand() {
        super("reload", "minions.command.reload", 1, "<type>");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        switch (args[0].toLowerCase()) {
            case "all" -> {
                RecipeManager.clearRecipes();
                plugin.getStorageManager().loadAll();
                reloadMinions(plugin);
                reloadConfig(plugin);
                reloadSkins(plugin);
                plugin.getModifierManager().loadAll();
                plugin.getMenuManager().loadAll();
                NMSHandlerProvider.getInventoryBridge().closeInventoryPlayers();
                plugin.getConfig0().sendMessage(sender, "reload_all_success");
            }
            case "minions" -> {
                RecipeManager.clearRecipes();
                reloadMinions(plugin);
                plugin.getConfig0().sendMessage(sender, "reload_minions_success");
            }
            case "config" -> {
                reloadConfig(plugin);
                plugin.getConfig0().sendMessage(sender, "reload_config_success");
            }
            case "skins" -> {
                reloadSkins(plugin);
                plugin.getConfig0().sendMessage(sender, "reload_skins_success");
            }
            case "modifiers" -> {
                plugin.getModifierManager().loadAll();
                plugin.getConfig0().sendMessage(sender, "reload_modifiers_success");
            }
            case "storages" -> {
                plugin.getStorageManager().loadAll();
                plugin.getConfig0().sendMessage(sender, "reload_storages_success");
                reloadMinions(plugin);
            }
            case "menus" -> {
                plugin.getMenuManager().loadAll();
                NMSHandlerProvider.getInventoryBridge().closeInventoryPlayers();
            }
            default -> plugin.getConfig0().sendMessage(sender, "reload_invalid_type", "%type%", args[0]);
        }
    }

    private void reloadMinions(ReMinions plugin) {
        MinionManager minionManager   = plugin.getMinionManager();
        StorageManager storageManager = plugin.getStorageManager();
        minionManager.loadAll();
        plugin.getPlayerManager().getAllMinions().forEach(minion -> {
            MinionConfig config = minionManager.get(minion.getName());
            if (config == null) return;
            minion.setMinionConfig(config);
            MinionUpgrade upgrade = config.getUpgrade(minion.getLevel());
            if (upgrade == null) return;
            minion.setCacheProductionSpeed(upgrade.productionSpeed());
            minion.getInventory().setMaxSlots(upgrade.maxStorage());
            MinionStorage storage = minion.getStorage();
            if (storage == null) return;
            StorageConfig storageConfig = storageManager.get(storage.name());
            if (storageConfig != null) storage.inventory().setMaxSlots(storageConfig.maxStorage());
        });
    }

    private void reloadConfig(ReMinions plugin) {
        plugin.getConfig0().reload();
        DebugLogger.setEnabled(plugin.getConfig0().getBoolean("settings.debug_enabled"));
        ReMinions.setRecipesEnabled(plugin.getConfig0().getBoolean("settings.minions_recipes"));
        if (plugin.getWorthService() != null) {
            plugin.getWorthService().reload(plugin.getConfig0());
        }
        if (plugin.getBoosterService() != null) {
            plugin.getBoosterService().reload(plugin.getConfig0());
        }
    }

    private void reloadSkins(ReMinions plugin) {
        SkinManager skinManager = plugin.getSkinManager();
        skinManager.loadAll();
        plugin.getPlayerManager().getAllMinions().forEach(minion -> {
            minion.despawn();
            MinionSkinConfig skin = skinManager.get(minion.getCurrentSkin());
            if (skin != null) minion.spawn(skin);
        });
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        if (args.length != 1) return List.of();
        return Stream.of("all","minions","config","skins","modifiers","storages","menus")
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
    }
}
