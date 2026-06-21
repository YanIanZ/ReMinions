package dev.yanianz.v26_1_2;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.nms.NMSHandler;
import dev.yanianz.reminions.utils.Text;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay.Empty;
import net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NMSHandlerImpl_26_1_2 implements NMSHandler {
    @Override
    public void sendGhostRecipe(PlayerRecipeBookClickEvent event, Config config, MinionManager minionManager,
                                Recipe recipe, ItemStack resultItem) {
        Player player = event.getPlayer();
        ShapedRecipe shaped = (ShapedRecipe) recipe;
        ArrayList<net.minecraft.world.item.crafting.display.SlotDisplay> slots = new ArrayList<>();
        String[] shape = shaped.getShape();
        Map<Character, RecipeChoice> choiceMap = shaped.getChoiceMap();
        String amountTag = config.getTag("recipe_book_display_amount_required");

        for (String row : shape) {
            for (char c : row.toCharArray()) {
                RecipeChoice choice = choiceMap.get(c);
                if (choice == null) {
                    slots.add(Empty.INSTANCE);
                    continue;
                }
                ItemStack ingredient = choice.getItemStack();
                if (ingredient == null || ingredient.getType().isAir()) {
                    slots.add(Empty.INSTANCE);
                    continue;
                }
                if (ingredient.getType() == Material.PLAYER_HEAD) {
                    String[] parts = event.getRecipe().getKey().split("_lvl");
                    String minionId = parts[0];
                    int level = Integer.parseInt(parts[1]);
                    MinionConfig minionConfig = minionManager.get(minionId);
                    if (minionConfig == null) {
                        slots.add(Empty.INSTANCE);
                        continue;
                    }
                    MinionUpgrade upgrade = minionConfig.getUpgrade(Math.max(1, level - 1));
                    if (upgrade == null) {
                        slots.add(Empty.INSTANCE);
                        continue;
                    }
                    ItemStack head = minionConfig.getMinionHead(Math.max(1, level - 1), 0L,
                            upgrade.maxStorage(), upgrade.productionSpeed());
                    slots.add(new ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(CraftItemStack.asNMSCopy(head))));
                } else {
                    ItemStack copy = ingredient.clone();
                    ItemMeta meta = copy.getItemMeta();
                    List<net.kyori.adventure.text.Component> lore =
                            meta.hasLore() ? meta.lore() : new ArrayList<>();
                    lore.add(Text.parseComponent(amountTag.replace("%x_amount%", String.valueOf(copy.getAmount()))));
                    meta.lore(lore);
                    copy.setItemMeta(meta);
                    slots.add(new ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(CraftItemStack.asNMSCopy(copy))));
                }
            }
        }

        ItemStackSlotDisplay result = new ItemStackSlotDisplay(
                ItemStackTemplate.fromNonEmptyStack(CraftItemStack.asNMSCopy(shaped.getResult())));
        ShapedCraftingRecipeDisplay display = new ShapedCraftingRecipeDisplay(
                shape[0].length(),
                shape.length,
                slots,
                result,
                new ItemStackSlotDisplay(new ItemStackTemplate(Items.CRAFTING_TABLE))
        );
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        int containerId = serverPlayer.containerMenu.containerId;
        serverPlayer.connection.send(new ClientboundPlaceGhostRecipePacket(containerId, display));
    }

    @Override
    public void updateInventoryTitle(InventoryView inventoryView, String newTitle) {
        CraftInventoryView.sendInventoryTitleChange(inventoryView, newTitle);
    }
}
