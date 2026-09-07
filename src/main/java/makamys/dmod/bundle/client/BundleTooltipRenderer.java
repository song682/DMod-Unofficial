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
 *   <li>Stacks are drawn left-to-right, top-to-bottom, mirroring the 1.21
 *       {@code ClientBundleTooltip} layout: the most recently inserted stack lives
 *       at the top-left slot. The highlighted slot follows the bundle's selected
 *       index ({@code Sel}): when a selection exists the selected stack is
 *       highlighted, otherwise the top-left (newest) stack keeps the highlight.</li>
 *   <li>When {@code n <= 12}, every stack is shown. Otherwise the grid always
 *       fills its 12 cells with the 11 newest stacks, reserving the bottom-right
 *       cell for a {@code "+N"} overflow counter (N = total item count of the
 *       folded stacks).</li>
 *   <li>A capacity bar lives below the grid, blue while filling, red when full,
 *       with an "Empty"/"Full" hint text painted at the extremes. The bar fill is
 *       inset 1px from each end so its rounded caps stay inside the frame's caps.</li>
 * </ul>
 * Textures live in {@code assets/dmod/textures/gui/container/}, each paired with a
 * {@code .mcmeta} file whose {@code stretching} metadata drives CatFrame's
 * {@link decok.dfcdvadstf.catframe.ui.util.TextureStretching#drawAuto}:
 * <pre>
 * item_background.png            24x24  static(24)        - slot background
 * item_background_highlighted.png 24x24  static(24)        - highlighted slot (top-left)
 * filled_bar_boader.png          12x12  nine_patch(edge 3) - hollow bar frame (caps + hairlines)
 * filled_bar_half_filled.png     6x6    nine_patch(edge 2) - bar fill, not full (blue)
 * filled_bar_filled.png          6x6    nine_patch(edge 2) - bar fill, full (red)
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
    // Bar border is a 12x12 nine-patch (edge 3), the old 1px transparent rim trimmed
    // away; drawn 14px tall, the 6px inner band tiles seamlessly (CatFrame drives
    // the stretch from the .mcmeta, so these BORD_* values only feed the fallback).
    // 边框：12x12 九宫格(edge 3)，已去掉原透明边；目标高 14px，中部 6px 无缝平铺。
    private static final int BORD_W = 12, BORD_H = 14;
    // Bar fill is a 6x6 nine-patch (edge 2), blue (#5555FF) or red (#FF5555 when
    // full); drawn 12px tall at a 1px vertical offset so it sits inside the frame.
    private static final int FILL_H = 12;
    // Vertical offset of the fill relative to the border.
    private static final int FILL_Y_OFFSET = 1;
    // Fill inset from each end of the frame (1.21: PROGRESSBAR_BORDER=1 /
    // PROGRESSBAR_FILL_MAX=94): the rounded caps stay inside the frame's caps.
    // 填充条左右端相对边框各内缩 1px，端帽不再盖住边框端帽。
    private static final int FILL_X_INSET = 1;

    private final List<ItemStack> inventory;
    private final int occupancy;
    private final int displayCount;
    private final int overflow;
    private final int rows;
    private final boolean isEmpty;

    /**
     * 袋子本体引用：draw 时实时读取 {@code Sel}，滚轮切换后高亮即时跟随，不依赖
     * tooltip handler 的重新构建时机。
     * <p>The bundle stack itself: {@code Sel} is read live in draw(), so the
     * highlight follows wheel switches immediately, independent of when the
     * tooltip handler is rebuilt.
     */
    private final ItemStack bundleStack;

    public BundleTooltipRenderer(List<ItemStack> stacks, int occupancy, ItemStack bundleStack) {
        this.inventory = stacks;
        this.occupancy = occupancy;
        this.bundleStack = bundleStack;
        this.isEmpty = stacks.isEmpty();

        int n = stacks.size();
        // 复刻 1.21 溢出布局：>12 个条目时网格固定 12 格（11 个 + 右下 "+N"）。
        // "+N" 计折叠条目的物品数量总和（现代语义，而非隐藏条数）。
        this.displayCount = BundleContents.getDisplayCount(n);
        this.overflow = n > this.displayCount
                ? this.inventory.subList(this.displayCount, n).stream()
                        .mapToInt(s -> s.stackSize).sum()
                : 0;

        int cells = displayCount + (overflow > 0 ? 1 : 0);
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
        // 高亮跟随选中索引（Sel）：已选中时高亮选中格子；未选中时保持左上角
        // （最新插入）高亮。选中项被折叠到显示区外时自然不出现高亮。
        // Highlight follows the selected index (Sel); without a selection the
        // top-left (newest) slot stays highlighted. A selection folded out of
        // the visible grid simply shows no highlight.
        int selected = BundleContents.getSelectedIndex(bundleStack);

        for (int i = 0; i < totalCells; i++) {
            // i=0 is the top-left cell (the most recently inserted stack).
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int sx = x + col * SLOT_W;
            int sy = y + row * SLOT_H;

            // The "+N" overflow cell is text-only: no slot background behind it.
            // "+N" 溢出格不绘制 item background，只显示数字。
            boolean isOverflowCell = overflow > 0 && i == cells - 1;
            boolean highlighted;
            if (selected >= 0 && selected < inventory.size()) {
                highlighted = i == selected;
            } else {
                highlighted = i == 0 && displayCount > 0;
            }
            if (!isOverflowCell) {
                drawAuto(highlighted ? TEX_SLOT_HIGHLIGHTED : TEX_SLOT,
                        sx, sy, SLOT_W, SLOT_H,
                        TextureStretching.StretchType.STATIC, SLOT_W, SLOT_H, 0, 0, 0, 0);
            }

            if (i >= cells) continue;

            if (isOverflowCell) {
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
                TextureStretching.StretchType.NINE_PATCH, BORD_W, BORD_H, 3, 3, 3, 3);

        // 2) Fill scaled by occupancy / CAPACITY, inset 1px from each end of the
        //    frame (1.21: PROGRESSBAR_BORDER=1 / PROGRESSBAR_FILL_MAX=94), so the
        //    nine-patch caps never cover the frame's own end caps.
        //    填充：长度按容量比例缩放，左右各内缩 1px（对齐 1.21），圆角帽始终
        //    保持在边框端帽之内（未满蓝色 / 满红色）。
        boolean full = occupancy >= CAPACITY;
        int fillMax = barW - 2 * FILL_X_INSET;
        int fillPx = (int) Math.round(fillMax * (Math.min(occupancy, CAPACITY) / (double) CAPACITY));
        if (fillPx < 0) fillPx = 0;
        if (fillPx > fillMax) fillPx = fillMax;
        if (fillPx > 0) {
            drawAuto(full ? TEX_BAR_FILLED : TEX_BAR_HALF_FILLED,
                    x + FILL_X_INSET, y + FILL_Y_OFFSET, fillPx, FILL_H,
                    TextureStretching.StretchType.NINE_PATCH, 6, 6, 2, 2, 2, 2);
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
