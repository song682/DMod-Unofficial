package makamys.dmod.item;

import static makamys.dmod.DModConstants.MODID;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ModernItem;
import makamys.dmod.ConfigDMod;
import makamys.dmod.DModConstants;
import makamys.dmod.DModItems;
import makamys.dmod.client.tooltip.ModernBundleTooltipHandler;
import makamys.dmod.future.inventory.SlotFuture;
import makamys.dmod.future.item.IItemFuture;
import makamys.dmod.future.item.ItemStackFuture;
import makamys.dmod.future.nbt.NBTTagListFuture;
import makamys.dmod.util.StatRegistry;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Modern (1.21+) Bundle with dye support.
 * Extends CatFrame's ModernItem for unlimited render passes (3-layer open state).
 * Uses metadata (0-15) for 16 color variants.
 *
 * Rendering layers when open (has items):
 *   pass 0 = back layer (bundle_open_back)
 *   pass 1 = first item peeking out
 *   pass 2 = front layer (bundle_open_front)
 *
 * When closed (empty): all 3 passes show the closed texture.
 */
public class ItemModernBundle extends ModernItem implements IItemFuture, IConfigurable {
    
    public static final int MAX_STORAGE = 64;
    private static final int ITEM_BAR_COLOR = 0x6666FF;
    private static final int ITEM_BAR_COLOR_FULL = 0xFF3333;
    
    // 16 colors for dye system (metadata 0-15)
    private static final String[] COLOR_NAMES = {
        "white", "orange", "magenta", "light_blue",
        "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"
    };
    
    private static final int RENDER_PASSES = 3;
    
    @SideOnly(Side.CLIENT)
    private IIcon[] iconClosedColored = new IIcon[16];
    @SideOnly(Side.CLIENT)
    private IIcon[] iconOpenFrontColored = new IIcon[16];
    @SideOnly(Side.CLIENT)
    private IIcon[] iconOpenBackColored = new IIcon[16];
    
    public ItemModernBundle() {
        super(RENDER_PASSES);  // 3 render layers via CatFrame ModernItem
        setMaxStackSize(1);
        setUnlocalizedName(MODID + "." + "stained_bundle");
        setCreativeTab(CreativeTabs.tabTools);
        setTextureName(MODID + ":white_bundle"); // Default texture fallback
        setRender3DInHand(false); // Bundle is a flat item, not a 3D tool
        if(isEnabled()) {
            StatRegistry.instance.registerItem(this);
        }
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IIconRegister iconRegister) {
        // Register all 16 color variants x 3 states
        for (int i = 0; i < 16; i++) {
            String color = COLOR_NAMES[i];
            iconClosedColored[i] = iconRegister.registerIcon(MODID + ":" + color + "_bundle");
            iconOpenFrontColored[i] = iconRegister.registerIcon(MODID + ":" + color + "_bundle_open_front");
            iconOpenBackColored[i] = iconRegister.registerIcon(MODID + ":" + color + "_bundle_open_back");
        }
        // Set itemIcon for vanilla fallback (white closed)
        this.itemIcon = iconClosedColored[0];
    }

