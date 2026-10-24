package makamys.dmod.content.village.inventory.gui;

import java.util.Map;
import java.util.Random;

import decok.dfcdvadstf.catframe.ui.screens.container.AbstractContainerMenu;
import decok.dfcdvadstf.catframe.ui.screens.container.Container;
import decok.dfcdvadstf.catframe.ui.screens.container.SimpleContainer;
import makamys.dmod.DModBlocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Grindstone menu — ported from 26.1.2 {@code GrindstoneMenu}, built on
 * CatFrame's {@link AbstractContainerMenu} (the data / menu / screen split).
 * <p>
 * Two input slots accept damageable or enchanted items; the result slot merges
 * durability (+5 % of the larger maximum) or strips enchantments (an enchanted
 * book turns back into a plain book), and awards experience equal to the
 * removed enchantments' minimum enchanting cost.
 */
public class GridStoneMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;

    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;

    private final Container repairSlots;
    private final Container resultSlots;
    private final World world;
    private final int posX;
    private final int posY;
    private final int posZ;
    private final Random rand = new Random();

    public GridStoneMenu(InventoryPlayer playerInventory, World world, int x, int y, int z) {
        // The input container is created before super() runs, so it cannot call
        // back into the menu; the result is refreshed after every click and on
        // each tick instead (equivalent to the high-version "recompute on
        // container change" contract).
        super(new SimpleContainer(2));

        this.repairSlots = this.container;
        this.resultSlots = new SimpleContainer(1);
        this.world = world;
        this.posX = x;
        this.posY = y;
        this.posZ = z;

        this.addSlotToContainer(new Slot(this.repairSlots, INPUT_SLOT, 49, 19) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return canGrind(stack);
            }
        });
        this.addSlotToContainer(new Slot(this.repairSlots, ADDITIONAL_SLOT, 49, 40) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return canGrind(stack);
            }
        });
        this.addSlotToContainer(new Slot(this.resultSlots, 0, 129, 34) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }

            @Override
            public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
                GridStoneMenu.this.onResultTaken(player);
                super.onPickupFromSlot(player, stack);
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    // ==================== Result computation ====================

    private void createResult() {
        this.resultSlots.setItem(0, computeResult(this.repairSlots.getItem(INPUT_SLOT),
                this.repairSlots.getItem(ADDITIONAL_SLOT)));
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.createResult();
    }

    @Override
    public ItemStack slotClick(int slotIndex, int button, int mode, EntityPlayer player) {
        ItemStack result = super.slotClick(slotIndex, button, mode, player);
        this.createResult();
        return result;
    }

    private ItemStack computeResult(ItemStack input, ItemStack additional) {
        boolean hasAnItem = input != null || additional != null;
        if (!hasAnItem) {
            return null;
        }
        if (getStackSize(input) <= 1 && getStackSize(additional) <= 1) {
            boolean hasBothItems = input != null && additional != null;
            if (!hasBothItems) {
                ItemStack item = input != null ? input : additional;
                return getEnchantments(item).isEmpty() ? null : removeEnchantsFrom(item.copy());
            }
            return mergeItems(input, additional);
        }
        return null;
    }

    private ItemStack mergeItems(ItemStack input, ItemStack additional) {
        if (input.getItem() != additional.getItem()) {
            return null;
        }

        int durability = Math.max(input.getMaxDamage(), additional.getMaxDamage());
        int remaining1 = input.getMaxDamage() - input.getItemDamage();
        int remaining2 = additional.getMaxDamage() - additional.getItemDamage();
        int remaining = remaining1 + remaining2 + durability * 5 / 100;

        int count = 1;
        if (!input.isItemStackDamageable()) {
            if (input.getMaxStackSize() < 2 || !areStacksEqual(input, additional)) {
                return null;
            }
            count = 2;
        }

        ItemStack newItem = input.copy();
        newItem.stackSize = count;
        if (newItem.isItemStackDamageable()) {
            newItem.setItemDamage(Math.max(durability - remaining, 0));
        }

        mergeEnchantsFrom(newItem, additional);
        return removeEnchantsFrom(newItem);
    }

    private void mergeEnchantsFrom(ItemStack target, ItemStack source) {
        for (Map.Entry<Integer, Integer> entry : getEnchantments(source).entrySet()) {
            Enchantment enchantment = Enchantment.enchantmentsList[entry.getKey()];
            int level = entry.getValue();
            if (enchantment != null && EnchantmentHelper.getEnchantmentLevel(enchantment.effectId, target) < level) {
                target.addEnchantment(enchantment, level);
            }
        }
    }

    /**
     * Strips every non-curse enchantment (1.7.10 has no curses, so that is all
     * of them), turns an emptied enchanted book back into a plain book, and
     * resets the repair cost — matching {@code GrindstoneMenu#removeNonCursesFrom}.
     */
    private ItemStack removeEnchantsFrom(ItemStack item) {
        ItemStack result = item.copy();

        if (result.stackTagCompound != null) {
            result.stackTagCompound.removeTag("ench");
            result.stackTagCompound.removeTag("StoredEnchantments");
            if (result.stackTagCompound.hasNoTags()) {
                result.stackTagCompound = null;
            }
        }

        if (result.getItem() == Items.enchanted_book) {
            ItemStack book = new ItemStack(Items.book, result.stackSize, 0);
            book.stackTagCompound = result.stackTagCompound;
            result = book;
        }

        // Every remaining enchantment count is zero here, so the anvil repair
        // cost curve always resolves to zero.
        result.setRepairCost(0);
        return result;
    }

    // ==================== Result taking ====================

    private void onResultTaken(EntityPlayer player) {
        int amount = getExperienceFromItem(this.repairSlots.getItem(INPUT_SLOT))
                + getExperienceFromItem(this.repairSlots.getItem(ADDITIONAL_SLOT));

        if (amount > 0) {
            int halfAmount = (int) Math.ceil(amount / 2.0D);
            int experience = halfAmount + this.rand.nextInt(halfAmount);
            if (experience > 0 && !player.worldObj.isRemote) {
                player.worldObj.spawnEntityInWorld(new EntityXPOrb(player.worldObj,
                        this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D, experience));
            }
        }

        this.repairSlots.setItem(INPUT_SLOT, null);
        this.repairSlots.setItem(ADDITIONAL_SLOT, null);
    }

    private int getExperienceFromItem(ItemStack stack) {
        int amount = 0;
        for (Map.Entry<Integer, Integer> entry : getEnchantments(stack).entrySet()) {
            Enchantment enchantment = Enchantment.enchantmentsList[entry.getKey()];
            if (enchantment != null) {
                amount += enchantment.getMinEnchantability(entry.getValue());
            }
        }
        return amount;
    }

    // ==================== Slot transfer ====================

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        ItemStack clicked = null;
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);

        if (slot != null && slot.getHasStack()) {
            ItemStack item = slot.getStack();
            clicked = item.copy();

            ItemStack input = this.repairSlots.getItem(INPUT_SLOT);
            ItemStack additional = this.repairSlots.getItem(ADDITIONAL_SLOT);

            if (slotIndex == RESULT_SLOT) {
                if (!this.mergeItemStack(item, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
                    return null;
                }
                slot.onSlotChange(item, clicked);
            } else if (slotIndex != INPUT_SLOT && slotIndex != ADDITIONAL_SLOT) {
                if (input != null && additional != null) {
                    if (slotIndex >= INV_SLOT_START && slotIndex < INV_SLOT_END) {
                        if (!this.mergeItemStack(item, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                            return null;
                        }
                    } else if (slotIndex >= USE_ROW_SLOT_START && slotIndex < USE_ROW_SLOT_END
                            && !this.mergeItemStack(item, INV_SLOT_START, INV_SLOT_END, false)) {
                        return null;
                    }
                } else if (!this.mergeItemStack(item, INPUT_SLOT, ADDITIONAL_SLOT + 1, false)) {
                    return null;
                }
            } else if (!this.mergeItemStack(item, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                return null;
            }

            if (item.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }

            if (item.stackSize == clicked.stackSize) {
                return null;
            }

            slot.onPickupFromSlot(player, item);
        }

        return clicked;
    }

    // ==================== Lifecycle ====================

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);

        if (!player.worldObj.isRemote) {
            for (int i = 0; i < this.repairSlots.getSizeInventory(); i++) {
                ItemStack stack = this.repairSlots.removeItemNoUpdate(i);
                if (stack != null) {
                    player.dropPlayerItemWithRandomChoice(stack, false);
                }
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (this.world.getBlock(this.posX, this.posY, this.posZ) != DModBlocks.gridstone) {
            return false;
        }
        return player.getDistanceSq(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D) <= 64.0D;
    }

    // ==================== Helpers ====================

    private static boolean canGrind(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return stack.isItemStackDamageable() || !getEnchantments(stack).isEmpty();
    }

    private static int getStackSize(ItemStack stack) {
        return stack == null ? 0 : stack.stackSize;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> getEnchantments(ItemStack stack) {
        if (stack == null) {
            return java.util.Collections.emptyMap();
        }
        // EnchantmentHelper handles the StoredEnchantments list of enchanted books.
        return EnchantmentHelper.getEnchantments(stack);
    }

    private static boolean areStacksEqual(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem()
                && a.getItemDamage() == b.getItemDamage()
                && ItemStack.areItemStackTagsEqual(a, b);
    }
}
