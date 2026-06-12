package dev.yanianz.reminions.listener;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import dev.yanianz.reminions.api.events.MinionItemsProduceEvent;
import dev.yanianz.reminions.api.events.MinionItemsRemoveEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.ModifierManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Awards AuraSkills experience whenever a minion produces or removes an item that has an
 * {@code exp_source} entry configured on its product. Soft-dep only — registered iff
 * AuraSkills is loaded at startup.
 */
public class AuraSkillListener implements Listener {

    private final MinionManager minionManager;
    private final ModifierManager modifierManager;
    private final Config config;
    private final AuraSkillsApi auraSkills;

    public AuraSkillListener(MinionManager minionManager, ModifierManager modifierManager,
                             Config config, AuraSkillsApi auraSkills) {
        this.minionManager = minionManager;
        this.modifierManager = modifierManager;
        this.config = config;
        this.auraSkills = auraSkills;
    }

    @EventHandler
    public void onMinionItemsProduce(MinionItemsProduceEvent event) {
        if (!this.config.getBoolean("settings.products_add_exp_produce")) return;
        if (event.getResult() != MinionItemsProduceEvent.ResultState.SUCESS) return;
        this.processExpGain(event.getMinion(), event.getItems());
    }

    @EventHandler
    public void onMinionItemRemove(MinionItemsRemoveEvent event) {
        if (!this.config.getBoolean("settings.products_add_exp_take_item")) return;
        if (event.getResult() != MinionItemsRemoveEvent.ResultState.SUCESS) return;
        this.processExpGain(event.getMinion(), event.getItems());
    }

    private void processExpGain(Minion minion, MinionInventory.ItemData[] producedItems) {
        MinionConfig config = this.minionManager.get(minion.getName());
        if (config == null) return;
        SkillsUser skillsUser = this.auraSkills.getUser(minion.getOwner());
        if (skillsUser == null) return;
        List<Product> products = this.getAllProducts(minion, config);

        for (MinionInventory.ItemData item : producedItems) {
            products.stream()
                    .filter(p -> p.matches(item.getItem()) && p.getExpConfig() != null)
                    .findFirst()
                    .ifPresent(p -> this.grantExp(skillsUser, p.getExpConfig(), item.getAmount()));
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

    private void grantExp(SkillsUser user, SourceExpConfig expConfig, int itemAmount) {
        Skills skill = Skills.valueOf(expConfig.skillId());
        user.addSkillXp(skill, expConfig.exp() * itemAmount);
    }
}
