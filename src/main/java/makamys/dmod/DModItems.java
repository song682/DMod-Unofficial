package makamys.dmod;

import cpw.mods.fml.common.registry.GameRegistry;
import makamys.dmod.item.IConfigurable;
import makamys.dmod.item.ItemBundle;
import makamys.dmod.item.ItemModernBundle;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

// Class structure insprired by Et Futurum

public class DModItems {
    
    // Classic bundle (always available when enableBundle=true)
    public static Item bundle;
    // Modern dyeable bundle (available when modernBundle=true)
    public static Item modernBundleItem;
    
    public static void preInit() {
        // Always register classic bundle when bundles are enabled
        if (ConfigDMod.enableBundle) {
            bundle = initItem(new ItemBundle());
            // Additionally register modern bundle when modernBundle=true
            if (ConfigDMod.modernBundle) {
                modernBundleItem = initItem(new ItemModernBundle());
            }
        }
    }
    
    public static void postInit() {
        registerRecipes();
    }
    
    private static void registerRecipes() {
        // Both versions need the basic crafting recipe
        for(Item bundleCraftingItem : ConfigDMod.bundleCraftingItems) {
            GameRegistry.addShapedRecipe(new ItemStack(bundle), new Object[] {"SLS", "L L", "LLL", 'L', bundleCraftingItem, 'S', Items.string});
        }
        
        // Only modern bundle needs dye recipes
        if (ConfigDMod.modernBundle && modernBundleItem != null) {
            registerDyeRecipe(Items.dye, 0, 15); // Black dye -> Black bundle (meta 15)
            registerDyeRecipe(Items.dye, 1, 14); // Red dye -> Red bundle (meta 14)
            registerDyeRecipe(Items.dye, 2, 13); // Green dye -> Green bundle (meta 13)
            registerDyeRecipe(Items.dye, 3, 12); // Brown dye -> Brown bundle (meta 12)
            registerDyeRecipe(Items.dye, 4, 11); // Blue dye -> Blue bundle (meta 11)
            registerDyeRecipe(Items.dye, 5, 10); // Purple dye -> Purple bundle (meta 10)
            registerDyeRecipe(Items.dye, 6, 9);  // Cyan dye -> Cyan bundle (meta 9)
            registerDyeRecipe(Items.dye, 7, 7);  // Light gray dye -> Light gray bundle (meta 7)
            registerDyeRecipe(Items.dye, 8, 6);  // Gray dye -> Gray bundle (meta 6)
            registerDyeRecipe(Items.dye, 9, 8);  // Pink dye -> Pink bundle (meta 8)
            registerDyeRecipe(Items.dye, 10, 5); // Lime dye -> Lime bundle (meta 5)
            registerDyeRecipe(Items.dye, 11, 4); // Yellow dye -> Yellow bundle (meta 4)
            registerDyeRecipe(Items.dye, 12, 3); // Light blue dye -> Light blue bundle (meta 3)
            registerDyeRecipe(Items.dye, 13, 2); // Magenta dye -> Magenta bundle (meta 2)
            registerDyeRecipe(Items.dye, 14, 1); // Orange dye -> Orange bundle (meta 1)
            registerDyeRecipe(Items.dye, 15, 0); // White dye -> White bundle (meta 0)
        }
    }
    
    private static void registerDyeRecipe(Item dyeItem, int dyeMeta, int bundleMeta) {
        ItemStack dyeStack = new ItemStack(dyeItem, 1, dyeMeta);
        ItemStack bundleAnyColor = new ItemStack(modernBundleItem, 1, 32767); // Wildcard metadata
        
        // Shapeless recipe: any bundle + dye = colored bundle
        GameRegistry.addShapelessRecipe(new ItemStack(modernBundleItem, 1, bundleMeta), 
            new Object[] {bundleAnyColor, dyeStack});
    }
    
    private static Item initItem(Item item) {
        if(!(item instanceof IConfigurable) || ((IConfigurable)item).isEnabled()) {
            String name = item.getUnlocalizedName();
            int firstDot = name.lastIndexOf('.');
            GameRegistry.registerItem(item, name.substring(firstDot + 1));
        }
        return item;
    }
    
}
