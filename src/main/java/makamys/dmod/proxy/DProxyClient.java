package makamys.dmod.proxy;

import static makamys.dmod.DModConstants.LOGGER;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import makamys.dmod.ConfigDMod;
import makamys.dmod.DModItems;
import makamys.dmod.bundle.client.BundleItemModel;
import makamys.dmod.client.render.ModelFox;
import makamys.dmod.client.render.RenderFox;
import makamys.dmod.compat.NEICompat;
import makamys.dmod.entity.EntityFox;
import makamys.dmod.future.item.ItemFuture;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public class DProxyClient extends DProxyCommon {
    
    @Override
    public void init() {
        super.init();
        
        if(ConfigDMod.enableFox) {
            RenderingRegistry.registerEntityRenderingHandler(EntityFox.class, new RenderFox(new ModelFox(), 0.4F));
        }
        
        // TODO don't crash if chicken is not present
        if(Loader.isModLoaded("NotEnoughItems")) {
            NEICompat.init();
        } else {
            LOGGER.warn("NotEnoughItems was not found. Some optional features will not work.");
        }
        
        registerBundleModels();
    }
    
    /**
     * Register the CatFrame decision-tree models for both bundle items.
     * <p>
     * Persistent registration deliberately suppresses the automatic items/ registration:
     * the decision tree is parsed by {@link BundleItemModel} itself (so the
     * {@code bundle_selected_item} marker layer can be intercepted), while texture
     * collection is still done independently by CatFrame's NamespaceLoadTask — the
     * two do not conflict.
     * <p>
     * NOTE: CatFrame's incremental rebuild ({@code registerItemModels}, fired on the
     * item-atlas stitch Post) unconditionally overwrites registered models from
     * items/ JSONs (no containsKey guard in step 4a), so we re-assert our
     * registration with LOWEST priority after CatFrame's own handler.
     */
    private void registerBundleModels() {
        if(ConfigDMod.enableBundle) {
            ModelRegistry.registerItemModel(DModItems.bundle, BundleItemModel.load("dmod", "bundle"));
        }
        if(ConfigDMod.modernBundle) {
            ModelRegistry.registerItemModel(DModItems.modernBundleItem, BundleItemModel.load("dmod", "stained_bundle"));
        }
    }
    
    /**
     * Register bundle item textures with the item atlas.
     * <p>
     * Necessary because the bundle items/ JSON uses a custom type
     * ({@code dmod:bundle/selected_item}) that CatFrame's standard
     * {@code NamespaceLoadTask} cannot parse — the resulting
     * {@code JsonParseException} causes the entire JSON to be skipped,
     * so none of the bundle model textures are collected into the atlas.
     * This handler ensures they are registered explicitly.
     */
    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 1) {
            registerBundleTextures(event);
        }
    }

    private void registerBundleTextures(TextureStitchEvent.Pre event) {
        String[] closed = { "bundle", "white_bundle", "orange_bundle", "magenta_bundle",
                "light_blue_bundle", "yellow_bundle", "lime_bundle", "pink_bundle",
                "gray_bundle", "light_gray_bundle", "cyan_bundle", "purple_bundle",
                "blue_bundle", "brown_bundle", "green_bundle", "red_bundle", "black_bundle" };
        for (String name : closed) {
            event.map.registerIcon("dmod:" + name);
            event.map.registerIcon("dmod:" + name + "_open_back");
            event.map.registerIcon("dmod:" + name + "_open_front");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        // Re-assert bundle registrations after CatFrame's incremental item-model rebuild
        if(event.map.getTextureType() == 1) {
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
