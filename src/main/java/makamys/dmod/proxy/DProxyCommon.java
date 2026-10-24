package makamys.dmod.proxy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import makamys.dmod.DGuiHandler;
import makamys.dmod.DMod;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.item.ItemTossEvent;

public class DProxyCommon {
    
    public Cache<EntityItem, EntityPlayer> itemDropperMap = CacheBuilder.newBuilder().maximumSize(1000).build();


    public void preInit() {
        
    }
    
    public void init() {
        NetworkRegistry.INSTANCE.registerGuiHandler(DMod.instance, new DGuiHandler());
    }
    
    @SubscribeEvent
    public void onItemTossEvent(ItemTossEvent event) {
        itemDropperMap.put(event.entityItem, event.player);
    }
    
}
