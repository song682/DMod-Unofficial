package makamys.dmod.future.item;

import java.util.List;

import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Shared interface for items that support bundle-style inventory interaction
 * and colored durability bars. Implemented by both {@link ItemFuture} (vanilla Item subclass)
 * and ModernItem-based items.
 */
public interface IItemFuture {

    boolean onStackClicked(ItemStack stack, Slot slot, int button, EntityPlayer player);

    boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, int button, EntityPlayer player);

    boolean getItemBarHasColor(ItemStack stack);

    int getItemBarColor(ItemStack stack);

    @SideOnly(Side.CLIENT)
    void appendTooltip(ItemStack stack, World world, List<String> tooltip);

    @cpw.mods.fml.common.Optional.Method(modid = "CodeChickenCore")
    @SideOnly(Side.CLIENT)
    List<ITooltipLineHandler> getTooltipHandlers(ItemStack stack);
}
