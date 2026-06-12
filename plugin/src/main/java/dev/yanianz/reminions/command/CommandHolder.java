package dev.yanianz.reminions.command;

import java.util.List;
import dev.yanianz.reminions.ReMinions;
import org.bukkit.command.CommandSender;

public abstract class CommandHolder {
    private final String name;
    private final String permission;
    private final int length;
    private final String usage;

    public CommandHolder(String name, String permission, int length, String usage) {
        this.name = name;
        this.permission = permission;
        this.length = length;
        this.usage = usage;
    }

    public abstract void onExecute(CommandSender sender, String[] args, ReMinions plugin);
    public abstract List<String> onTab(CommandSender sender, String[] args, ReMinions plugin);

    public boolean hasPermission(CommandSender sender) {
        return this.permission == null || sender.hasPermission(this.permission) || sender.isOp();
    }

    public String getUsage() {
        return String.format("/minions %s %s", this.name, this.usage);
    }

    public boolean isValidLength(String[] args) {
        return args.length == this.length;
    }

    public String getName()       { return this.name; }
    public String getPermission() { return this.permission; }
    public int getLength()        { return this.length; }
}
