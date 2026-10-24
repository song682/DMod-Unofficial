package makamys.dmod.compat;

import codechicken.nei.guihook.GuiContainerManager;
import makamys.dmod.content.bundles.tooltip.DTooltipHandler;

public class NEICompat {
    
    public static void init() {
        GuiContainerManager.addTooltipHandler(new DTooltipHandler());
    }
    
}
