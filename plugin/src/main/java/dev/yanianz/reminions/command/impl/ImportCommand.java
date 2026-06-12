package dev.yanianz.reminions.command.impl;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.managers.importer.DbBatchImporter;
import dev.yanianz.reminions.managers.importer.MinionBatchImporter;
import dev.yanianz.reminions.managers.importer.ModifierBatchImporter;
import dev.yanianz.reminions.managers.importer.SkinBatchImporter;
import org.bukkit.command.CommandSender;

public class ImportCommand extends CommandHolder {

    public ImportCommand() {
        super("import", "minions.command.importer", 1, "<type>");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        String importType = args[0];
        Runnable task = () -> {
            try {
                switch (importType.toLowerCase()) {
                    case "minions" -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            File srcDir  = new File(plugin.getDataFolder(), "import/minions");
                            File destDir = new File(plugin.getDataFolder(), "minions");
                            new MinionBatchImporter(srcDir, destDir).importAll();
                            plugin.getConfig0().sendMessage(sender, "import_minions_success");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    case "skins" -> {
                        File srcDir  = new File(plugin.getDataFolder(), "import/skins");
                        File destDir = new File(plugin.getDataFolder(), "skins");
                        new SkinBatchImporter(srcDir, destDir).importAll();
                        plugin.getConfig0().sendMessage(sender, "import_skins_success");
                    }
                    case "modifiers" -> {
                        File srcDir  = new File(plugin.getDataFolder(), "import/modifiers");
                        File destDir = new File(plugin.getDataFolder(), "modifiers");
                        new ModifierBatchImporter(srcDir, destDir).importAll();
                        plugin.getConfig0().sendMessage(sender, "import_modifiers_success");
                    }
                    case "players" -> {
                        File srcDir = new File(plugin.getDataFolder(), "import/players");
                        new DbBatchImporter(srcDir, plugin.getMinionManager(), plugin.getDatabase()).importAll();
                        plugin.getConfig0().sendMessage(sender, "import_players_success");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        // Minion import must run on the main thread; all others can be async.
        if (importType.equalsIgnoreCase("minions")) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        if (args.length != 1) return List.of();
        return Stream.of("minions", "modifiers", "skins", "players")
                .filter(s -> s.startsWith(args[0]))
                .toList();
    }
}
