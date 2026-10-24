package makamys.dmod.proxy;

import static makamys.dmod.DModConstants.LOGGER;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.impl.ModernBlock;
import makamys.dmod.ConfigDMod;
import makamys.dmod.DModBlocks;
import makamys.dmod.DModItems;
import makamys.dmod.content.village.block.GridStone;
import makamys.dmod.content.bundles.bundle.client.BundleItemModel;
import makamys.dmod.compat.NEICompat;
import makamys.dmod.future.item.ItemFuture;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public class DProxyClient extends DProxyCommon {

    @Override
    public void preInit() {
        super.preInit();

        // CatFrame parses items/ decision trees during its first texture stitch,
        // before FML initialization. Register the custom node during preInit.
        BundleItemModel.registerNodeTypes();
    }
    
    @Override
    public void init() {
        super.init();
        
        // TODO don't crash if chicken is not present
        if(Loader.isModLoaded("NotEnoughItems")) {
            NEICompat.init();
        } else {
            LOGGER.warn("NotEnoughItems was not found. Some optional features will not work.");
        }

        registerBundleModels();
        registerBlockModels();
    }

    /**
     * Hands the grindstone over to CatFrame's model pipeline. The typed state
     * definition is registered first so the pipeline resolves variants through
     * it (and therefore bakes the blockstate's x / y rotation, which the wall
     * and ceiling orientations depend on).
     */
    private void registerBlockModels() {
        if (DModBlocks.gridstone instanceof GridStone) {
            GridStone gridstone = (GridStone) DModBlocks.gridstone;
            ModelRegistry.registerStateDefinition(gridstone, gridstone.getStateDefinition());
            ModernBlock.register(gridstone);
        }
    }

    private void registerBundleModels() {
        if (ConfigDMod.enableBundle) {
            ModelRegistry.registerItemModel(DModItems.bundle, BundleItemModel.load("dmod", "bundle"));
            ModelRegistry.registerItemModel(DModItems.stainedBundle, BundleItemModel.load("dmod", "stained_bundle"));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() == 1) {
            registerBundleModels();
        }
    }
    
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if(event.itemStack.getItem() instanceof ItemFuture) {
            ((ItemFuture)event.itemStack.getItem()).appendTooltip(event.itemStack, event.entity.worldObj, event.toolTip);
        }
    }
    
}
