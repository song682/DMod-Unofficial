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
 * Shared interface for items that support bundle-style inventory interaction,
 * colored durability bars, and runtime configuration gating.
 * Merges the former {@code IConfigurable} contract so that a single instanceof
 * check covers interaction, tooltip, and enablement.
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

    /**
     * Returns whether this item is enabled according to the current config.
     */
    boolean isEnabled();
}
