package dev.yanianz.reminions.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import dev.yanianz.reminions.utils.FileHandler;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

public class Config extends FileHandler {

    private static final int FILE_VERSION = 6;

    private final List<MinionRewardConfig> rewardConfigs = new ArrayList<>();

    public Config() {
        super("config.yml");
        this.load();
    }

    @Override
    public void load() {
        ConfigurationSection unlockedSection = this.getSectionOrThrow("minions_unlocked", "No found section 'minions_unlocked'");
        for (String key : unlockedSection.getKeys(false)) {
            ConfigurationSection entry = unlockedSection.getConfigurationSection(key);
            if (entry == null) continue;
            int requiredUnique = entry.getInt("required_unique_minions");
            int newMax         = entry.getInt("new_max_minions");
            this.rewardConfigs.add(new MinionRewardConfig(requiredUnique, newMax));
        }

        int savedVersion = this.getInt("file_version");
        if (FILE_VERSION > savedVersion) {
            applyMigrations(savedVersion);
            this.set("file_version", FILE_VERSION);
            this.save();
        }
    }

    private void applyMigrations(int fromVersion) {
        switch (fromVersion) {
            case 1 -> {
                this.set("database.type", "sqlite");
                this.set("database.host", "localhost");
                this.set("database.port", 3306);
                this.set("database.user", "root");
                this.set("database.password", "");
                this.set("database.database", "reminions");
                this.set("database.redis.host", "localhost");
                this.set("database.redis.port", 6379);
                this.set("database.redis.password", "");
            }
            case 2 -> this.set("tags.recipe_book_display_amount_required",
                    "#FFAA00⚠ &eRequired amount: &f%x_amount% &7📘");
            case 3 -> this.set("settings.minions_recipes", true);
            case 4 -> {
                this.set("messages.minion_deleted",
                        "#55FF55✔ &aThe minion &f%minion_name% &7(ID: &f%minion_id%&7) &ahas been successfully deleted! 🗑");
                this.set("messages.reset_unique_success_admin",
                        "#55FF55✔ &aYou have successfully reset the unique minions of &f%player%&a. &7(Max minions reset to &e%max_minions%&7) 🔄");
                this.set("messages.reset_unique_success_player",
                        "#FFCC00⚠ &eYour unique minions progress has been reset by an admin. &7(Max minions: &f%max_minions%&7) 🐝");
                this.set("messages.all_unique_minions_reset_success",
                        "#55FF55✔ &aAll players' unique minions have been reset successfully! &7(%count% players updated, default max: &e%default_max%&7) 🐝");
                this.set("messages.minion_place_limit_reached",
                        "#FF5555✖ &cYou've reached your limit of %limit% placed minions.");
            }
            case 5 -> this.set("settings.minions_offline_time_limit", 3);
        }
    }

    public void sendMessage(CommandSender sender, String messageKey, Object... placeholders) {
        String message = this.getString("messages." + messageKey);
        if (message != null && !message.isEmpty()) {
            sender.sendMessage(Text.parseComponent(message, placeholders));
        }
    }

    public MinionRewardConfig getMinionReward(int uniqueMinions) {
        return this.rewardConfigs.stream()
                .filter(r -> uniqueMinions == r.requiredUniqueMinions())
                .findFirst()
                .orElse(null);
    }

    public MinionRewardConfig getNextMinionReward(int uniqueMinions) {
        return this.rewardConfigs.stream()
                .filter(r -> r.requiredUniqueMinions() > uniqueMinions)
                .min(Comparator.comparingInt(MinionRewardConfig::requiredUniqueMinions))
                .orElse(null);
    }

    public String getTag(String tagKey) {
        return this.getString("tags." + tagKey);
    }
}
