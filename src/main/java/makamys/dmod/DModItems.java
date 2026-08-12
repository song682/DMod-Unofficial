package makamys.dmod;

import cpw.mods.fml.common.registry.GameRegistry;
import decok.dfcdvadstf.catframe.recipe.CatFrameRecipeManager;
import makamys.dmod.bundle.BundleColor;
import makamys.dmod.bundle.BundleItem;
import makamys.dmod.item.IConfigurable;
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
        // initItem() consults isEnabled() (enableBundle / modernBundle)
        bundle = initItem(new BundleItem(false));
        modernBundleItem = initItem(new BundleItem(true));
    }
    
    public static void postInit() {
        registerRecipes();
    }
    
    private static void registerRecipes() {
        // Both versions need the basic crafting recipe (CatFrame ShapedTagRecipe,
        // mirrored by default — equivalent to the old GameRegistry behavior)
        for(Item bundleCraftingItem : ConfigDMod.bundleCraftingItems) {
            CatFrameRecipeManager.addShaped(new ItemStack(bundle), "SLS", "L L", "LLL", 'L', bundleCraftingItem, 'S', Items.string);
        }
        
        // Only modern bundle needs dye recipes. BundleColor is the single source
        // of truth for the dye -> bundle meta mapping (fixes the old 7/8/9 dye
        // mis-mapping bug); 32767 = wildcard metadata.
        if (ConfigDMod.modernBundle && modernBundleItem != null) {
            for(BundleColor color : BundleColor.values()) {
                CatFrameRecipeManager.addShapeless(new ItemStack(modernBundleItem, 1, color.getBundleMeta()),
                        new ItemStack(modernBundleItem, 1, 32767),
                        new ItemStack(Items.dye, 1, color.getDyeMeta()));
            }
        }
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
