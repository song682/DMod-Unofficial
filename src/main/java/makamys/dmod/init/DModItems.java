package makamys.dmod.init;

import cpw.mods.fml.common.registry.GameRegistry;
import decok.dfcdvadstf.catframe.recipe.TagRecipeManager;
import makamys.dmod.ConfigDMod;
import makamys.dmod.content.bundles.bundle.BundleColor;
import makamys.dmod.content.bundles.bundle.BundleItem;
import makamys.dmod.future.item.IItemFuture;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

// Class structure insprired by Et Futurum

public class DModItems {

    public static Item bundle;
    public static Item stainedBundle;
    
    public static void preInit() {
        // initItem() consults the shared enableBundle toggle.
        bundle = initItem(new BundleItem(false));
        stainedBundle = initItem(new BundleItem(true));
    }
    
    public static void postInit() {
        registerRecipes();
    }
    
    private static void registerRecipes() {
        if (!ConfigDMod.enableBundle || bundle == null || stainedBundle == null) {
            return;
        }

        // Both bundle variants share the basic crafting recipe.
        for(Item bundleCraftingItem : ConfigDMod.bundleCraftingItems) {
            for (Item output : new Item[] { bundle, stainedBundle }) {
                TagRecipeManager.addShaped(new ItemStack(output), "SLS", "L L", "LLL", 'L', bundleCraftingItem, 'S', Items.string);
            }
        }
        
        // BundleColor is the single source of truth for dye -> bundle meta mapping;
        // 32767 = wildcard metadata.
        for(BundleColor color : BundleColor.values()) {
            TagRecipeManager.addShapeless(new ItemStack(stainedBundle, 1, color.getBundleMeta()),
                    new ItemStack(stainedBundle, 1, 32767),
                    new ItemStack(Items.dye, 1, color.getDyeMeta()));
        }
    }
    
    private static Item initItem(Item item) {
        if(!(item instanceof IItemFuture) || ((IItemFuture)item).isEnabled()) {
            String name = item.getUnlocalizedName();
            int firstDot = name.lastIndexOf('.');
            GameRegistry.registerItem(item, name.substring(firstDot + 1));
        }
        return item;
    }
    
}
