package makamys.dmod.mixin;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.dmod.bundle.BundleItem;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

@Mixin(GuiScreen.class)
public class MixinGuiScreen {

    /**
     * 鼠标悬停在收纳袋格子上滚动滚轮 → 循环切换选中索引（打开状态袋口展示的物品随之
     * 改变）。注入于 handleMouseInput 末尾：1.7.10 的容器 GUI 中滚轮无原版用途
     * （滚轮切快捷栏仅在世界内生效），且 drawScreen 每帧刷新 theSlot（悬停格子）。
     * 切换后调用
     * slot.onSlotChanged()，由 Container.detectAndSendChanges 将 NBT 同步到服务端。
     * <p>Scroll wheel on a bundle slot cycles the selected index (the item shown at
     * the bundle mouth when open). Injected at RETURN of GuiScreen#handleMouseInput:
     * vanilla 1.7.10 has no wheel handling in container GUIs (the wheel switches the
     * hotbar only while no screen is open), and drawScreen refreshes theSlot (the
     * hovered slot) every frame. The NBT change is propagated to the server via
     * slot.onSlotChanged()
     * + Container.detectAndSendChanges.
     */
    @Inject(method = "handleMouseInput", at = @At("RETURN"))
    private void dmod$scrollBundleSelection(CallbackInfo ci) {
        if (!((Object) this instanceof GuiContainer)) {
            return;
        }
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0) {
            return;
        }
        Slot slot = ((AccessorGuiContainer) (Object) this).getHoveredSlot();
        if (slot == null) {
            return;
        }
        ItemStack stack = slot.getStack();
        if (stack != null && stack.getItem() instanceof BundleItem
                && BundleItem.scrollSelectedIndex(stack, dWheel)) {
            slot.onSlotChanged();
        }
    }
}
