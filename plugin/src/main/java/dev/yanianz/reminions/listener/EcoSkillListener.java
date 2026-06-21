package dev.yanianz.reminions.listener;

import com.willfp.ecoskills.api.EcoSkillsAPI;
import com.willfp.ecoskills.skills.Skill;
import com.willfp.ecoskills.skills.Skills;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.api.events.MinionItemsProduceEvent;
import dev.yanianz.reminions.api.events.MinionItemsRemoveEvent;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listener for the EcoSkills plugin integration. Unlike legacy skill systems, EcoSkills
 * needs an online {@link Player} reference (not just a UUID) to award XP, so this listener
 * skips minions whose owner is offline.
 */
public class EcoSkillListener implements Listener {

    private final MinionManager minionManager;
    private final ModifierManager modifierManager;
    private final Config config;

    public EcoSkillListener(MinionManager minionManager, ModifierManager modifierManager, Config config) {
        this.minionManager = minionManager;
        this.modifierManager = modifierManager;
        this.config = config;
    }

    @EventHandler
    public void onMinionItemsProduce(MinionItemsProduceEvent event) {
        if (!this.config.getBoolean("settings.products_add_exp_produce")) return;
        if (event.getResult() != MinionItemsProduceEvent.ResultState.SUCESS) return;
        Player owner = Bukkit.getPlayer(event.getMinion().getOwner());
        if (owner == null) return;
        this.processExpGain(owner, event.getMinion(), event.getItems());
    }

    @EventHandler
    public void onMinionItemRemove(MinionItemsRemoveEvent event) {
        if (!this.config.getBoolean("settings.products_add_exp_take_item")) return;
        if (event.getResult() != MinionItemsRemoveEvent.ResultState.SUCESS) return;
        Player owner = Bukkit.getPlayer(event.getMinion().getOwner());
        if (owner == null) return;
        this.processExpGain(owner, event.getMinion(), event.getItems());
    }

    private void processExpGain(Player owner, Minion minion, MinionInventory.ItemData[] producedItems) {
        MinionConfig config = this.minionManager.get(minion.getName());
        if (config == null) return;
        List<Product> products = this.getAllProducts(minion, config);

        // EXP_BOOST modifier (item-based) + external booster plugin multiplier (BoosterService).
        double modBoost = 1.0 + this.modifierManager.getModifierNumber(minion, ModifierType.EXP_BOOST);
        double extBoost = ReMinions.getPlugin().getBoosterService()
                .multiplier(owner.getUniqueId(), BoostKind.EXP);
        double expMultiplier = modBoost * extBoost;

        for (MinionInventory.ItemData item : producedItems) {
            products.stream()
                    .filter(p -> p.matches(item.getItem()) && p.getExpConfig() != null)
                    .findFirst()
                    .ifPresent(p -> {
                        SourceExpConfig expConfig = p.getExpConfig();
                        Skill skill = (Skill) Skills.INSTANCE.getByID(expConfig.skillId());
                        if (skill != null) {
                            EcoSkillsAPI.giveSkillXP(owner, skill, expConfig.exp() * item.getAmount() * expMultiplier);
                        }
                    });
        }
    }

    private List<Product> getAllProducts(Minion minion, MinionConfig config) {
        return Stream.concat(
                config.products().stream(),
                minion.getModifiersByType(ModifierType.ITEM_UPGRADES).stream()
                        .map(d -> this.modifierManager.get(d.getName()))
                        .filter(Objects::nonNull)
                        .flatMap(cfg -> cfg.upgradeProducts().stream())
        ).toList();
    }
}
