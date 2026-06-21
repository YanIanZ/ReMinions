package dev.yanianz.v1_21_5;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.nms.NMSHandler;
import dev.yanianz.reminions.utils.Text;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
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

/** Real NMS adapter for Paper 1.21.5. Uses post-1.21.2 SlotDisplay system for ghost recipes. */
public final class NMSHandlerImpl_1_21_5 implements NMSHandler {

    @Override
    public void sendGhostRecipe(PlayerRecipeBookClickEvent event, Config config, MinionManager minionManager,
                                Recipe recipe, ItemStack resultItem) {
        Player player = event.getPlayer();
        ShapedRecipe shaped = (ShapedRecipe) recipe;
        List<SlotDisplay> slots = new ArrayList<>();
        String[] shape = shaped.getShape();
        Map<Character, RecipeChoice> choiceMap = shaped.getChoiceMap();
        String amountTag = config.getTag("recipe_book_display_amount_required");

        for (String row : shape) {
            for (char symbol : row.toCharArray()) {
                slots.add(this.buildSlotDisplay(event, minionManager, choiceMap.get(symbol), amountTag));
            }
        }

        net.minecraft.world.item.ItemStack nmsResult = CraftItemStack.asNMSCopy(shaped.getResult());
        ShapedCraftingRecipeDisplay display = new ShapedCraftingRecipeDisplay(
                shape[0].length(), shape.length, slots,
                new SlotDisplay.ItemStackSlotDisplay(nmsResult),
                new SlotDisplay.ItemStackSlotDisplay(new net.minecraft.world.item.ItemStack(Items.CRAFTING_TABLE))
        );
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        int containerId = serverPlayer.containerMenu.containerId;
        serverPlayer.connection.send(new ClientboundPlaceGhostRecipePacket(containerId, display));
    }

    private SlotDisplay buildSlotDisplay(PlayerRecipeBookClickEvent event, MinionManager minionManager,
                                         RecipeChoice choice, String amountTag) {
        if (choice == null) return SlotDisplay.Empty.INSTANCE;
        ItemStack item = choice.getItemStack();
        if (item == null || item.getType().isAir()) return SlotDisplay.Empty.INSTANCE;

        if (item.getType() == Material.PLAYER_HEAD) {
            String[] keyParts = event.getRecipe().getKey().split("_lvl");
            if (keyParts.length < 2) return SlotDisplay.Empty.INSTANCE;
            MinionConfig minionConfig = minionManager.get(keyParts[0]);
            if (minionConfig == null) return SlotDisplay.Empty.INSTANCE;
            int targetLevel = Math.max(1, Integer.parseInt(keyParts[1]) - 1);
            MinionUpgrade upgrade = minionConfig.getUpgrade(targetLevel);
            if (upgrade == null) return SlotDisplay.Empty.INSTANCE;
            ItemStack head = minionConfig.getMinionHead(targetLevel, 0L, upgrade.maxStorage(), upgrade.productionSpeed());
            return new SlotDisplay.ItemStackSlotDisplay(CraftItemStack.asNMSCopy(head));
        }

        ItemStack annotated = item.clone();
        ItemMeta meta = annotated.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        lore.add(Text.parseComponent(amountTag.replace("%x_amount%", String.valueOf(annotated.getAmount()))));
        meta.lore(lore);
        annotated.setItemMeta(meta);
        return new SlotDisplay.ItemStackSlotDisplay(CraftItemStack.asNMSCopy(annotated));
    }

    @Override
    public void updateInventoryTitle(InventoryView inventoryView, String newTitle) {
        CraftInventoryView.sendInventoryTitleChange(inventoryView, newTitle);
    }
}
