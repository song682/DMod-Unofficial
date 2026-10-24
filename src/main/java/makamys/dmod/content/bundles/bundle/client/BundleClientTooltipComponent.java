package makamys.dmod.content.bundles.bundle.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.FontRenderer;

/**
 * Client render adapter for {@link BundleTooltipComponent}.
 * <p>
 * Reports the grid + bar size to CatFrame's tooltip layout and delegates drawing
 * to {@link BundleTooltipRenderer}. Registered via
 * {@code ClientTooltipComponent.register} during client init.
 */
@SideOnly(Side.CLIENT)
public class BundleClientTooltipComponent implements ClientTooltipComponent {

    private final BundleTooltipRenderer renderer;

    public BundleClientTooltipComponent(BundleTooltipComponent component) {
        this.renderer = new BundleTooltipRenderer(component.stacks, component.occupancy, component.bundleStack);
    }

    @Override
    public int getWidth(FontRenderer font) {
        return this.renderer.getSize().width;
    }

    @Override
    public int getHeight(FontRenderer font) {
        return this.renderer.getSize().height;
    }

    @Override
    public void renderImage(FontRenderer font, int x, int y, int w, int h) {
        this.renderer.draw(x, y);
    }
}
