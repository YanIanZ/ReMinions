package dev.yanianz.reminions.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

public class CommandManager implements TabExecutor {
    private final Map<String, CommandHolder> commands = new HashMap<>();
    private final ReMinions plugin;

    public CommandManager(ReMinions plugin) {
        this.plugin = plugin;
    }

    public void register(CommandHolder command) {
        this.commands.put(command.getName().toLowerCase(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            this.plugin.getConfig0().getStringList("messages.command_help")
                    .forEach(line -> sender.sendMessage(Text.parseComponent(line)));
            return true;
        }

        Config config = this.plugin.getConfig0();
        CommandHolder handler = this.commands.get(args[0].toLowerCase());
        if (handler == null) {
            config.sendMessage(sender, "command_not_exists", "%command.name%", args[0]);
            return true;
        }
        if (!handler.hasPermission(sender)) {
            config.sendMessage(sender, "command_not_has_permission");
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        if (!handler.isValidLength(subArgs)) {
            config.sendMessage(sender, "command_not_equals_length", "%command.usage%", handler.getUsage());
            return true;
        }

        try {
            handler.onExecute(sender, subArgs, this.plugin);
        } catch (Exception e) {
            DebugLogger.warn("An error occurred while executing the command.");
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return this.commands.keySet().stream()
                    .filter(name -> name.startsWith(args[0])).sorted().toList();
        }
        CommandHolder handler = this.commands.get(args[0]);
        return handler == null ? Collections.emptyList()
                : handler.onTab(sender, Arrays.copyOfRange(args, 1, args.length), this.plugin);
    }
}
