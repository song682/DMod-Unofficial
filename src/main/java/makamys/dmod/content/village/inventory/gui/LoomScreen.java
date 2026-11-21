package makamys.dmod.content.village.inventory.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.screens.container.AbstractContainerScreen;
import decok.dfcdvadstf.catframe.ui.util.SpriteBlitter;
import ganymedes01.etfuturum.blocks.itemblocks.ItemBlockBanner;
import ganymedes01.etfuturum.client.LayeredColorMaskTexture;
import ganymedes01.etfuturum.lib.EnumColor;
import ganymedes01.etfuturum.recipes.ModRecipes;
import ganymedes01.etfuturum.tileentities.TileEntityBanner;
import ganymedes01.etfuturum.tileentities.TileEntityBanner.EnumBannerPattern;
import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

/**
 * Loom screen — ported from 26.1.2 {@code LoomScreen}, built on CatFrame's
 * {@link AbstractContainerScreen}.
 * <p>
 * Layout matches the high version: the 4x4 pattern grid sits at (60, 13) with
 * 14x14 cells, the scroller track spans y 9..65 at x 119, the banner preview
 * occupies (141, 8) at 20x40, and the "too many layers" error icon overlays
 * the result slot. Pattern icons are drawn as a grey flag with the EFR mask
 * blended additively on top, because EFR stores pattern coverage in the red
 * channel of an opaque mask texture. The preview is composited at runtime via
 * EFR's {@link LayeredColorMaskTexture} into a dynamic texture.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class LoomScreen extends AbstractContainerScreen<LoomMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "dmod", "textures/gui/container/loom.png");
    private static final ResourceLocation BANNER_SLOT_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/slot/banner.png");
    private static final ResourceLocation DYE_SLOT_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/slot/dye.png");
    private static final ResourceLocation PATTERN_SLOT_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/slot/banner_pattern.png");
    private static final ResourceLocation SCROLLER_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/loom/scroller.png");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/loom/scroller_disabled.png");
    private static final ResourceLocation PATTERN_SELECTED_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/loom/pattern_selected.png");
    private static final ResourceLocation PATTERN_HIGHLIGHTED_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/loom/pattern_highlighted.png");
    private static final ResourceLocation PATTERN_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/loom/pattern.png");
    private static final ResourceLocation ERROR_SPRITE = new ResourceLocation(
            "dmod", "textures/gui/sprites/container/loom/error.png");

    /** EFR's blank banner texture and pattern masks (both live in the minecraft namespace). */
    private static final ResourceLocation BANNER_BASE = new ResourceLocation(
            "minecraft", "textures/entity/banner_base.png");
    private static final String BANNER_BASE_MASK = "textures/entity/banner/base.png";

    /** Dynamic texture holding the composited preview of the result banner. */
    private static final ResourceLocation PREVIEW_TEXTURE = new ResourceLocation(
            "dmod", "textures/gui/loom_preview.png");

    private static final int PATTERN_COLUMNS = 4;
    private static final int PATTERN_ROWS = 4;
    private static final int PATTERN_IMAGE_SIZE = 14;
    private static final int PATTERNS_X = 60;
    private static final int PATTERNS_Y = 13;

    private static final int SCROLLER_X = 119;
    private static final int SCROLLER_TRACK_Y = 9;
    private static final int SCROLLER_TRACK_HEIGHT = 56;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    private static final int SCROLLER_FULL_HEIGHT = SCROLLER_TRACK_HEIGHT - SCROLLER_HEIGHT;

    private static final int PREVIEW_X = 141;
    private static final int PREVIEW_Y = 8;
    private static final int PREVIEW_WIDTH = 20;
    private static final int PREVIEW_HEIGHT = 40;

    /** Banner texture size in pixels (the front face is a (1,1) 20x40 crop). */
    private static final float BANNER_TEXTURE_SIZE = 64.0F;

    private final Map<EnumBannerPattern, ResourceLocation> patternTextures =
            new EnumMap<EnumBannerPattern, ResourceLocation>(EnumBannerPattern.class);

    private ItemStack lastBanner;
    private ItemStack lastDye;
    private ItemStack lastPattern;
    private ItemStack lastResult;

    private List<EnumBannerPattern> selectablePatterns = Collections.emptyList();
    private boolean displayPatterns;
    private boolean hasMaxPatterns;
    private boolean previewReady;
    private float scrollOffs;
    private boolean scrolling;
    private int startRow;

    public LoomScreen(LoomMenu menu) {
        super(menu);
    }

    // ==================== Per-frame state ====================

    private void updateState() {
        Slot bannerSlot = this.menu.getSlot(LoomMenu.BANNER_SLOT);
        Slot dyeSlot = this.menu.getSlot(LoomMenu.DYE_SLOT);
        Slot patternSlot = this.menu.getSlot(LoomMenu.PATTERN_SLOT);
        Slot resultSlot = this.menu.getSlot(LoomMenu.RESULT_SLOT);

        ItemStack banner = bannerSlot.getStack();
        ItemStack dye = dyeSlot.getStack();
        ItemStack pattern = patternSlot.getStack();
        ItemStack result = resultSlot.getStack();

        if (!ItemStack.areItemStacksEqual(banner, this.lastBanner)
                || !ItemStack.areItemStacksEqual(dye, this.lastDye)
                || !ItemStack.areItemStacksEqual(pattern, this.lastPattern)) {
            this.hasMaxPatterns = this.menu.hasMaxPatternLayers();
            this.selectablePatterns = this.menu.getSelectablePatterns();
            this.displayPatterns = banner != null && dye != null
                    && !this.hasMaxPatterns && !this.selectablePatterns.isEmpty();
        }

        if (!ItemStack.areItemStacksEqual(result, this.lastResult)) {
            this.updatePreview(result);
        }

        if (this.startRow >= this.totalRowCount()) {
            this.startRow = 0;
            this.scrollOffs = 0.0F;
        }

        this.lastBanner = copy(banner);
        this.lastDye = copy(dye);
        this.lastPattern = copy(pattern);
        this.lastResult = copy(result);
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private int totalRowCount() {
        return (this.selectablePatterns.size() + PATTERN_COLUMNS - 1) / PATTERN_COLUMNS;
    }

    private boolean isScrollBarActive() {
        return this.displayPatterns
                && this.selectablePatterns.size() > PATTERN_COLUMNS * PATTERN_ROWS;
    }

    private void clampScroll(int offscreenRows) {
        this.scrollOffs = MathHelper.clamp_float(this.scrollOffs, 0.0F, 1.0F);
        this.startRow = Math.max((int) (this.scrollOffs * offscreenRows + 0.5F), 0);
    }

    // ==================== Preview compositing ====================

    /**
     * Rebuilds the dynamic preview texture from the result banner's base color
     * and pattern layers, using EFR's layered color-mask compositor.
     */
    private void updatePreview(ItemStack result) {
        this.previewReady = false;
        if (result == null) {
            return;
        }

        List<String> masks = new ArrayList<String>();
        List<EnumColor> colors = new ArrayList<EnumColor>();
        masks.add(BANNER_BASE_MASK);
        colors.add(EnumColor.fromDamage(TileEntityBanner.getBaseColor(result)));

        NBTTagCompound blockEntityTag = ItemBlockBanner.getSubTag(result, "BlockEntityTag", false);
        if (blockEntityTag != null && blockEntityTag.hasKey("Patterns", 9)) {
            NBTTagList patterns = blockEntityTag.getTagList("Patterns", 10);
            for (int i = 0; i < patterns.tagCount(); i++) {
                NBTTagCompound layer = patterns.getCompoundTagAt(i);
                EnumBannerPattern pattern = EnumBannerPattern.getPatternByID(layer.getString("Pattern"));
                if (pattern != null) {
                    masks.add("textures/entity/banner/" + pattern.getPatternName() + ".png");
                    colors.add(EnumColor.fromDamage(layer.getInteger("Color")));
                }
            }
        }

        this.mc.getTextureManager().deleteTexture(PREVIEW_TEXTURE);
        this.mc.getTextureManager().loadTexture(PREVIEW_TEXTURE,
                new LayeredColorMaskTexture(BANNER_BASE, masks, colors));
        this.previewReady = true;
    }

    private ResourceLocation getPatternTexture(EnumBannerPattern pattern) {
        ResourceLocation texture = this.patternTextures.get(pattern);
        if (texture == null) {
            texture = new ResourceLocation("minecraft",
                    "textures/entity/banner/" + pattern.getPatternName() + ".png");
            this.patternTextures.put(pattern, texture);
        }
        return texture;
    }

    // ==================== Rendering ====================

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.updateState();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);

        Slot bannerSlot = this.menu.getSlot(LoomMenu.BANNER_SLOT);
        Slot dyeSlot = this.menu.getSlot(LoomMenu.DYE_SLOT);
        Slot patternSlot = this.menu.getSlot(LoomMenu.PATTERN_SLOT);
        Slot resultSlot = this.menu.getSlot(LoomMenu.RESULT_SLOT);

        if (!bannerSlot.getHasStack()) {
            SpriteBlitter.blitStretch(BANNER_SLOT_SPRITE,
                    this.guiLeft + bannerSlot.xDisplayPosition,
                    this.guiTop + bannerSlot.yDisplayPosition, 16, 16, 16, 16);
        }
        if (!dyeSlot.getHasStack()) {
            SpriteBlitter.blitStretch(DYE_SLOT_SPRITE,
                    this.guiLeft + dyeSlot.xDisplayPosition,
                    this.guiTop + dyeSlot.yDisplayPosition, 16, 16, 16, 16);
        }
        if (!patternSlot.getHasStack()) {
            SpriteBlitter.blitStretch(PATTERN_SLOT_SPRITE,
                    this.guiLeft + patternSlot.xDisplayPosition,
                    this.guiTop + patternSlot.yDisplayPosition, 16, 16, 16, 16);
        }

        int scrollerOffset = (int) (SCROLLER_FULL_HEIGHT * this.scrollOffs);
        ResourceLocation scroller = this.isScrollBarActive() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        SpriteBlitter.blitStretch(scroller,
                this.guiLeft + SCROLLER_X, this.guiTop + PATTERNS_Y + scrollerOffset,
                SCROLLER_WIDTH, SCROLLER_HEIGHT, SCROLLER_WIDTH, SCROLLER_HEIGHT);

        if (this.previewReady && !this.hasMaxPatterns) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(PREVIEW_TEXTURE);
            Gui.func_152125_a(this.guiLeft + PREVIEW_X, this.guiTop + PREVIEW_Y,
                    1.0F, 1.0F, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                    PREVIEW_WIDTH, PREVIEW_HEIGHT, BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE);
        } else if (this.hasMaxPatterns) {
            SpriteBlitter.blitStretch(ERROR_SPRITE,
                    this.guiLeft + resultSlot.xDisplayPosition - 5,
                    this.guiTop + resultSlot.yDisplayPosition - 5, 26, 26, 26, 26);
        }

        if (this.displayPatterns) {
            this.drawPatternGrid(mouseX, mouseY);
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawPatternGrid(int mouseX, int mouseY) {
        int gridX = this.guiLeft + PATTERNS_X;
        int gridY = this.guiTop + PATTERNS_Y;
        int selected = this.menu.getSelectedBannerPatternIndex();

        grid:
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int column = 0; column < PATTERN_COLUMNS; column++) {
                int index = (row + this.startRow) * PATTERN_COLUMNS + column;
                if (index >= this.selectablePatterns.size()) {
                    break grid;
                }

                int x = gridX + column * PATTERN_IMAGE_SIZE;
                int y = gridY + row * PATTERN_IMAGE_SIZE;
                boolean highlighted = mouseX >= x && mouseY >= y
                        && mouseX < x + PATTERN_IMAGE_SIZE && mouseY < y + PATTERN_IMAGE_SIZE;

                ResourceLocation background;
                if (index == selected) {
                    background = PATTERN_SELECTED_SPRITE;
                } else if (highlighted) {
                    background = PATTERN_HIGHLIGHTED_SPRITE;
                } else {
                    background = PATTERN_SPRITE;
                }
                SpriteBlitter.blitStretch(background, x, y,
                        PATTERN_IMAGE_SIZE, PATTERN_IMAGE_SIZE,
                        PATTERN_IMAGE_SIZE, PATTERN_IMAGE_SIZE);

                EnumBannerPattern pattern = this.selectablePatterns.get(index);

                // Grey flag backing, then the mask added on top. EFR masks are
                // opaque black with coverage in the red channel, so additive
                // blending keeps the black background invisible.
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                Gui.drawRect(x + 4, y + 2, x + 9, y + 12, 0xFF4C4C4C);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.mc.getTextureManager().bindTexture(this.getPatternTexture(pattern));
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                Gui.func_152125_a(x + 4, y + 2, 1.0F, 1.0F, 20, 40, 5, 10,
                        BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                if (highlighted) {
                    GuiGraphicsExtractor.getInstance().setTooltipForNextFrame(
                            StatCollector.translateToLocal("item.banner."
                                    + pattern.getPatternName() + "." + this.menu.getPatternDyeName()),
                            mouseX, mouseY);
                }
            }
        }
    }

    // ==================== Mouse ====================

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.displayPatterns && mouseButton == 0) {
            int gridX = this.guiLeft + PATTERNS_X;
            int gridY = this.guiTop + PATTERNS_Y;

            for (int row = 0; row < PATTERN_ROWS; row++) {
                for (int column = 0; column < PATTERN_COLUMNS; column++) {
                    int index = (row + this.startRow) * PATTERN_COLUMNS + column;
                    if (index >= this.selectablePatterns.size()) {
                        break;
                    }
                    int x = gridX + column * PATTERN_IMAGE_SIZE;
                    int y = gridY + row * PATTERN_IMAGE_SIZE;
                    if (mouseX >= x && mouseY >= y
                            && mouseX < x + PATTERN_IMAGE_SIZE && mouseY < y + PATTERN_IMAGE_SIZE) {
                        this.menu.selectPattern(index);
                        this.mc.playerController.sendEnchantPacket(this.menu.windowId, index);
                        return;
                    }
                }
            }

            int scrollerX = this.guiLeft + SCROLLER_X;
            int scrollerY = this.guiTop + SCROLLER_TRACK_Y;
            if (mouseX >= scrollerX && mouseX < scrollerX + SCROLLER_WIDTH
                    && mouseY >= scrollerY && mouseY < scrollerY + SCROLLER_TRACK_HEIGHT
                    && this.isScrollBarActive()) {
                this.scrolling = true;
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (this.scrolling && this.displayPatterns) {
            int offscreenRows = this.totalRowCount() - PATTERN_ROWS;
            if (offscreenRows > 0) {
                int trackTop = this.guiTop + PATTERNS_Y;
                this.scrollOffs = ((float) (mouseY - trackTop) - 7.5F) / SCROLLER_FULL_HEIGHT;
                this.clampScroll(offscreenRows);
            }
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state >= 0) {
            this.scrolling = false;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && this.displayPatterns) {
            int offscreenRows = this.totalRowCount() - PATTERN_ROWS;
            if (offscreenRows > 0) {
                this.scrollOffs -= (wheel > 0 ? 1.0F : -1.0F) / offscreenRows;
                this.clampScroll(offscreenRows);
            }
        }
    }

    // ==================== Keyboard ====================

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

    // ==================== Lifecycle ====================

    @Override
    public void removed() {
        super.removed();
        this.mc.getTextureManager().deleteTexture(PREVIEW_TEXTURE);
        this.previewReady = false;
    }
}
