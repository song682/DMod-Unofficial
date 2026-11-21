package makamys.dmod.init;

import cpw.mods.fml.common.registry.GameRegistry;
import decok.dfcdvadstf.catframe.recipe.TagRecipeManager;
import makamys.dmod.ConfigDMod;
import makamys.dmod.compat.EFRBannerCompat;
import makamys.dmod.compat.EFRCompat;
import makamys.dmod.content.village.block.GridStone;
import makamys.dmod.content.village.block.Loom;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public class DModBlocks {

    public static Block gridstone;
    public static Block loom;

    public static void preInit() {
        if (ConfigDMod.enableGridstone) {
            gridstone = new GridStone();
            GameRegistry.registerBlock(gridstone, "grindstone");
        }
        
        // Loom functionality depends on Et Futurum Requiem's banners, so the
        // block only exists when EFR (and its banner block) is available.
        if (ConfigDMod.enableLoom && EFRCompat.isLoaded() && EFRBannerCompat.isBannerAvailable()) {
            loom = new Loom();
            GameRegistry.registerBlock(loom, "loom");
        }
    }

    public static void postInit() {
        registerRecipes();
    }

    private static void registerRecipes() {
        if (gridstone != null) {
            // Matches the modern recipe: stick on top, stone slab in the middle,
            // any planks on the bottom row.
            TagRecipeManager.addShaped(gridstone, "I-I", "# #",
                    'I', Items.stick,
                    '-', new ItemStack(Blocks.stone_slab, 1, 0),
                    '#', "plankWood");
        }

        if (loom != null) {
            // Matches the modern recipe: string on the top row, any planks below.
            TagRecipeManager.addShaped(loom, "@@", "##",
                    '@', Items.string,
                    '#', "plankWood");
        }
    }
}
