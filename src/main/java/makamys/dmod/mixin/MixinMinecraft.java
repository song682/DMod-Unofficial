package makamys.dmod.mixin;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import makamys.dmod.bundle.BundleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.ForgeHooksClient;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    /**
     * 在鼠标事件分发链的最前端拦截滚轮：Minecraft.runTick 的鼠标循环里，
     * ForgeHooksClient.postMouseEvent() 是第一个消费点（其后依次是快捷栏切换
     * changeCurrentItem、currentScreen.handleMouseInput、fireMouseInput）。
     * 当鼠标悬停在容器 GUI 的收纳袋格子上滚动滚轮时，由 tryScrollSlot 独立完成
     * 切换，然后返回 true 令循环 continue——整个事件被吞掉，Forge MouseEvent
     * 总线（NEI 物品列表翻页等）、快捷栏、handleMouseInput 及其注入方全部收不到
     * 该事件，从而消除"滚动袋子时其它区域跟着滚动"的副作用。
     * <p>Intercepts the wheel at the very front of the mouse dispatch chain: in
     * the mouse loop of Minecraft#runTick, ForgeHooksClient.postMouseEvent() is
     * the first consumer (followed by the changeCurrentItem hotbar switch,
     * currentScreen.handleMouseInput and fireMouseInput). When the wheel is
     * scrolled over a bundle slot of a container GUI, tryScrollSlot performs the
     * switch and returning true makes the loop continue, swallowing the whole
     * event: the Forge MouseEvent bus (NEI item list paging, ...), the hotbar,
     * handleMouseInput and its injectors never see it, so other regions no
     * longer scroll along with the bundle.
     */
    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;postMouseEvent()Z"))
    private boolean dmod$redirectPostMouseEvent() {
        Minecraft mc = (Minecraft) (Object) this;
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0 && mc.currentScreen instanceof GuiContainer) {
            Slot slot = ((AccessorGuiContainer) mc.currentScreen).getHoveredSlot();
            if (BundleItem.tryScrollSlot(slot, dWheel)) {
                return true;
            }
        }
        return ForgeHooksClient.postMouseEvent();
    }
}
