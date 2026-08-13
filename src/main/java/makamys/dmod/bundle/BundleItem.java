package makamys.dmod.bundle;

import static makamys.dmod.DModConstants.MODID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyProvider;
import makamys.dmod.ConfigDMod;
import makamys.dmod.bundle.client.BundleTooltipRenderer;
import makamys.dmod.future.inventory.SlotFuture;
import makamys.dmod.future.item.ItemFuture;
import makamys.dmod.future.item.ItemStackFuture;
import makamys.dmod.item.IConfigurable;
import makamys.dmod.mixin.AccessorGuiContainer;
import makamys.dmod.util.StatRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import org.lwjgl.input.Mouse;

/**
 * 统一的收纳袋物品类 —— 替换旧的 {@code ItemBundle}（经典）与 {@code ItemModernBundle}
 * （现代染色）两个重复物品类。
 * <p>
 * 以 {@code colored} 构造参数实例化两个注册对象（{@code dmod:bundle} /
 * {@code dmod:stained_bundle}），注册名、NBT、配置键与旧版完全一致，旧存档无缝兼容。
 * <p>
 * 渲染完全交给 CatFrame 决策树模型（见 {@code assets/dmod/items/*.json} 与
 * {@link BundleItemModel}），本类不再持有任何 IIcon 或渲染逻辑。属性经
 * {@link #getPropertyDefinitions()} 声明，由 CatFrame 发现阶段自动注册：
 * <ul>
 *   <li>{@code dmod:bundle/color} — 16 色分派（仅染色变体）</li>
 *   <li>{@code dmod:bundle/has_selected_item} — 是否处于打开状态（需已用滚轮选中
 *       且光标当前正悬停在本袋上；光标移开立即闭合并清除选择，见
 *       {@link #isBundleOpen(ItemStack)}）</li>
 *   <li>{@code dmod:bundle/selected_item} — 选中索引（滚轮选择写入）</li>
 * </ul>
 * 交互（onStackClicked / onClicked / onItemRightClick）与耐久条逻辑委托
 * {@link BundleContents} 数据层。
 * <p>
 * Unified bundle item replacing the duplicated legacy {@code ItemBundle} and
 * {@code ItemModernBundle}; instantiated twice via the {@code colored} constructor
 * flag ({@code dmod:bundle} / {@code dmod:stained_bundle}). Rendering is fully
 * delegated to CatFrame decision-tree models; interactions delegate to
 * {@link BundleContents}.
 */
public class BundleItem extends ItemFuture implements IItemStateProvider, IConfigurable {

    /** true = stained_bundle（16 色），false = bundle（经典） */
    private final boolean colored;

    private static final int ITEM_BAR_COLOR = 0x6666FF;
    private static final int ITEM_BAR_COLOR_FULL = 0xFF3333;

    public BundleItem(boolean colored) {
        this.colored = colored;
        setMaxStackSize(1);
        setUnlocalizedName(MODID + "." + (colored ? "stained_bundle" : "bundle"));
        setCreativeTab(CreativeTabs.tabTools);
        if (isEnabled()) {
            StatRegistry.instance.registerItem(this);
        }
    }

    @Override
    public boolean isEnabled() {
        return colored ? ConfigDMod.modernBundle : ConfigDMod.enableBundle;
    }

    // ==================== 自定义属性声明（CatFrame 发现时自动注册） ====================

    @Override
    public Map<String, ItemPropertyProvider> getPropertyDefinitions() {
        Map<String, ItemPropertyProvider> props = new HashMap<>();
        if (colored) {
            props.put("dmod:bundle/color", (stack, phase) -> stack != null ? (stack.getItemDamage() & 0xF) : 0);
        }
        props.put("dmod:bundle/has_selected_item", (stack, phase) -> isBundleOpen(stack));
        props.put("dmod:bundle/selected_item", (stack, phase) -> BundleContents.getSelectedIndex(stack));
        return props;
    }

