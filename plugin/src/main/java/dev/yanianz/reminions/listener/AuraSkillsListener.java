package dev.yanianz.reminions.listener;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.api.events.MinionItemsProduceEvent;
import dev.yanianz.reminions.api.events.MinionItemsRemoveEvent;
import dev.yanianz.reminions.api.events.MinionSellItemsEvent;
import dev.yanianz.reminions.booster.BoostKind;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Reflective bridge to <a href="https://wiki.aurelium.dev/auraskills">AuraSkills</a> — grants
 * skill XP whenever the minion produces items, the player collects items from a minion, or the
 * minion auto-sells.
 *
 * <p>Uses {@link MethodHandle}s resolved at construction time so no AuraSkills classes need to
 * exist on the compile classpath. If any reflective lookup fails the listener becomes a no-op
 * (the {@link #usable} flag is {@code false}).</p>
 *
 * <p>Per-product XP comes from the same {@code source_exp} block the existing EcoSkills hook
 * reads, so a single minion yml drives both integrations.</p>
 */
public class AuraSkillsListener implements Listener {

    private static final String AURASKILLS_BUKKIT_CLASS = "dev.aurelium.auraskills.api.AuraSkillsBukkit";
    private static final String AURASKILLS_API_CLASS    = "dev.aurelium.auraskills.api.AuraSkillsApi";

    private final MinionManager minionManager;
    private final ModifierManager modifierManager;
    private final Config config;

    /** Resolved {@code AuraSkillsApi} singleton. */
    private final Object api;
    /** {@code api.getUser(UUID)}. */
    private final MethodHandle getUserMh;
    /** {@code user.addSkillXp(Skill, double)} OR fallback {@code addXp(NamespacedKey, double)}. */
    private final MethodHandle addXpMh;
    /** {@code Skills.<NAME>()} resolver via {@code NamespacedKey} (auraskills:NAME). */
    private final MethodHandle skillFromKeyMh;

    private final boolean usable;

    public AuraSkillsListener(MinionManager minionManager, ModifierManager modifierManager, Config config) {
        this.minionManager = minionManager;
        this.modifierManager = modifierManager;
        this.config = config;

        Object resolvedApi = null;
        MethodHandle getUser = null;
        MethodHandle addXp = null;
        MethodHandle skillFromKey = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            // AuraSkillsBukkit.get() returns the platform handle exposing #getApi().
            Class<?> bukkitCls = Class.forName(AURASKILLS_BUKKIT_CLASS);
            Object bukkit = bukkitCls.getMethod("get").invoke(null);
            resolvedApi = bukkitCls.getMethod("getApi").invoke(bukkit);

            Class<?> apiCls = Class.forName(AURASKILLS_API_CLASS);
            Class<?> userCls = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
            Class<?> skillCls = Class.forName("dev.aurelium.auraskills.api.skill.Skill");

            getUser = lookup.findVirtual(apiCls, "getUser",
                    MethodType.methodType(userCls, java.util.UUID.class));
            addXp = lookup.findVirtual(userCls, "addSkillXp",
                    MethodType.methodType(void.class, skillCls, double.class));

            // Skills registry: AuraSkillsApi#getGlobalRegistry().getSkill(NamespacedKey)
            Class<?> registryCls = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedRegistry");
            Object registry = apiCls.getMethod("getGlobalRegistry").invoke(resolvedApi);
            MethodHandle getSkill = lookup.findVirtual(registryCls, "getSkill",
                    MethodType.methodType(skillCls, NamespacedKey.class));
            skillFromKey = getSkill.bindTo(registry);
        } catch (Throwable failure) {
            DebugLogger.warn("AuraSkills API resolution failed; XP integration disabled: " + failure.getMessage());
        }

        this.api = resolvedApi;
        this.getUserMh = getUser;
        this.addXpMh = addXp;
        this.skillFromKeyMh = skillFromKey;
        this.usable = resolvedApi != null && getUser != null && addXp != null && skillFromKey != null;
    }

    public boolean isUsable() {
        return this.usable;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event hooks
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onMinionItemsProduce(MinionItemsProduceEvent event) {
        if (!this.usable) return;
        if (!this.config.getBoolean("settings.products_add_exp_produce")) return;
        if (event.getResult() != MinionItemsProduceEvent.ResultState.SUCESS) return;
        Player owner = Bukkit.getPlayer(event.getMinion().getOwner());
        if (owner == null) return;
        this.grantXp(owner, event.getMinion(), event.getItems());
    }

    @EventHandler
    public void onMinionItemRemove(MinionItemsRemoveEvent event) {
        if (!this.usable) return;
        if (!this.config.getBoolean("settings.products_add_exp_take_item")) return;
        if (event.getResult() != MinionItemsRemoveEvent.ResultState.SUCESS) return;
        Player owner = Bukkit.getPlayer(event.getMinion().getOwner());
        if (owner == null) return;
        this.grantXp(owner, event.getMinion(), event.getItems());
    }

    @EventHandler
    public void onMinionAutoSell(MinionSellItemsEvent event) {
        if (!this.usable) return;
        if (!this.config.getBoolean("settings.products_add_exp_auto_sell", true)) return;
        Player owner = Bukkit.getPlayer(event.getMinion().getOwner());
        if (owner == null) return;
        Product sold = event.getItemSell();
        if (sold == null || sold.getExpConfig() == null) return;
        double xp = sold.getExpConfig().exp() * event.getAmountSelling() * this.xpMultiplier(event.getMinion(), owner);
        this.giveXp(owner, sold.getExpConfig().skillId(), xp);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — match item ↔ product, then call MethodHandles
    // ─────────────────────────────────────────────────────────────────────────

    private void grantXp(Player owner, Minion minion, MinionInventory.ItemData[] producedItems) {
        MinionConfig minionCfg = this.minionManager.get(minion.getName());
        if (minionCfg == null) return;
        List<Product> products = this.allProducts(minion, minionCfg);
        double multiplier = this.xpMultiplier(minion, owner);

        for (MinionInventory.ItemData item : producedItems) {
            products.stream()
                    .filter(p -> p.matches(item.getItem()) && p.getExpConfig() != null)
                    .findFirst()
                    .ifPresent(p -> {
                        SourceExpConfig exp = p.getExpConfig();
                        double total = exp.exp() * item.getAmount() * multiplier;
                        this.giveXp(owner, exp.skillId(), total);
                    });
        }
    }

    private double xpMultiplier(Minion minion, Player owner) {
        double modBoost = 1.0 + this.modifierManager.getModifierNumber(minion, ModifierType.EXP_BOOST);
        double extBoost = ReMinions.getPlugin().getBoosterService()
                .multiplier(owner.getUniqueId(), BoostKind.EXP);
        return modBoost * extBoost;
    }

    private List<Product> allProducts(Minion minion, MinionConfig minionCfg) {
        return Stream.concat(
                minionCfg.products().stream(),
                minion.getModifiersByType(ModifierType.ITEM_UPGRADES).stream()
                        .map(d -> this.modifierManager.get(d.getName()))
                        .filter(Objects::nonNull)
                        .flatMap(cfg -> cfg.upgradeProducts().stream())
        ).toList();
    }

    private void giveXp(Player owner, String skillId, double amount) {
        if (skillId == null || amount <= 0.0) return;
        try {
            NamespacedKey key = skillId.contains(":")
                    ? NamespacedKey.fromString(skillId.toLowerCase())
                    : new NamespacedKey("auraskills", skillId.toLowerCase());
            if (key == null) return;
            Object skill = this.skillFromKeyMh.invoke(key);
            if (skill == null) return;
            Object user = this.getUserMh.invoke(this.api, owner.getUniqueId());
            if (user == null) return;
            this.addXpMh.invoke(user, skill, amount);
        } catch (Throwable failure) {
            DebugLogger.warn("AuraSkills XP grant failed for skill '" + skillId + "': " + failure.getMessage());
        }
    }
}
