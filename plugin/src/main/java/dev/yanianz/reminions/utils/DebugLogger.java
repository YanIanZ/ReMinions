package dev.yanianz.reminions.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

public class DebugLogger {
    private static final ConsoleCommandSender CONSOLE = Bukkit.getConsoleSender();
    private static boolean enabled = false;

    public static void debug(String message) {
        if (enabled) {
            CONSOLE.sendMessage(Text.parseComponent(
                    "#00FFFF🐞 #55FFFF[BeeMinions] #AAAAAA→ #00AAFF[Debug] #FFFFFF" + message));
        }
    }

    public static void warn(String message) {
        CONSOLE.sendMessage(Text.parseComponent(
                "#FFAA00⚠ #FFCC00[BeeMinions] #AAAAAA→ #FF9900[Warn] #FFFFFF" + message));
    }

    public static void error(String message) {
        CONSOLE.sendMessage(Text.parseComponent(
                "#FF5555❌ #FF6666[BeeMinions] #AAAAAA→ #FF0000[Error] #FFFFFF" + message));
    }

    public static void info(String message) {
        CONSOLE.sendMessage(Text.parseComponent(
                "#00FFFF✨ #55FFFF[BeeMinions] #AAAAAA→ #00FF00[Info] #FFFFFF" + message));
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
