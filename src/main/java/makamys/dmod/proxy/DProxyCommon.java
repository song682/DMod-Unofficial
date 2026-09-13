package makamys.dmod.proxy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.item.ItemTossEvent;

public class DProxyCommon {
    
    public Cache<EntityItem, EntityPlayer> itemDropperMap = CacheBuilder.newBuilder().maximumSize(1000).build();
    
    public void init() {
    }
    
    @SubscribeEvent
    public void onItemTossEvent(ItemTossEvent event) {
        itemDropperMap.put(event.entityItem, event.player);
    }
    
}
