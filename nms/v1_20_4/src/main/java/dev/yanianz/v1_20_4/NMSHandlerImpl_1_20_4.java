package dev.yanianz.v1_20_4;

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
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.PacketPlayOutAutoRecipe;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeItemStack;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapedRecipes;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftNamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

public class NMSHandlerImpl_1_20_4 implements NMSHandler {
   @Override
   public void sendGhostRecipe(PlayerRecipeBookClickEvent e, Config config, MinionManager minionManager, Recipe recipe, ItemStack resultItem) {
      Player var6 = e.getPlayer();
      ShapedRecipe var7 = (ShapedRecipe)recipe;
      HashMap var8 = new HashMap();
      String[] var9 = var7.getShape();
      Map var10 = var7.getChoiceMap();
      String var11 = config.getTag("recipe_book_display_amount_required");

      for (String var15 : var9) {
         for (char var19 : var15.toCharArray()) {
            RecipeChoice var20 = (RecipeChoice)var10.get(var19);
            if (var20 == null) {
               var8.put(var19, RecipeItemStack.a);
            } else {
               ItemStack var21 = var20.getItemStack();
               if (var21 != null && !var21.getType().isAir()) {
                  if (var21.getType() == Material.PLAYER_HEAD) {
                     String[] var22 = e.getRecipe().getKey().split("_lvl");
                     String var23 = var22[0];
                     int var24 = Integer.parseInt(var22[1]);
                     MinionConfig var25 = minionManager.get(var23);
                     if (var25 == null) {
                        var8.put(var19, RecipeItemStack.a);
                     } else {
                        MinionUpgrade var26 = var25.getUpgrade(Math.max(1, var24 - 1));
                        if (var26 == null) {
                           var8.put(var19, RecipeItemStack.a);
                        } else {
                           ItemStack var27 = var25.getMinionHead(Math.max(1, var24 - 1), 0L, var26.maxStorage(), var26.productionSpeed());
                           var8.put(var19, RecipeItemStack.a(new net.minecraft.world.item.ItemStack[]{CraftItemStack.asNMSCopy(var27)}));
                        }
                     }
                  } else {
                     ItemStack var38 = var21.clone();
                     ItemMeta var39 = var38.getItemMeta();
                     Object var40 = var39.hasLore() ? var39.lore() : new ArrayList();
                     var40.add(Text.parseComponent(var11.replace("%x_amount%", String.valueOf(var38.getAmount()))));
                     var39.lore((List)var40);
                     var38.setItemMeta(var39);
                     net.minecraft.world.item.ItemStack var41 = CraftItemStack.asNMSCopy(var38);
                     var8.put(var19, RecipeItemStack.a(new net.minecraft.world.item.ItemStack[]{var41}));
                  }
               } else {
                  var8.put(var19, RecipeItemStack.a);
               }
            }
         }
      }

      net.minecraft.world.item.ItemStack var28 = CraftItemStack.asNMSCopy(resultItem);
      int var29 = var9[0].length();
      NonNullList var30 = NonNullList.a(var9.length * var29, RecipeItemStack.a);

      for (int var31 = 0; var31 < var9.length; var31++) {
         String var33 = var9[var31];

         for (int var35 = 0; var35 < var33.length(); var35++) {
            var30.set(var31 * var29 + var35, (RecipeItemStack)var8.get(var33.charAt(var35)));
         }
      }

      ShapedRecipePattern var32 = ShapedRecipePattern.a(var8, var9);
      RecipeHolder var34 = new RecipeHolder(
         CraftNamespacedKey.toMinecraft(((ShapedRecipe)recipe).getKey()),
         new ShapedRecipes(((ShapedRecipe)recipe).getGroup(), CraftRecipe.getCategory(((ShapedRecipe)recipe).getCategory()), var32, var28)
      );
      EntityPlayer var36 = ((CraftPlayer)var6).getHandle();
      int var37 = var36.bS.j;
      var36.c.b(new PacketPlayOutAutoRecipe(var37, var34));
   }

   @Override
   public void updateInventoryTitle(InventoryView inventory, String newTitle) {
      CraftInventoryView.sendInventoryTitleChange(inventory, newTitle);
   }
}
