package makamys.dmod.proxy;

import java.util.Optional;
import java.util.stream.Collectors;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.impl.ModernBlock;
import decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip.ClientTooltipComponent;
import decok.dfcdvadstf.catframe.world.tooltip.ItemTooltipImages;
import makamys.dmod.ConfigDMod;
import makamys.dmod.init.DModBlocks;
import makamys.dmod.init.DModItems;
import makamys.dmod.content.bundles.bundle.BundleContents;
import makamys.dmod.content.bundles.bundle.client.BundleClientTooltipComponent;
import makamys.dmod.content.bundles.bundle.client.BundleItemModel;
import makamys.dmod.content.bundles.bundle.client.BundleTooltipComponent;
import makamys.dmod.content.village.block.GridStone;
import makamys.dmod.future.item.ItemFuture;
import net.minecraft.item.Item;
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
        
        registerBundleTooltips();
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

    /**
     * Hands the bundle preview over to CatFrame's deferred tooltip pipeline. The
     * former wiring used NEI's container tooltip handler plus CodeChickenLib
     * line-handler markers; a provider / client-component pair is the CatFrame
     * equivalent and removes the optional NEI dependency entirely.
     */
    private void registerBundleTooltips() {
        if (!ConfigDMod.enableBundle) return;
        ClientTooltipComponent.register(BundleTooltipComponent.class,
                component -> new BundleClientTooltipComponent((BundleTooltipComponent) component));
        registerBundleTooltip(DModItems.bundle);
        registerBundleTooltip(DModItems.stainedBundle);
    }

    private void registerBundleTooltip(Item item) {
        ItemTooltipImages.register(item, stack -> Optional.of(new BundleTooltipComponent(
                BundleContents.getBundledStacks(stack).collect(Collectors.toList()),
                BundleContents.getOccupancy(stack),
                stack)));
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
