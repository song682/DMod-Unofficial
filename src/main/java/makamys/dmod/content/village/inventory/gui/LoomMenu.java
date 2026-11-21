package makamys.dmod.content.village.inventory.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import decok.dfcdvadstf.catframe.ui.screens.container.AbstractContainerMenu;
import decok.dfcdvadstf.catframe.ui.screens.container.Container;
import decok.dfcdvadstf.catframe.ui.screens.container.SimpleContainer;
import decok.dfcdvadstf.catframe.ui.screens.container.data.ContainerData;
import decok.dfcdvadstf.catframe.ui.screens.container.data.SimpleContainerData;
import ganymedes01.etfuturum.ModBlocks;
import ganymedes01.etfuturum.blocks.itemblocks.ItemBlockBanner;
import ganymedes01.etfuturum.lib.EnumColor;
import ganymedes01.etfuturum.recipes.ModRecipes;
import ganymedes01.etfuturum.tileentities.TileEntityBanner;
import ganymedes01.etfuturum.tileentities.TileEntityBanner.EnumBannerPattern;
import makamys.dmod.init.DModBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Loom menu — ported from 26.1.2 {@code LoomMenu}, built on CatFrame's
 * {@link AbstractContainerMenu} (the data / menu / screen split).
 * <p>
 * Three inputs (banner, dye, banner pattern item) produce a banner with one
 * extra pattern layer in the result slot. Taking the result consumes one
 * banner and one dye. The selected pattern index is synced to the client
 * through a {@link ContainerData} slot, and pattern selection arrives from
 * the screen via {@link #enchantItem(EntityPlayer, int)}.
 * </p>
 * <p>
 * Banner data mirrors EFR's format: the layers live in
 * {@code BlockEntityTag.Patterns} as {@code {Pattern, Color}} compounds where
 * {@code Color} is the wool/dye-order index ({@code ~dyeDamage & 15}).
 * Patterns that require a banner pattern item are not selectable yet because
 * the pattern item is not implemented.
 * </p>
 */
public class LoomMenu extends AbstractContainerMenu {

    public static final int BANNER_SLOT = 0;
    public static final int DYE_SLOT = 1;
    public static final int PATTERN_SLOT = 2;
    public static final int RESULT_SLOT = 3;

    private static final int INV_SLOT_START = 4;
    private static final int INV_SLOT_END = 31;
    private static final int USE_ROW_SLOT_START = 31;
    private static final int USE_ROW_SLOT_END = 40;

    /** Matches the high-version cap of six pattern layers per banner. */
    public static final int MAX_PATTERN_LAYERS = 6;

    /** Patterns that can be applied without a banner pattern item. */
    private static final List<EnumBannerPattern> SELECTABLE_PATTERNS = buildSelectablePatterns();

    private final Container inputContainer;
    private final Container resultContainer;
    private final ContainerData selectedPatternIndex = new SimpleContainerData(1);

    private final World world;
    private final int posX;
    private final int posY;
    private final int posZ;

    private final Slot bannerSlot;
    private final Slot dyeSlot;
    private final Slot patternSlot;
    private final Slot resultSlot;

    public LoomMenu(InventoryPlayer playerInventory, World world, int x, int y, int z) {
        super(new SimpleContainer(3));

        this.inputContainer = this.container;
        this.resultContainer = new SimpleContainer(1);
        this.world = world;
        this.posX = x;
        this.posY = y;
        this.posZ = z;

        ((SimpleContainer) this.inputContainer).setCustomName(StatCollector.translateToLocal("container.loom"));

        this.bannerSlot = this.addSlotToContainer(new Slot(this.inputContainer, BANNER_SLOT, 13, 26) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return isBannerItem(stack);
            }
        });
        this.dyeSlot = this.addSlotToContainer(new Slot(this.inputContainer, DYE_SLOT, 33, 26) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return getDyeDamage(stack) >= 0;
            }
        });
        this.patternSlot = this.addSlotToContainer(new Slot(this.inputContainer, PATTERN_SLOT, 23, 45) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                // Banner pattern items are not implemented yet, so the slot is
                // display-only and every selectable pattern is item-free.
                return false;
            }
        });
        this.resultSlot = this.addSlotToContainer(new Slot(this.resultContainer, 0, 143, 57) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }

            @Override
            public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
                LoomMenu.this.consumeInputs();
                super.onPickupFromSlot(player, stack);
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addDataSlot(this.selectedPatternIndex);
        this.selectedPatternIndex.set(0, -1);
    }

    // ==================== Pattern list ====================

    private static List<EnumBannerPattern> buildSelectablePatterns() {
        List<EnumBannerPattern> patterns = new ArrayList<EnumBannerPattern>();
        for (EnumBannerPattern pattern : EnumBannerPattern.VALUES) {
            if (pattern == EnumBannerPattern.BASE || pattern.hasCraftingStack()) {
                continue;
            }
            patterns.add(pattern);
        }
        return Collections.unmodifiableList(patterns);
    }

    /**
     * @return the patterns currently selectable in the GUI; empty until both
     *         the banner and dye inputs are present
     */
    public List<EnumBannerPattern> getSelectablePatterns() {
        if (this.bannerSlot.getHasStack() && this.dyeSlot.getHasStack()
                && !this.patternSlot.getHasStack()) {
            return SELECTABLE_PATTERNS;
        }
        return Collections.emptyList();
    }

    public int getSelectedBannerPatternIndex() {
        return this.selectedPatternIndex.get(0);
    }

    /**
     * Client-side visual selection; the authoritative selection is applied by
     * {@link #enchantItem(EntityPlayer, int)} on the server.
     */
    public void selectPattern(int index) {
        this.selectedPatternIndex.set(0, index);
    }

    /**
     * @return the dye-order name used in the {@code item.banner.<pattern>.<dye>}
     *         tooltip key, defaulting to white when no dye is present
     */
    public String getPatternDyeName() {
        ItemStack dye = this.dyeSlot.getStack();
        int dyeDamage = dye != null ? getDyeDamage(dye) : 15;
        if (dyeDamage < 0) {
            dyeDamage = 15;
        }
        return ModRecipes.dye_names[~dyeDamage & 15];
    }

    // ==================== Result computation ====================

    /**
     * Recomputes the result slot from the current inputs and selection.
     * Mirrors 26.1.2 {@code LoomMenu#setupResultSlot}: the result is a copy of
     * the banner with one appended pattern layer.
     */
    private void updateResult() {
        ItemStack banner = this.bannerSlot.getStack();
        ItemStack dye = this.dyeSlot.getStack();
        boolean inputsReady = banner != null && dye != null;
        boolean hasMaxPatterns = inputsReady && TileEntityBanner.getPatterns(banner) >= MAX_PATTERN_LAYERS;
        int selected = this.selectedPatternIndex.get(0);

        if ((!inputsReady || hasMaxPatterns) && selected != -1) {
            this.selectedPatternIndex.set(0, -1);
            selected = -1;
        }

        ItemStack result = null;
        if (inputsReady && !hasMaxPatterns && selected >= 0 && selected < SELECTABLE_PATTERNS.size()
                && !this.patternSlot.getHasStack()) {
            int dyeDamage = getDyeDamage(dye);
            if (dyeDamage >= 0) {
                EnumBannerPattern pattern = SELECTABLE_PATTERNS.get(selected);
                result = banner.copy();
                result.stackSize = 1;
                NBTTagCompound blockEntityTag = ItemBlockBanner.getSubTag(result, "BlockEntityTag", true);
                NBTTagList patterns;
                if (blockEntityTag.hasKey("Patterns", 9)) {
                    patterns = blockEntityTag.getTagList("Patterns", 10);
                } else {
                    patterns = new NBTTagList();
                    blockEntityTag.setTag("Patterns", patterns);
                }
                NBTTagCompound layer = new NBTTagCompound();
                layer.setString("Pattern", pattern.getPatternID());
                layer.setInteger("Color", ~dyeDamage & 15);
                patterns.appendTag(layer);
            }
        }

        if (!ItemStack.areItemStacksEqual(result, this.resultContainer.getItem(0))) {
            this.resultContainer.setItem(0, result);
        }
    }

    /**
     * Consumes one banner and one dye after the result was taken, matching
     * 26.1.2 {@code LoomMenu} result-slot {@code onTake}.
     */
    private void consumeInputs() {
        this.inputContainer.removeItem(BANNER_SLOT, 1);
        this.inputContainer.removeItem(DYE_SLOT, 1);
        if (!this.bannerSlot.getHasStack() || !this.dyeSlot.getHasStack()) {
            this.selectedPatternIndex.set(0, -1);
        }
    }

    // ==================== Slot / data sync ====================

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.updateResult();
    }

    @Override
    public ItemStack slotClick(int slotIndex, int button, int mode, EntityPlayer player) {
        ItemStack result = super.slotClick(slotIndex, button, mode, player);
        if (!player.worldObj.isRemote) {
            this.updateResult();
        }
        return result;
    }

    /**
     * Pattern selection packet handler ({@code C11PacketEnchantItem}), the
     * 1.7.10 counterpart of 26.1.2 {@code LoomMenu#clickMenuButton}.
     */
    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (id >= 0 && id < this.getSelectablePatterns().size()) {
            this.selectedPatternIndex.set(0, id);
            this.updateResult();
            return true;
        }
        return false;
    }

    // ==================== Slot transfer ====================

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        ItemStack clicked = null;
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);

        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            clicked = stack.copy();

            if (slotIndex == RESULT_SLOT) {
                if (!this.mergeItemStack(stack, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
                    return null;
                }
                slot.onSlotChange(stack, clicked);
            } else if (slotIndex != BANNER_SLOT && slotIndex != DYE_SLOT && slotIndex != PATTERN_SLOT) {
                if (isBannerItem(stack)) {
                    if (!this.mergeItemStack(stack, BANNER_SLOT, BANNER_SLOT + 1, false)) {
                        return null;
                    }
                } else if (getDyeDamage(stack) >= 0) {
                    if (!this.mergeItemStack(stack, DYE_SLOT, DYE_SLOT + 1, false)) {
                        return null;
                    }
                } else if (slotIndex >= INV_SLOT_START && slotIndex < INV_SLOT_END) {
                    if (!this.mergeItemStack(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                        return null;
                    }
                } else if (slotIndex >= USE_ROW_SLOT_START && slotIndex < USE_ROW_SLOT_END
                        && !this.mergeItemStack(stack, INV_SLOT_START, INV_SLOT_END, false)) {
                    return null;
                }
            } else if (!this.mergeItemStack(stack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                return null;
            }

            if (stack.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }

            if (stack.stackSize == clicked.stackSize) {
                return null;
            }

            slot.onPickupFromSlot(player, stack);
        }

        return clicked;
    }

    // ==================== Lifecycle ====================

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);

        if (!player.worldObj.isRemote) {
            for (int i = 0; i < this.inputContainer.getSizeInventory(); i++) {
                ItemStack stack = this.inputContainer.removeItemNoUpdate(i);
                if (stack != null) {
                    player.dropPlayerItemWithRandomChoice(stack, false);
                }
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (this.world.getBlock(this.posX, this.posY, this.posZ) != DModBlocks.loom) {
            return false;
        }
        return player.getDistanceSq(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D) <= 64.0D;
    }

    // ==================== Accessors ====================

    public Slot getBannerSlot() {
        return this.bannerSlot;
    }

    public Slot getDyeSlot() {
        return this.dyeSlot;
    }

    public Slot getPatternSlot() {
        return this.patternSlot;
    }

    public Slot getResultSlot() {
        return this.resultSlot;
    }

    public boolean hasMaxPatternLayers() {
        ItemStack banner = this.bannerSlot.getStack();
        return banner != null && TileEntityBanner.getPatterns(banner) >= MAX_PATTERN_LAYERS;
    }

    // ==================== Item predicates ====================

    private static boolean isBannerItem(ItemStack stack) {
        return stack != null && stack.getItem() == Item.getItemFromBlock(ModBlocks.BANNER.get());
    }

    /**
     * Resolves the dye damage (0..15, {@link EnumColor} ordinal order) for the
     * given stack. Vanilla dyes use their item damage directly; EFR's
     * standalone dye items are matched through the {@code dye<Color>} ore
     * dictionary entries.
     *
     * @return the dye damage, or {@code -1} if the stack is not a dye
     */
    private static int getDyeDamage(ItemStack stack) {
        if (stack == null) {
            return -1;
        }
        if (stack.getItem() == Items.dye) {
            int damage = stack.getItemDamage();
            return damage >= 0 && damage < 16 ? damage : -1;
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds != null) {
            for (int color = 0; color < EnumColor.VALUES.length; color++) {
                int oreId = OreDictionary.getOreID(EnumColor.VALUES[color].getOreName());
                for (int i = 0; i < oreIds.length; i++) {
                    if (oreIds[i] == oreId) {
                        return color;
                    }
                }
            }
        }
        return -1;
    }
}
