package makamys.dmod.bundle.client;

import static makamys.dmod.DModConstants.MODID;

import java.awt.Dimension;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import makamys.dmod.bundle.BundleContents;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * 统一的收纳袋 tooltip 渲染器（由旧 ModernBundleTooltipHandler 迁入，经典/现代
 * bundle 共用同一 modern 视觉）。
 * <p>
 * Layout rules follow the vanilla Bundle spec:
 * <ul>
 *   <li>Stacks are drawn right-to-left, top-to-bottom. The most recently inserted stack
 *       lives at the top-right slot and is rendered with a highlight.</li>
 *   <li>When {@code n <= 12}, every stack is shown. Otherwise we show
 *       {@code 11 - ((4 - (n mod 4)) mod 4)} stacks, reserving the bottom-right cell
 *       for a {@code "+N"} overflow counter so the last row is always full.</li>
 *   <li>A capacity bar lives below the grid, blue while filling, red when full,
 *       with an "Empty"/"Full" hint text painted at the extremes.</li>
 * </ul>
 * Textures live in {@code assets/dmod/textures/gui/container/}, each paired with a
 * {@code .mcmeta} file whose {@code stretching} metadata drives CatFrame's
 * {@link decok.dfcdvadstf.catframe.ui.util.TextureStretching#drawAuto}:
 * <pre>
 * item_background.png            24x24  static(24)        - slot background
 * item_background_highlighted.png 24x24  static(24)        - highlighted slot (top-right)
 * filled_bar_boader.png          14x14  nine_patch(edge 4) - bar border, 12px effective area
 * filled_bar_half_filled.png     8x8    nine_patch(edge 3) - bar fill, not full (blue)
 * filled_bar_filled.png          8x8    nine_patch(edge 3) - bar fill, full (red)
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class BundleTooltipRenderer implements ITooltipLineHandler {

    // Per-sprite textures; each is paired with a .mcmeta carrying stretching metadata.
    private static final ResourceLocation TEX_SLOT =
            new ResourceLocation(MODID, "textures/gui/container/item_background.png");
    private static final ResourceLocation TEX_SLOT_HIGHLIGHTED =
            new ResourceLocation(MODID, "textures/gui/container/item_background_highlighted.png");
    private static final ResourceLocation TEX_BAR_BORDER =
            new ResourceLocation(MODID, "textures/gui/container/filled_bar_boader.png");
    private static final ResourceLocation TEX_BAR_HALF_FILLED =
            new ResourceLocation(MODID, "textures/gui/container/filled_bar_half_filled.png");
    private static final ResourceLocation TEX_BAR_FILLED =
            new ResourceLocation(MODID, "textures/gui/container/filled_bar_filled.png");

    // Grid layout
    private static final int SLOT_W = 24, SLOT_H = 24;
    private static final int COLUMNS = 4;
    private static final int MAX_ROWS = 3;
    private static final int CAPACITY = BundleContents.MAX_STORAGE;

    // Outer padding / gaps
    private static final int PAD = 2;
    private static final int BAR_GAP = 2;
    // Bar border is 14x14: a 1px transparent rim around a 12px effective area
    // (which includes the 1px top hairline, tiled across the full width).
    private static final int BORD_W = 14, BORD_H = 14;
    // Bar fill is 8x8 with a 1px transparent rim; drawn 12px tall so its opaque
    // content (10px) sits exactly inside the border's effective area.
    private static final int FILL_H = 12;
    // Vertical offset of the fill relative to the border (matches the transparent rim).
    private static final int FILL_Y_OFFSET = 1;

    private final List<ItemStack> inventory;
    private final int occupancy;
    private final int displayCount;
    private final int overflow;
    private final int rows;
    private final boolean isEmpty;

    public BundleTooltipRenderer(List<ItemStack> stacks, int occupancy) {
        this.inventory = stacks;
        this.occupancy = occupancy;
        this.isEmpty = stacks.isEmpty();

        int n = stacks.size();
        int disp;
        if (n <= 12) {
            disp = n;
        } else {
            // Keep (disp + 1) a multiple of 4 so the last row is always filled.
            disp = 11 - ((4 - (n % 4)) % 4);
        }
        this.displayCount = disp;
        this.overflow = Math.max(0, n - disp);

        int cells = disp + (overflow > 0 ? 1 : 0);
        int r = (int) Math.ceil(cells / (double) COLUMNS);
        if (r < 1) r = 1;
        if (r > MAX_ROWS) r = MAX_ROWS;
        this.rows = r;
    }

    @Override
    public Dimension getSize() {
        if (isEmpty) {
            // Show hint text instead of grid
            FontRenderer fr = GuiDraw.fontRenderer;
            String hint = I18n.format("item.dmod.stained_bundle.empty_hint");
            int w = fr.getStringWidth(hint) + PAD * 2;
            int h = fr.FONT_HEIGHT + PAD * 2 + BAR_GAP + BORD_H;
            return new Dimension(Math.max(w, COLUMNS * SLOT_W + PAD * 2), h);
        }
        
        int w = COLUMNS * SLOT_W + PAD * 2;
        int h = rows * SLOT_H + PAD * 2 + BAR_GAP + BORD_H;
        return new Dimension(w, h);
    }

    @Override
    public void draw(int x, int y) {
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GuiContainerManager.enable2DRender();

        if (isEmpty) {
            drawEmptyHint(x + PAD, y + PAD);
            drawBar(x + PAD, y + PAD + GuiDraw.fontRenderer.FONT_HEIGHT + BAR_GAP);
        } else {
            drawGrid(x + PAD, y + PAD);
            drawBar(x + PAD, y + PAD + rows * SLOT_H + BAR_GAP);
        }

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
    }
    
    private void drawEmptyHint(int x, int y) {
        FontRenderer fr = GuiDraw.fontRenderer;
        String hint = I18n.format("item.dmod.stained_bundle.empty_hint");
        int tw = fr.getStringWidth(hint);
        fr.drawStringWithShadow(hint,
                x + (COLUMNS * SLOT_W - tw) / 2,
                y + (GuiDraw.fontRenderer.FONT_HEIGHT) / 2,
                0xFF888888);
    }

    private void drawGrid(int x, int y) {
        int cells = displayCount + (overflow > 0 ? 1 : 0);
        int totalCells = rows * COLUMNS;
        FontRenderer fr = GuiDraw.fontRenderer;

        for (int i = 0; i < totalCells; i++) {
            // i=0 is the top-right cell (the most recently inserted stack).
            int row = i / COLUMNS;
            int col = (COLUMNS - 1) - (i % COLUMNS);
            int sx = x + col * SLOT_W;
            int sy = y + row * SLOT_H;

            boolean isTopStack = i == 0 && displayCount > 0;
            drawAuto(isTopStack ? TEX_SLOT_HIGHLIGHTED : TEX_SLOT,
                    sx, sy, SLOT_W, SLOT_H,
                    TextureStretching.StretchType.STATIC, SLOT_W, SLOT_H, 0, 0, 0, 0);

            if (i >= cells) continue;

            if (overflow > 0 && i == cells - 1) {
                // "+N" overflow marker in the bottom-right cell.
                String s = "+" + overflow;
                int tw = fr.getStringWidth(s);
                GuiContainerManager.drawItems.zLevel += 200f;
                fr.drawStringWithShadow(s,
                        sx + (SLOT_W - tw) / 2,
                        sy + (SLOT_H - fr.FONT_HEIGHT) / 2,
                        0xFFFFFFFF);
                GuiContainerManager.drawItems.zLevel -= 200f;
            } else if (i < inventory.size()) {
                ItemStack is = inventory.get(i);
                if (is != null) {
                    GuiContainerManager.drawItems.zLevel += 200f;
                    // 24x24 cell, 16x16 item -> 4px inset
                    GuiContainerManager.drawItem(sx + 4, sy + 4, is);
                    GuiContainerManager.drawItems.zLevel -= 200f;
                }
            }
        }
    }

    private void drawBar(int x, int y) {
        int barW = COLUMNS * SLOT_W;

        // 1) Border: nine-patch stretching driven by the .mcmeta. The 14x14 sprite
        //    has a 1px transparent rim; its 12px effective area includes the 1px
        //    top hairline, which is tiled across the full width automatically.
        //    边框：mcmeta 九宫格自动拉伸，顶部 1px 细线与两侧圆角帽自动延伸。
        drawAuto(TEX_BAR_BORDER, x, y, barW, BORD_H,
                TextureStretching.StretchType.NINE_PATCH, BORD_W, BORD_H, 4, 4, 4, 4);

        // 2) Fill scaled by occupancy / CAPACITY. Its length matches the border's
        //    full width (the 1px transparent rim makes the opaque content line up
        //    with the border's effective area), and the nine-patch keeps rounded
        //    caps on both ends - including the right cap the old code omitted.
        //    填充：长度与边框一致；九宫格自动保留左右圆角帽（右侧帽子效果）。
        boolean full = occupancy >= CAPACITY;
        int fillPx = (int) Math.round(barW * (Math.min(occupancy, CAPACITY) / (double) CAPACITY));
        if (fillPx < 0) fillPx = 0;
        if (fillPx > barW) fillPx = barW;
        if (fillPx > 0) {
            drawAuto(full ? TEX_BAR_FILLED : TEX_BAR_HALF_FILLED,
                    x, y + FILL_Y_OFFSET, fillPx, FILL_H,
                    TextureStretching.StretchType.NINE_PATCH, 8, 8, 3, 3, 3, 3);
        }

        // 3) empty / full hint text.
        String key = null;
        if (occupancy <= 0) {
            key = "item." + MODID + ".bundle.empty";
        } else if (full) {
            key = "item." + MODID + ".bundle.full";
        }
        if (key != null) {
            FontRenderer fr = GuiDraw.fontRenderer;
            String s = I18n.format(key);
            int tw = fr.getStringWidth(s);
            fr.drawStringWithShadow(s,
                    x + (barW - tw) / 2,
                    y + (BORD_H - fr.FONT_HEIGHT) / 2 + 1,
                    0xFFFFFFFF);
        }
    }

    /**
     * <p>
     * Draw a texture with CatFrame's mcmeta-driven auto-stretching, falling back to
     * the given parameters when the texture has no metadata.<br>
     * 使用 CatFrame 的 mcmeta 驱动的自动拉伸绘制纹理；纹理缺少元数据时回退到给定参数。
     * </p>
     *
     * @param texture      texture resource / 纹理资源
     * @param x            screen X / 屏幕 X
     * @param y            screen Y / 屏幕 Y
     * @param w            target width / 目标宽度
     * @param h            target height / 目标高度
     * @param fallbackType fallback stretch type / 回退拉伸类型
     * @param fallbackW    fallback texture width / 回退纹理宽度
     * @param fallbackH    fallback texture height / 回退纹理高度
     * @param fallbackL    fallback left edge / 回退左边缘
     * @param fallbackT    fallback top edge / 回退上边缘
     * @param fallbackR    fallback right edge / 回退右边缘
     * @param fallbackB    fallback bottom edge / 回退下边缘
     */
    private static void drawAuto(ResourceLocation texture, int x, int y, int w, int h,
            TextureStretching.StretchType fallbackType,
            int fallbackW, int fallbackH,
            int fallbackL, int fallbackT, int fallbackR, int fallbackB) {
        TextureStretching.drawAuto(texture, x, y, w, h,
                fallbackType, fallbackW, fallbackH,
                fallbackL, fallbackT, fallbackR, fallbackB);
    }
}
