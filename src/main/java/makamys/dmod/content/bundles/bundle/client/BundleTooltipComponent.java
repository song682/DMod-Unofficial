package makamys.dmod.content.bundles.bundle.client;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.world.tooltip.TooltipComponent;
import net.minecraft.item.ItemStack;

/**
 * Structured tooltip image component for the bundle preview grid and capacity bar.
 * <p>
 * Data-only holder; geometry lives in {@link BundleTooltipRenderer} and the client
 * render adapter in {@link BundleClientTooltipComponent}. Registered through
 * CatFrame's {@code ItemTooltipImages} provider so the preview no longer depends on
 * NEI's CodeChickenLib line-handler mechanism.
 */
@SideOnly(Side.CLIENT)
public class BundleTooltipComponent implements TooltipComponent {

    /** Stacks shown in the preview grid, most recently inserted first. */
    final List<ItemStack> stacks;

    /** Total occupancy (in 64ths) used by the capacity bar. */
    final int occupancy;

    /** The bundle stack itself, read live for the selected-slot highlight. */
    final ItemStack bundleStack;

    public BundleTooltipComponent(List<ItemStack> stacks, int occupancy, ItemStack bundleStack) {
        this.stacks = stacks;
        this.occupancy = occupancy;
        this.bundleStack = bundleStack;
    }
}
