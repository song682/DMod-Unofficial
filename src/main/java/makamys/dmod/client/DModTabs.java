package makamys.dmod.client;

import makamys.dmod.DModItems;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.StatCollector;

public class DModTabs extends CreativeTabs {

    public static final CreativeTabs dmodTabInstance = new DModTabs(CreativeTabs.getNextID(), StatCollector.translateToLocal("tab.dmod"));

    public DModTabs(int position, String tabName) {
        super(position, tabName);
    }

    @Override
    public Item getTabIconItem() {
        return DModItems.bundle;
    }
}
