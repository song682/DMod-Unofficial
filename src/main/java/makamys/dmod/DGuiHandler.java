package makamys.dmod;

import cpw.mods.fml.common.network.IGuiHandler;
import makamys.dmod.client.gui.GridStoneScreen;
import makamys.dmod.client.gui.GridStoneMenu;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class DGuiHandler implements IGuiHandler {

    public static final int GRIDSTONE_GUI_ID = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        switch (id) {
            case GRIDSTONE_GUI_ID:
                return new GridStoneMenu(player.inventory, world, x, y, z);
            default:
                return null;
        }
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        switch (id) {
            case GRIDSTONE_GUI_ID:
                return new GridStoneScreen(new GridStoneMenu(player.inventory, world, x, y, z));
            default:
                return null;
        }
    }
}
