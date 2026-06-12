package dev.yanianz.v1_21_1;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

public class NMSHandlerImpl_1_21_1 implements NMSHandler {
   @Override
   public void sendGhostRecipe(PlayerRecipeBookClickEvent var1, Config var2, MinionManager var3, Recipe var4, ItemStack var5) {
      Player var6 = var1.getPlayer();
      ShapedRecipe var7 = (ShapedRecipe)var4;
      HashMap var8 = new HashMap();
      String[] var9 = var7.getShape();
      Map var10 = var7.getChoiceMap();
      String var11 = var2.getTag("recipe_book_display_amount_required");

      for (String var15 : var9) {
         for (char var19 : var15.toCharArray()) {
            RecipeChoice var20 = (RecipeChoice)var10.get(var19);
            if (var20 == null) {
               var8.put(var19, Ingredient.EMPTY);
            } else {
               ItemStack var21 = var20.getItemStack();
               if (var21 != null && !var21.getType().isAir()) {
                  if (var21.getType() == Material.PLAYER_HEAD) {
                     String[] var22 = var1.getRecipe().getKey().split("_lvl");
                     String var23 = var22[0];
                     int var24 = Integer.parseInt(var22[1]);
                     MinionConfig var25 = var3.get(var23);
                     if (var25 == null) {
                        var8.put(var19, Ingredient.EMPTY);
                     } else {
                        MinionUpgrade var26 = var25.getUpgrade(Math.max(1, var24 - 1));
                        if (var26 == null) {
                           var8.put(var19, Ingredient.EMPTY);
                        } else {
                           ItemStack var27 = var25.getMinionHead(Math.max(1, var24 - 1), 0L, var26.maxStorage(), var26.productionSpeed());
                           var8.put(var19, Ingredient.of(new net.minecraft.world.item.ItemStack[]{CraftItemStack.asNMSCopy(var27)}));
                        }
                     }
                  } else {
                     ItemStack var33 = var21.clone();
                     ItemMeta var34 = var33.getItemMeta();
                     List<net.kyori.adventure.text.Component> var35 = var34.hasLore() ? var34.lore() : new ArrayList<>();
                     var35.add(Text.parseComponent(var11.replace("%x_amount%", String.valueOf(var33.getAmount()))));
                     var34.lore(var35);
                     var33.setItemMeta(var34);
                     net.minecraft.world.item.ItemStack var36 = CraftItemStack.asNMSCopy(var33);
                     var8.put(var19, Ingredient.of(new net.minecraft.world.item.ItemStack[]{var36}));
                  }
               } else {
                  var8.put(var19, Ingredient.EMPTY);
               }
            }
         }
      }

      net.minecraft.world.item.ItemStack var28 = CraftItemStack.asNMSCopy(var5);
      ShapedRecipePattern var29 = ShapedRecipePattern.of(var8, var9);
      RecipeHolder var30 = new RecipeHolder(
         CraftNamespacedKey.toMinecraft(((ShapedRecipe)var4).getKey()),
         new net.minecraft.world.item.crafting.ShapedRecipe(
            ((ShapedRecipe)var4).getGroup(), CraftRecipe.getCategory(((ShapedRecipe)var4).getCategory()), var29, var28
         )
      );
      ServerPlayer var31 = ((CraftPlayer)var6).getHandle();
      int var32 = var31.containerMenu.containerId;
      var31.connection.send(new ClientboundPlaceGhostRecipePacket(var32, var30));
   }

   @Override
   public void updateInventoryTitle(InventoryView var1, String var2) {
      CraftInventoryView.sendInventoryTitleChange(var1, var2);
   }
}
