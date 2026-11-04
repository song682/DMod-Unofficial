package makamys.dmod.mixin.middle;

import static makamys.dmod.init.DModConstants.LOGGER;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import makamys.dmod.future.item.IItemFuture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

@Mixin(Container.class)
public class MixinContainer {

    // See https://wiki.vg/Protocol#Click_Window
    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void preSlotClick(int slotNumber, int button, int modifier, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        Container container = (Container) (Object) this;
        boolean eventconsumed = false;
        if (modifier == 0 || modifier == 5) {
            if (modifier == 5) {
                switch (button) {
                case 1:
                    button = 0;
                    break;
                case 5:
                    button = 1;
                    break;
                case 9:
                    button = 2;
                    break;
                default:
                    button = -1;
                }
            }
            if (button >= 0 && button <= 2 && slotNumber >= 0) {
                if (slotNumber < container.inventorySlots.size()) {
                    Slot slot = container.getSlot(slotNumber);
                    ItemStack cursor = player.inventory.getItemStack();

                    ItemStack stack = slot.getStack();
                    if (cursor != null && cursor.getItem() instanceof IItemFuture) {
                        eventconsumed |= ((IItemFuture) cursor.getItem()).onStackClicked(cursor, slot, button, player);
                    } else if (stack != null && stack.getItem() instanceof IItemFuture) {
                        eventconsumed |= ((IItemFuture) stack.getItem()).onClicked(stack, cursor, slot, button, player);
                    }
                    if (eventconsumed) {
                        slot.onSlotChanged();
                    }
                } else {
                    LOGGER.warn("Invalid index in MixinContainer.preSlotClick: slotNumber=" + slotNumber + ", but container " + container + " only has " + container.inventorySlots.size() + " slots.");
                    // TODO figure out why this can happen. (TrashSlot?)
                }
            }
        }
        if (eventconsumed) {
            cir.setReturnValue(null);
        }
    }
}
