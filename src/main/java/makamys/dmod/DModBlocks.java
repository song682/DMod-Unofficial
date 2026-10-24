package makamys.dmod;

import cpw.mods.fml.common.registry.GameRegistry;
import decok.dfcdvadstf.catframe.recipe.TagRecipeManager;
import makamys.dmod.content.village.block.GridStone;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public class DModBlocks {

    public static Block gridstone;

    public static void preInit() {
        if (ConfigDMod.enableGridstone) {
            gridstone = new GridStone();
            GameRegistry.registerBlock(gridstone, "grindstone");
        }
    }

    public static void postInit() {
        registerRecipes();
    }

    private static void registerRecipes() {
        if (gridstone == null) {
            return;
        }

        // Matches the modern recipe: stick on top, stone slab in the middle,
        // any planks on the bottom row.
        TagRecipeManager.addShaped(gridstone, "I-I", "# #",
                'I', Items.stick,
                '-', new ItemStack(Blocks.stone_slab, 1, 0),
                '#', "plankWood");
    }
}