    @Override
    public boolean isEnabled() {
        return ConfigDMod.modernBundle;
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        // Add all 16 color variants to creative tab
        for (int i = 0; i < 16; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
    
    // ==================== Multi-layer rendering (CatFrame ModernItem) ====================
    
    @SideOnly(Side.CLIENT)
    @Override
    public boolean requiresMultipleRenderPasses() {
        return true;
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public int getRenderPasses(int metadata) {
        return RENDER_PASSES;
    }
    
    @Override
    public IIcon getIconIndex(ItemStack stack) {
        int colorIndex = stack.getItemDamage() & 0xF;
        return iconClosedColored[colorIndex];
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(ItemStack stack, int renderPass) {
        int colorIndex = stack.getItemDamage() & 0xF;
        
        if (getAmountFilled(stack) > 0) {
            // Open state: 3-layer rendering
            switch (renderPass) {
                case 0:
                    // Back layer
                    return iconOpenBackColored[colorIndex];
                case 1:
                    // Middle layer: first item peeking out of the bundle
                    ItemStack firstItem = getFirstBundledItem(stack);
                    if (firstItem != null) {
                        return firstItem.getItem().getIconIndex(firstItem);
                    }
                    // Fallback if item can't be resolved
                    return iconOpenBackColored[colorIndex];
                case 2:
                    // Front layer
                    return iconOpenFrontColored[colorIndex];
            }
        }
        // Closed state: all passes show the closed icon
        return iconClosedColored[colorIndex];
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public int getColorFromItemStack(ItemStack stack, int pass) {
        if (pass == 1 && getAmountFilled(stack) > 0) {
            // Apply the first item's own tint color
            ItemStack firstItem = getFirstBundledItem(stack);
            if (firstItem != null) {
                return firstItem.getItem().getColorFromItemStack(firstItem, 0);
            }
        }
        return 0xFFFFFF; // No tint for bundle textures
    }
    
    /**
     * Check if the bundle should render in open state
     */
    @SideOnly(Side.CLIENT)
    public static boolean shouldRenderOpen(ItemStack stack) {
        return stack != null && getAmountFilled(stack) > 0;
    }
    
    /**
     * Get the color index from metadata (0-15)
     */
    public static int getColorIndex(ItemStack stack) {
        if (stack == null) return 0;
        return stack.getItemDamage() & 0xF;
    }
    
    /**
     * Set the color of the bundle using metadata
     */
    public static ItemStack setBundleColor(ItemStack stack, int colorIndex) {
        if (stack == null || stack.getItem() != DModItems.modernBundleItem) return stack;
        colorIndex = colorIndex & 0xF;
        stack.setItemDamage(colorIndex);
        return stack;
    }
    
    // ==================== Bundle interaction (IItemFuture) ====================
    
    public static float getAmountFilled(ItemStack stack) {
        return (float) getBundleOccupancy(stack) / 64.0F;
    }
    
    @Override
    public boolean onStackClicked(ItemStack bundleStack, Slot slot, int button, EntityPlayer player) {
        if (button != 1) {
            return false;
        } else {
            ItemStack itemStack = slot.getStack();
            if (itemStack == null) {
                ItemStack removed = removeFirstStack(bundleStack);
                if(removed != null) {
                    addToBundle(bundleStack, SlotFuture.insertStack(slot, removed));
                }
            } else if (canAcceptItemStack(itemStack)) {
                int i = (64 - getBundleOccupancy(bundleStack)) / getItemOccupancy(itemStack);
                addToBundle(bundleStack, SlotFuture.takeStackRange(slot, itemStack.stackSize, i, player));
            }

            return true;
        }
    }

    @Override
    public boolean onClicked(ItemStack bundleStack, ItemStack otherStack, Slot slot, int button, EntityPlayer player) {
        if (button == 1 && SlotFuture.canTakePartial(slot, player)) {
            if (otherStack == null) {
                ItemStack var10000 = removeFirstStack(bundleStack);
                if(var10000 != null) {
                    player.inventory.setItemStack(var10000);
                }
            } else {
                int added = addToBundle(bundleStack, otherStack);
                otherStack.stackSize -= added;
                otherStack = ItemStackFuture.oldify(otherStack);
                if(otherStack == null) {
                    player.inventory.setItemStack(null);
                }
            }

            return true;
        } else {
            return false;
        }
    }
    
    @Override
    public ItemStack onItemRightClick(ItemStack p_77659_1_, World p_77659_2_, EntityPlayer user) {
        ItemStack itemStack = user.getHeldItem();
        if (dropAllBundledItems(itemStack, user)) {
            user.addStat(StatList.objectUseStats[Item.getIdFromItem(this)], 1);
        }
        return itemStack;
    }
    
    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getBundleOccupancy(stack) > 0;
    }
    
    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (Math.min(1 + 12 * getBundleOccupancy(stack) / 64, 13) / 13.0);
    }

    @Override
    public boolean getItemBarHasColor(ItemStack stack) {
        return true;
    }
    
    @Override
    public int getItemBarColor(ItemStack stack) {
        if (getBundleOccupancy(stack) >= MAX_STORAGE) {
            return ITEM_BAR_COLOR_FULL;
        }
        return ITEM_BAR_COLOR;
    }

    private static int addToBundle(ItemStack bundle, ItemStack stack) {
        if (stack != null && canAcceptItemStack(stack)) {
            NBTTagCompound NBTTagCompound = ItemStackFuture.getOrCreateNbt(bundle);
            if (!NBTTagCompound.hasKey("Items")) {
                NBTTagCompound.setTag("Items", new NBTTagList());
            }

            int i = getBundleOccupancy(bundle);
            int j = getItemOccupancy(stack);
            int k = Math.min(stack.stackSize, (64 - i) / j);
            if (k == 0) {
                return 0;
            } else {
                NBTTagList tagList = NBTTagCompound.getTagList("Items", 10);
                Optional<NBTTagCompound> optional = canMergeStack(stack, tagList);
                List<NBTBase> list = NBTTagListFuture.toList(tagList);
                if (optional.isPresent()) {
                    NBTTagCompound NBTTagCompound2 = (NBTTagCompound) optional.get();
                    ItemStack itemStack = ItemStack.loadItemStackFromNBT(NBTTagCompound2);
                    if (itemStack == null) return 0;
                    ItemStackFuture.increment(itemStack, k);
                    itemStack.writeToNBT(NBTTagCompound2);
                    list.remove(NBTTagCompound2);
                    NBTTagListFuture.add(tagList, 0, NBTTagCompound2);
                } else {
                    ItemStack itemStack2 = stack.copy();
                    itemStack2.stackSize = k;
                    NBTTagCompound NBTTagCompound3 = new NBTTagCompound();
                    itemStack2.writeToNBT(NBTTagCompound3);
                    
                    NBTTagListFuture.add(tagList, 0, NBTTagCompound3);
                }

                return k;
            }
        } else {
            return 0;
        }
    }
    
    private static boolean canAcceptItemStack(ItemStack is) {
        return ConfigDMod.backpackHelper.isAllowed(is);
    }

    private static Optional<NBTTagCompound> canMergeStack(ItemStack stack, NBTTagList items) {
        if (stack != null && (stack.getItem() == DModItems.bundle || stack.getItem() == DModItems.modernBundleItem)) {
            return Optional.empty();
        } else {
            Stream<NBTBase> var10000 = NBTTagListFuture.toList(items).stream();
            Objects.requireNonNull(NBTTagCompound.class);
            var10000 = var10000.filter(NBTTagCompound.class::isInstance);
            Objects.requireNonNull(NBTTagCompound.class);
            return var10000.map(NBTTagCompound.class::cast).filter((item) -> {
                ItemStack loaded = ItemStack.loadItemStackFromNBT(item);
                return loaded != null && ItemStackFuture.canCombine(loaded, stack);
            }).findFirst();
        }
    }

    private static int getItemOccupancy(ItemStack stack) {
        if(stack == null) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == DModItems.bundle || item == DModItems.modernBundleItem) {
            return 4 + getBundleOccupancy(stack);
        } else {
            return 64 / stack.getMaxStackSize();
        }
    }

    private static int getBundleOccupancy(ItemStack stack) {
        return getBundledStacks(stack).mapToInt((itemStack) -> {
            return getItemOccupancy(itemStack) * itemStack.stackSize;
        }).sum();
    }

    private static ItemStack removeFirstStack(ItemStack stack) {
        NBTTagCompound NBTTagCompound = ItemStackFuture.getOrCreateNbt(stack);
        if (!NBTTagCompound.hasKey("Items")) {
            return null;
        } else {
            NBTTagList tagList = NBTTagCompound.getTagList("Items", 10);
            List<NBTBase> list = NBTTagListFuture.toList(tagList);
            if (tagList.tagCount() == 0) {
                return null;
            } else {
                // Skip entries that failed to load (e.g. mod removed)
                ItemStack itemStack = null;
                while (tagList.tagCount() > 0 && itemStack == null) {
                    NBTTagCompound NBTTagCompound2 = tagList.getCompoundTagAt(0);
                    itemStack = ItemStack.loadItemStackFromNBT(NBTTagCompound2);
                    list.remove(0);
                }
                if (tagList.tagCount() == 0) {
                    stack.stackTagCompound.removeTag("Items");
                }

                return itemStack;
            }
        }
    }

    private static boolean dropAllBundledItems(ItemStack stack, EntityPlayer player) {
        NBTTagCompound NBTTagCompound = ItemStackFuture.getOrCreateNbt(stack);
        if (!NBTTagCompound.hasKey("Items")) {
            return false;
        } else {
            if (player instanceof EntityPlayerMP) {
                NBTTagList tagList = NBTTagCompound.getTagList("Items", 10);

                for (int i = 0; i < tagList.tagCount(); ++i) {
                    NBTTagCompound NBTTagCompound2 = tagList.getCompoundTagAt(i);
                    ItemStack itemStack = ItemStack.loadItemStackFromNBT(NBTTagCompound2);
                    if (itemStack != null) {
                        player.dropPlayerItemWithRandomChoice(itemStack, true);
                    }
                }
            }

            stack.stackTagCompound.removeTag("Items");
            return true;
        }
    }

    private static Stream<ItemStack> getBundledStacks(ItemStack stack) {
        NBTTagCompound NBTTagCompound = stack.stackTagCompound;
        if (NBTTagCompound == null) {
            return Stream.empty();
        } else {
            NBTTagList NBTTagList = NBTTagCompound.getTagList("Items", 10);
            Stream<NBTBase> var10000 = NBTTagListFuture.toList(NBTTagList).stream();
            Objects.requireNonNull(NBTTagCompound.class);
            return var10000.map(NBTTagCompound.class::cast).map(ItemStack::loadItemStackFromNBT).filter(Objects::nonNull);
        }
    }
    
    /**
     * Get the first item in the bundle (top of stack) for rendering.
     */
    @SideOnly(Side.CLIENT)
    private static ItemStack getFirstBundledItem(ItemStack bundle) {
        if (bundle.stackTagCompound == null) return null;
        if (!bundle.stackTagCompound.hasKey("Items")) return null;
        NBTTagList tagList = bundle.stackTagCompound.getTagList("Items", 10);
        if (tagList.tagCount() == 0) return null;
        return ItemStack.loadItemStackFromNBT(tagList.getCompoundTagAt(0));
    }
    
    // ==================== Tooltip ====================
    
    @SideOnly(Side.CLIENT)
    @cpw.mods.fml.common.Optional.Method(modid = "CodeChickenCore")
    @Override
    public List<ITooltipLineHandler> getTooltipHandlers(ItemStack stack) {
        List<ItemStack> stacks = getBundledStacks(stack).collect(Collectors.toList());
        int occ = getBundleOccupancy(stack);
        return Arrays.asList((ITooltipLineHandler) new ModernBundleTooltipHandler(stacks, occ));
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, World world, List<String> tooltip) {
        tooltip.add(
                EnumChatFormatting.GRAY + I18n.format("item." + MODID + ".stained_bundle.fullness", getBundleOccupancy(stack), 64));
    }
    
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        int colorIndex = getColorIndex(stack);
        String colorName = I18n.format("color.dmod." + COLOR_NAMES[colorIndex]);
        return I18n.format("item.dmod.stained_bundle.colored.name", colorName);
    }
}
