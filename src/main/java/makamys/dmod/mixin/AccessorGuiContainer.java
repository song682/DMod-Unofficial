package makamys.dmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

/**
 * Accessor interface exposing GuiContainer's theSlot field (1.7.10 MCP name;
 * srg field_147006_u) — the slot hovered by the cursor, refreshed by
 * updateScreen every frame.
 * <p>
 * 暴露 GuiContainer 的 theSlot 字段（1.7.10 MCP 名；srg field_147006_u）——
 * 鼠标悬停的格子，由 updateScreen 每帧刷新。
 */
@Mixin(GuiContainer.class)
public interface AccessorGuiContainer {

    /**
     * Returns the slot the cursor is currently hovering, or null if none.
     * <p>
     * 返回鼠标当前悬停的格子；无悬停时返回 null。
     */
    @Accessor("theSlot")
    Slot getHoveredSlot();
}
