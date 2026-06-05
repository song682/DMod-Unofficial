package makamys.dmod.client.tooltip;

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
import makamys.dmod.item.ItemBundle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Modern (1.21+) bundle tooltip renderer.
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
 * Texture layout comes from {@code assets/dmod/textures/gui/container/new_bundle.png} (128x128):
 * <pre>
 * |--24--|--24--|
 * |TOOLTIPS_BACK|TOOLTIPS_HIGHLIGHTED|
 * |--8--|--8--|
 * |CONTAIN_BAR_FILLED|CONTAIN_BAR_FULL|
 * |--14--|
 * |CONTAIN_BAR_BORDER|
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class ModernBundleTooltipHandler implements ITooltipLineHandler {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MODID, "textures/gui/container/new_bundle.png");

    // Sprite coordinates on new_bundle.png
    private static final int SLOT_W = 24, SLOT_H = 24;
    private static final int BACK_U = 0,  BACK_V = 0;
    private static final int HILI_U = 24, HILI_V = 0;
    private static final int FILL_U = 0,  FILL_V = 24, FILL_W = 8, FILL_H = 8;
    private static final int FULL_U = 8,  FULL_V = 24;
    private static final int BORD_U = 0,  BORD_V = 32, BORD_W = 14, BORD_H = 14;

    // Grid layout
    private static final int COLUMNS = 4;
    private static final int MAX_ROWS = 3;
    private static final int CAPACITY = ItemBundle.MAX_STORAGE;

    // Outer padding / gaps
    private static final int PAD = 2;
    private static final int BAR_GAP = 2;
    // Vertical inset of the fill sprite within the 14px border band
    private static final int BAR_INNER_PAD_X = 1;
    private static final int BAR_INNER_PAD_Y = 3;

    private final List<ItemStack> inventory;
    private final int occupancy;
    private final int displayCount;
    private final int overflow;
    private final int rows;
    private final boolean isEmpty;

    public ModernBundleTooltipHandler(List<ItemStack> stacks, int occupancy) {
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
            drawSprite(sx, sy,
                    isTopStack ? HILI_U : BACK_U,
                    isTopStack ? HILI_V : BACK_V,
                    SLOT_W, SLOT_H);

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

        // 1) 3-slice border: left cap + stretched middle + right cap.
        //    BORDER is 14x14; we split it in half horizontally for the caps,
        //    and stretch a 1-pixel column from the seam across the remaining middle.
        int capW = BORD_W / 2; // 7
        // left cap (left half of BORDER)
        drawSprite(x, y, BORD_U, BORD_V, capW, BORD_H);
        // right cap (right half of BORDER)
        drawSprite(x + barW - capW, y, BORD_U + capW, BORD_V, capW, BORD_H);
        // middle: stretch a 1-px vertical slice across the gap
        int midStart = x + capW;
        int midEnd = x + barW - capW;
        if (midEnd > midStart) {
            drawStretched(midStart, y,
                    BORD_U + capW, BORD_V, 1, BORD_H,
                    midEnd - midStart, BORD_H);
        }

        // 2) fill according to occupancy / CAPACITY (3-slice).
        //    FULL: full 3-slice (left cap + stretched middle + right cap).
        //    FILLED (not at capacity): left cap + stretched middle, **no right cap**
        //    so the bar visually "bleeds" to the right instead of looking finished.
        boolean full = occupancy >= CAPACITY;
        int innerW = barW - BAR_INNER_PAD_X * 2;
        int fillPx = (int) Math.round(innerW * (Math.min(occupancy, CAPACITY) / (double) CAPACITY));
        if (fillPx < 0) fillPx = 0;
        if (fillPx > innerW) fillPx = innerW;
        if (fillPx > 0) {
            int fillU = full ? FULL_U : FILL_U;
            int fillV = full ? FULL_V : FILL_V;
            int fCapW = FILL_W / 2; // 4
            int fx = x + BAR_INNER_PAD_X;
            int fy = y + BAR_INNER_PAD_Y;

            // Left cap (clipped when fillPx is smaller than the cap).
            int leftW = Math.min(fCapW, fillPx);
            drawSprite(fx, fy, fillU, fillV, leftW, FILL_H);

            if (full) {
                // FULL: both caps + stretched middle. Right cap may be clipped if
                // the bar is narrow (defensive - normally fillPx == innerW here).
                if (fillPx > fCapW) {
                    int rightCapW = Math.min(fCapW, fillPx - fCapW);
                    drawSprite(fx + fillPx - rightCapW, fy,
                            fillU + (FILL_W - rightCapW), fillV,
                            rightCapW, FILL_H);
                    int midW = fillPx - fCapW - rightCapW;
                    if (midW > 0) {
                        drawStretched(fx + fCapW, fy,
                                fillU + fCapW, fillV, 1, FILL_H,
                                midW, FILL_H);
                    }
                }
            } else {
                // FILLED: no right cap. Middle stretches from the cap to the
                // current fill extent.
                int midW = fillPx - fCapW;
                if (midW > 0) {
                    drawStretched(fx + fCapW, fy,
                            fillU + fCapW, fillV, 1, FILL_H,
                            midW, FILL_H);
                }
            }
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

    private static void drawSprite(int x, int y, int u, int v, int w, int h) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GuiDraw.changeTexture(TEXTURE);
        GuiDraw.drawTexturedModalRect(x, y, u, v, w, h);
    }

    /**
     * Samples a sub-rectangle (u, v, srcW, srcH) and stretches it into a screen rect of
     * size (dstW, dstH). Used for 3-slice borders where the middle needs to stretch,
     * and for solid-color fills where tiling vs stretching is visually identical.
     */
    private static void drawStretched(int x, int y, int u, int v, int srcW, int srcH, int dstW, int dstH) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GuiDraw.changeTexture(TEXTURE);
        final float px = 1.0F / 256.0F;
        float uMin = u * px;
        float uMax = (u + srcW) * px;
        float vMin = v * px;
        float vMax = (v + srcH) * px;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x,        y + dstH, 0, uMin, vMax);
        t.addVertexWithUV(x + dstW, y + dstH, 0, uMax, vMax);
        t.addVertexWithUV(x + dstW, y,        0, uMax, vMin);
        t.addVertexWithUV(x,        y,        0, uMin, vMin);
        t.draw();
    }
}
