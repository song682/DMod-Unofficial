package makamys.dmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import makamys.dmod.bundle.BundleItem;
import net.minecraft.client.gui.inventory.GuiContainer;

/**
 * 收纳袋选择（Sel）的兜底清理：选择只在光标悬停期间有效，不允许跨 GUI 会话保留。
 * 在 GUI 初始化（打开/窗口尺寸变化）与关闭时，无条件清除界面内所有收纳袋的 Sel，
 * 防止上一次会话写入的 Sel 残留在 NBT/存档中（例如未移开光标就关界面或退出游戏，
 * 逐帧的离开清除没有执行机会），导致下次打开界面悬停时 tooltip 显示旧的高亮。
 * <p>Fail-safe purge of bundle selections (Sel): the selection is only valid
 * while hovering and must not survive across screen sessions. Clears the Sel
 * of every bundle in the screen when the GUI is initialized (open / window
 * resize) and when it is closed, so a Sel written in the previous session
 * cannot linger in NBT/saves (e.g. when the GUI is closed or the game quits
 * while the cursor still rests on the bundle, the per-tick leave-clear never
 * gets a chance to run) and show a stale tooltip highlight on the next open.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiContainer.class)
public class MixinGuiContainer {

    /**
     * 界面打开（含窗口尺寸变化触发的重建）时清除残留选择。
     * GuiContainerCreative.initGui 等子类覆写均调用 super，注入点必达。
     * <p>Purge leftover selections when the screen opens (also on window-resize
     * rebuilds). Subclass overrides like GuiContainerCreative.initGui all call
     * super, so the injection always fires.
     */
    @SideOnly(Side.CLIENT)
    @Inject(method = "initGui", at = @At("RETURN"))
    private void dmod$purgeBundleSelectionsOnOpen(CallbackInfo ci) {
        BundleItem.clearAllSelections((GuiContainer) (Object) this);
    }

    /**
     * 界面关闭时清除选择，避免带着 Sel 写入存档。
     * <p>Purge selections when the screen closes, so no Sel is saved to disk.
     */
    @SideOnly(Side.CLIENT)
    @Inject(method = "onGuiClosed", at = @At("RETURN"))
    private void dmod$purgeBundleSelectionsOnClose(CallbackInfo ci) {
        BundleItem.clearAllSelections((GuiContainer) (Object) this);
    }
}