    /**
     * 打开状态判定：仅当已用滚轮选中物品（{@code Sel} 存在）且鼠标当前正悬停在
     * 本袋所在的槽位上时返回 true。悬停检测采用几何命中（鼠标坐标 vs 槽位矩形，
     * 与原版 {@code isMouseOverSlot} 同区间），不依赖 {@code theSlot} 字段——
     * 诊断发现创造模式 + NEI 环境下属性求值时 {@code theSlot} 会为 null，而滚轮
     * 写入时它又非空，字段读取时机不可靠。光标移开后立即闭合并清除选择（见
     * {@link #clearSelectionIfNotHovered}），再次悬停从头开始，不保留上次选中；
     * 右键取出会同样清除 {@code Sel}。仅客户端渲染时调用。
     * <p>Open-state check: true only when an entry is selected ({@code Sel}
     * present) AND the cursor is currently over the slot holding this exact
     * stack. Hover detection is geometric (mouse position vs slot rectangle,
     * same bounds as vanilla {@code isMouseOverSlot}) rather than reading the
     * {@code theSlot} field — diagnostics showed theSlot is null at property
     * evaluation time in creative-mode + NEI environments while being non-null
     * during the wheel write, making the field unreliable. Moving the cursor
     * away closes the bag and clears the selection (see
     * {@link #clearSelectionIfNotHovered}); the next hover starts from scratch
     * without keeping the previous choice. A right-click extraction clears
     * {@code Sel} as well. Client-only, evaluated during rendering.
     */
    @SideOnly(Side.CLIENT)
    public static boolean isBundleOpen(ItemStack stack) {
        int sel = BundleContents.getSelectedIndex(stack);
        if (sel < 0) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiContainer)) {
            return false;
        }
        GuiContainer gui = (GuiContainer) mc.currentScreen;
        int[] pos = getGuiRelativeMousePos(gui);
        Slot hitSlot = getSlotAt(gui, pos[2], pos[3]);
        return hitSlot != null && hitSlot.getStack() == stack;
    }

    /**
     * 将当前鼠标事件坐标换算为 GUI 内坐标（与 Minecraft.runTick 相同公式）。
     * 返回 {mouseX, mouseY, relX, relY}，其中 rel 相对 guiLeft/guiTop。
     * <p>Converts the current mouse event position into in-GUI coordinates
     * using the same formula as Minecraft.runTick. Returns {mouseX, mouseY,
     * relX, relY} where rel is relative to guiLeft/guiTop.
     */
    @SideOnly(Side.CLIENT)
    private static int[] getGuiRelativeMousePos(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * gui.width / mc.displayWidth;
        int mouseY = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        AccessorGuiContainer acc = (AccessorGuiContainer) (Object) gui;
        return new int[] { mouseX, mouseY, mouseX - acc.getGuiLeft(), mouseY - acc.getGuiTop() };
    }

    /**
     * 几何命中判定：返回 GUI 内坐标 (relX, relY) 命中的槽位，判定区间与原版
     * isMouseOverSlot/func_146978_c 相同（16x16 外扩 1px）；未命中返回 null。
     * <p>Geometric hit-test: returns the slot whose 16x16 rectangle (same
     * bounds as vanilla isMouseOverSlot/func_146978_c, padded by 1px) contains
     * the given in-GUI coordinates, or null if none.
     */
    @SideOnly(Side.CLIENT)
    private static Slot getSlotAt(GuiContainer gui, int relX, int relY) {
        for (Object o : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            if (relX >= slot.xDisplayPosition - 1 && relX < slot.xDisplayPosition + 17
                    && relY >= slot.yDisplayPosition - 1 && relY < slot.yDisplayPosition + 17) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 选择只在悬停期间有效：每次鼠标事件时调用，若光标不在某个已选中
     * （Sel≥0）收纳袋的槽位上，则立即清除该袋子的 Sel——再次悬停从头开始，
     * 不保留上次选择。
     * <p>The selection is only valid while hovering: called on every mouse
     * event; if the cursor is not over a slot holding a bundle with Sel≥0,
     * that bundle's Sel is cleared immediately (the next hover starts from
     * scratch, the previous choice is not kept).
     *
     * @param gui 当前打开的容器界面。The currently open container screen.
     */
    @SideOnly(Side.CLIENT)
    public static void clearSelectionIfNotHovered(GuiContainer gui) {
        if (gui == null || gui.inventorySlots == null) {
            return;
        }
        int[] pos = getGuiRelativeMousePos(gui);
        Slot hitSlot = getSlotAt(gui, pos[2], pos[3]);
        for (Object o : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            ItemStack s = slot.getStack();
            if (s != null && s.getItem() instanceof BundleItem
                    && BundleContents.getSelectedIndex(s) >= 0 && hitSlot != slot) {
                // setSelectedIndex(-1) 会移除 "Sel" 键（回到未选择状态）；随后刷新
                // 槽位以同步 NBT 到服务端（与滚轮写入路径一致）。
                // (setSelectedIndex(-1) removes the "Sel" key, i.e. unselected;
                // then refresh the slot to sync the NBT, matching the wheel path.)
                BundleContents.setSelectedIndex(s, -1);
                slot.onSlotChanged();
            }
        }
    }

    /**
     * 无条件清除界面内所有收纳袋的选择（Sel）：GUI 打开/关闭时的兜底清理。
     * 选择只在悬停期间有效，不应跨界面会话保留——否则上次会话写入的 Sel 会随
     * NBT 存进存档（例如未移开光标就关界面/退出游戏时，逐帧清除没有机会执行），
     * 下次打开界面悬停时 tooltip 会错误地显示旧的高亮。
     * <p>Unconditionally clears the selection (Sel) of every bundle in the
     * screen: a fail-safe purge on GUI open/close. The selection is only valid
     * while hovering and must not survive across screen sessions — otherwise a
     * Sel written in the previous session persists in NBT (e.g. when the GUI is
     * closed or the game quits while the cursor is still over the bundle, the
     * per-tick clear never gets a chance to run), and the next session would
     * show the stale highlight in the tooltip right after hovering.
     *
     * @param gui 目标容器界面。The target container screen.
     */
    @SideOnly(Side.CLIENT)
    public static void clearAllSelections(GuiContainer gui) {
        if (gui == null || gui.inventorySlots == null) {
            return;
        }
        for (Object o : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            ItemStack s = slot.getStack();
            if (s != null && s.getItem() instanceof BundleItem
                    && BundleContents.getSelectedIndex(s) >= 0) {
                BundleContents.setSelectedIndex(s, -1);
            }
        }
    }

    /**
     * 渲染由注册到 {@code ModelRegistry} 的 {@link BundleItemModel} 承担；本实现仅供
     * CatFrame 发现阶段作标记用（自动注册 dmod namespace 与上述属性），永远不会被调用
     * （注册表条目始终被 {@code BundleItemModel} 覆盖）。
     * <p>Rendering is handled by the registered {@link BundleItemModel}; this no-op
     * exists only so CatFrame's discovery can pick this item up. It is never invoked.
     */
    @Override
    public void render(ItemStack stack, RenderPhase phase) {}

    // ==================== 交互（委托 BundleContents） ====================

    @Override
    public boolean onStackClicked(ItemStack stack, Slot slot, int button, EntityPlayer player) {
        if (button != 0 && button != 1) {
            return false;
        }
        ItemStack itemStack = slot.getStack();
        if (itemStack == null) {
            if (button == 1) {
                ItemStack removed = BundleContents.removeFirst(stack);
                if (removed != null) {
                    BundleContents.add(stack, SlotFuture.insertStack(slot, removed));
                }
                return true;
            }
            return false;
        } else if (BundleContents.isAllowed(itemStack)) {
            int count = (BundleContents.MAX_STORAGE - BundleContents.getOccupancy(stack)) / BundleContents.getItemOccupancy(itemStack);
            BundleContents.add(stack, SlotFuture.takeStackRange(slot, itemStack.stackSize, count, player));
            return true;
        } else {
            // 黑名单物品：消费点击（与 1.17 一致，不装入也不执行原版交互）。
            // (Blacklisted item: consume the click like 1.17, no insert and no
            // vanilla interaction.)
            return true;
        }
    }

    /**
     * 光标悬停在收纳袋上时的点击交互（stack = 格子里的袋子）：
     * <ul>
     *   <li>左键（button 0）：不消费，走原版逻辑拿起收纳袋</li>
     *   <li>右键且光标为空（button 1, otherStack == null）：滚轮选中过则取出选中的
     *       一组（取出即闭合），否则取出最后放入的一组，放到光标上</li>
     *   <li>光标有物品时点击袋子（button 0/1, otherStack != null）：把光标物品
     *       装入袋子（“拿起其它物品左键点击袋子”收纳场景）</li>
     * </ul>
     * <p>Click interactions while the cursor is over a bundle in a slot
     * (stack = the bundle in the slot):
     * <ul>
     *   <li>Left click (button 0) is not consumed: vanilla pickup of the bundle</li>
     *   <li>Right click with an empty cursor (button 1, otherStack == null) takes
     *       the selected group when the wheel selection is active (taking closes the
     *       bundle), otherwise the most recently inserted group, onto the cursor</li>
     *   <li>Clicking the bundle with an item on the cursor (button 0/1,
     *       otherStack != null) inserts the cursor item into the bundle</li>
     * </ul>
     */
    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, int button, EntityPlayer player) {
        if (button == 1 && SlotFuture.canTakePartial(slot, player)) {
            if (otherStack == null) {
                ItemStack removed = BundleContents.removeSelected(stack);
                if (removed == null) {
                    removed = BundleContents.removeFirst(stack);
                }
                if (removed != null) {
                    player.inventory.setItemStack(removed);
                }
            } else {
                otherStack.stackSize -= BundleContents.add(stack, otherStack);
                otherStack = ItemStackFuture.oldify(otherStack);
                if (otherStack == null) {
                    player.inventory.setItemStack(null);
                }
            }
            return true;
        } else if (button == 0 && otherStack != null) {
            // 拿起（非收纳袋）物品时左键点击袋子 → 物品装入袋子。
            // (Left-clicking the bundle while holding a non-bundle item inserts it.)
            otherStack.stackSize -= BundleContents.add(stack, otherStack);
            otherStack = ItemStackFuture.oldify(otherStack);
            if (otherStack == null) {
                player.inventory.setItemStack(null);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack p_77659_1_, World p_77659_2_, EntityPlayer user) {
        ItemStack itemStack = user.getHeldItem();
        if (BundleContents.dropAll(itemStack, user)) {
            user.addStat(StatList.objectUseStats[Item.getIdFromItem(this)], 1);
        }
        return itemStack;
    }

    /**
     * 滚轮在可见槽位之间循环切换选中索引（0..displayCount-1 环绕）。未选中时向上
     * 滚选中第一个、向下滚选中最后一个；空袋或滚轮无输入时不操作。范围限定在
     * tooltip 可见槽位内（折叠区外的条目不可选中），保证选中项始终有可见的高亮。
     * 返回是否发生切换（供调用方决定是否刷新格子以同步 NBT 到服务端）。
     * <p>Cycle the selected index with the scroll wheel within the visible slots
     * (wrapping around 0..displayCount-1). With no selection yet, scrolling up
     * selects the first visible slot and scrolling down selects the last; empty
     * bundles and zero wheel input are no-ops. The range is limited to the slots
     * visible in the tooltip (entries folded out of the grid cannot be selected),
     * so the selected entry always has a visible highlight. Returns true when the
     * selection changed, so callers can refresh the slot to sync the NBT.
     */
    public static boolean scrollSelectedIndex(ItemStack stack, int dWheel) {
        if (dWheel == 0) {
            return false;
        }
        int count = BundleContents.getEntryCount(stack);
        if (count == 0) {
            return false;
        }
        int visible = BundleContents.getDisplayCount(count);
        int idx = BundleContents.getSelectedIndex(stack);
        int newIdx;
        if (idx < 0) {
            newIdx = dWheel > 0 ? 0 : visible - 1;
        } else {
            newIdx = (idx + (dWheel > 0 ? 1 : -1) + visible) % visible;
        }
        BundleContents.setSelectedIndex(stack, newIdx);
        return true;
    }

    // ==================== 耐久条（现代风格，经典同步升级） ====================

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return BundleContents.getOccupancy(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (Math.min(1 + 12 * BundleContents.getOccupancy(stack) / BundleContents.MAX_STORAGE, 13) / 13.0);
    }

    @Override
    public boolean getItemBarHasColor(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        if (BundleContents.getOccupancy(stack) >= BundleContents.MAX_STORAGE) {
            return ITEM_BAR_COLOR_FULL;
        }
        return ITEM_BAR_COLOR;
    }

    // ==================== 创造标签 / 显示名 ====================

    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        if (colored) {
            for (BundleColor color : BundleColor.values()) {
                list.add(new ItemStack(item, 1, color.getBundleMeta()));
            }
        } else {
            super.getSubItems(item, tab, list);
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (!colored) {
            return super.getItemStackDisplayName(stack);
        }
        BundleColor color = BundleColor.fromDamage(stack.getItemDamage());
        return I18n.format("item.dmod.stained_bundle.colored.name", I18n.format("color.dmod." + color.getTexturePrefix()));
    }

    // ==================== Tooltip ====================

    @SideOnly(Side.CLIENT)
    @cpw.mods.fml.common.Optional.Method(modid = "CodeChickenCore")
    @Override
    public List<ITooltipLineHandler> getTooltipHandlers(ItemStack stack) {
        List<ItemStack> stacks = BundleContents.getBundledStacks(stack).collect(Collectors.toList());
        return Arrays.asList(new BundleTooltipRenderer(stacks, BundleContents.getOccupancy(stack), stack));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, World world, List<String> tooltip) {
        tooltip.add(EnumChatFormatting.GRAY + I18n.format("item." + MODID + "." + (colored ? "stained_bundle" : "bundle") + ".fullness",
                BundleContents.getOccupancy(stack), BundleContents.MAX_STORAGE));
    }
}
