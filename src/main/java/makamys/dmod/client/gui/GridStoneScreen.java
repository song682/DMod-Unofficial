package makamys.dmod.client.gui;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.screens.container.AbstractContainerScreen;
import decok.dfcdvadstf.catframe.ui.util.SpriteBlitter;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

/**
 * Grindstone screen — ported from 26.1.2 {@code GrindstoneScreen}, built on
 * CatFrame's {@link AbstractContainerScreen}.
 * <p>
 * Background blit and the {@code container/grindstone/error} sprite use the
 * same coordinates as the high version: the error icon sits at (92, 31) with a
 * size of 28x21 and is shown whenever an input is occupied but no result exists.
 */
@SideOnly(Side.CLIENT)
public class GridStoneScreen extends AbstractContainerScreen<GridStoneMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "dmod:textures/gui/container/grindstone.png");
    private static final ResourceLocation ERROR_SPRITE = new ResourceLocation(
            "dmod:textures/gui/sprites/container/grindstone/error.png");

    private static final int ERROR_X = 92;
    private static final int ERROR_Y = 31;
    private static final int ERROR_WIDTH = 28;
    private static final int ERROR_HEIGHT = 21;

    public GridStoneScreen(GridStoneMenu menu) {
        super(menu);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        // The texture is 256x256, which matches drawTexturedModalRect's fixed
        // 256-unit normalisation, so the container is a plain (0,0) crop.
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);

        if ((this.menu.getSlot(0).getHasStack() || this.menu.getSlot(1).getHasStack())
                && !this.menu.getSlot(2).getHasStack()) {
            SpriteBlitter.blitStretch(ERROR_SPRITE,
                    this.guiLeft + ERROR_X, this.guiTop + ERROR_Y,
                    ERROR_WIDTH, ERROR_HEIGHT, ERROR_WIDTH, ERROR_HEIGHT);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRendererObj.drawString(StatCollector.translateToLocal("container.grindstone_title"), 8, 6, 4210752);
        this.fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8,
                this.ySize - 96 + 2, 4210752);
    }

    /**
     * Restores the vanilla {@code GuiContainer} keyboard behaviour that the
     * CatFrame base class drops (inventory key closes the screen, hotbar keys,
     * pick-block / drop on the hovered slot), while keeping CatFrame's own
     * widget dispatch for Esc and focused children.
     */
    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
            this.mc.thePlayer.closeScreen();
            return;
        }

        super.keyTyped(typedChar, keyCode);

        if (keyCode == Keyboard.KEY_ESCAPE) {
            return;
        }

        Slot slot = this.hoveredSlot;
        if (slot != null && slot.getHasStack()) {
            if (keyCode == this.mc.gameSettings.keyBindPickBlock.getKeyCode()) {
                this.handleMouseClick(slot, slot.slotNumber, 0, 3);
            } else if (keyCode == this.mc.gameSettings.keyBindDrop.getKeyCode()) {
                this.handleMouseClick(slot, slot.slotNumber, isCtrlKeyDown() ? 1 : 0, 4);
            }
        }

        this.checkHotbarKeys(keyCode);
    }
}
