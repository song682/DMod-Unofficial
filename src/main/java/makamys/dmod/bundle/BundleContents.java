package makamys.dmod.bundle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import cpw.mods.fml.relauncher.ReflectionHelper;
import makamys.dmod.ConfigDMod;
import makamys.dmod.future.item.ItemStackFuture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 收纳袋 NBT 数据层 —— 袋内物品读写的唯一入口。
 * <p>
 * 由旧 {@code ItemBundle} / {@code ItemModernBundle} 中逐字重复的静态方法合并而来
 * （消灭 ~600 行重复代码）。全部方法均为纯 ItemStack/NBT 操作，无客户端渲染依赖，
 * 可在服务端安全加载。
 * <p>
 * NBT 结构与旧存档逐字节兼容：{@code "Items"} 为 {@code NBTTagList}（每项一个
 * ItemStack 的 NBTTagCompound，新插入的条目在列表头部）；新增可选 {@code "Sel"}
 * int 字段保存选中索引（缺省 -1 = 未选择），供未来的滚轮选择功能使用。
 * <p>
 * The single data layer for bundle contents — the only entry point for reading and
 * writing the {@code "Items"} list. Merged from the duplicated static methods of the
 * legacy {@code ItemBundle} / {@code ItemModernBundle}, with a new optional
 * {@code "Sel"} field for the (future) scroll-to-select feature. The {@code "Items"}
 * format is byte-compatible with old saves.
 */
public class BundleContents {

    /** 容量上限（重量制，units）。 */
    public static final int MAX_STORAGE = 64;
    /** 嵌套 bundle 的基础占用（不含其自身内容物）。 */
    private static final int BUNDLE_OCCUPANCY_BASE = 4;
    /** 嵌套 bundle 的深度上限：超过直接按满占用处理，防止极端嵌套下递归过深。 */
    private static final int MAX_NESTING_DEPTH = 4;

    private BundleContents() {}

    // ==================== 查询 ====================

    /**
     * 返回袋内所有条目（跳过反序列化失败的损坏条目，如模组被移除）。
     */
    public static Stream<ItemStack> getBundledStacks(ItemStack stack) {
        NBTTagCompound nbt = stack.stackTagCompound;
        if (nbt == null) {
            return Stream.empty();
        }
        NBTTagList tagList = nbt.getTagList("Items", 10);
        return toList(tagList).stream()
                .filter(NBTTagCompound.class::isInstance)
                .map(NBTTagCompound.class::cast)
                .map(ItemStack::loadItemStackFromNBT)
                .filter(Objects::nonNull);
    }

    /**
     * 返回袋子当前占用（重量制，sum of {@code itemOccupancy * stackSize}）。
     */
    public static int getOccupancy(ItemStack stack) {
        return getOccupancy(stack, 0);
    }

    /**
     * 单个物品的占用：普通物品 {@code 64 / maxStackSize}；嵌套 bundle 为
     * {@code 4 + 其自身占用}，深度超过 {@link #MAX_NESTING_DEPTH} 时直接返回
     * {@link #MAX_STORAGE}（满占用），防止极端嵌套下递归过深。
     */
    public static int getItemOccupancy(ItemStack stack) {
        return getItemOccupancy(stack, 0);
    }

    private static int getItemOccupancy(ItemStack stack, int depth) {
        if (stack == null) {
            return 0;
        }
        Item item = stack.getItem();
        if (item instanceof BundleItem) {
            if (depth >= MAX_NESTING_DEPTH) {
                return MAX_STORAGE;
            }
            return BUNDLE_OCCUPANCY_BASE + getOccupancy(stack, depth + 1);
        }
        return MAX_STORAGE / stack.getMaxStackSize();
    }

    private static int getOccupancy(ItemStack stack, int depth) {
        return getBundledStacks(stack).mapToInt(itemStack -> getItemOccupancy(itemStack, depth + 1) * itemStack.stackSize).sum();
    }

    /**
     * 返回袋内首条目（列表头，即最新插入的物品）。损坏条目返回 null。
     */
    public static ItemStack getFirstItem(ItemStack stack) {
        NBTTagCompound nbt = stack != null ? stack.stackTagCompound : null;
        if (nbt == null || !nbt.hasKey("Items")) {
            return null;
        }
        NBTTagList tagList = nbt.getTagList("Items", 10);
        if (tagList.tagCount() == 0) {
            return null;
        }
        return ItemStack.loadItemStackFromNBT(tagList.getCompoundTagAt(0));
    }

    /**
     * 返回袋内条目数（直接读 {@code "Items"} 列表长度，不做 NBT 反序列化；
     * 损坏条目也计入，与选中索引的钳制范围一致）。用于滚轮选择的环绕范围。
     */
    public static int getEntryCount(ItemStack stack) {
        NBTTagCompound nbt = stack != null ? stack.stackTagCompound : null;
        if (nbt == null || !nbt.hasKey("Items")) {
            return 0;
        }
        return nbt.getTagList("Items", 10).tagCount();
    }

    // ==================== 写入 ====================

    /**
     * 将物品装入袋子，受容量上限约束，尽可能合并到现有条目（合并后移到列表头）。
     *
     * @return 实际装入的数量
     */
    public static int add(ItemStack bundle, ItemStack stack) {
        if (stack != null && isAllowed(stack)) {
            NBTTagCompound nbt = ItemStackFuture.getOrCreateNbt(bundle);
            if (!nbt.hasKey("Items")) {
                nbt.setTag("Items", new NBTTagList());
            }

            int occupancy = getOccupancy(bundle);
            int itemOccupancy = getItemOccupancy(stack);
            int count = Math.min(stack.stackSize, (MAX_STORAGE - occupancy) / itemOccupancy);
            if (count == 0) {
                return 0;
            } else {
                NBTTagList tagList = nbt.getTagList("Items", 10);
                Optional<NBTTagCompound> mergeTarget = canMergeStack(stack, tagList);
                List<NBTBase> list = toList(tagList);
                if (mergeTarget.isPresent()) {
                    NBTTagCompound entry = mergeTarget.get();
                    ItemStack existing = ItemStack.loadItemStackFromNBT(entry);
                    if (existing == null) return 0;
                    ItemStackFuture.increment(existing, count);
                    existing.writeToNBT(entry);
                    list.remove(entry);
                    add(tagList, 0, entry);
                } else {
                    ItemStack copy = stack.copy();
                    copy.stackSize = count;
                    NBTTagCompound entry = new NBTTagCompound();
                    copy.writeToNBT(entry);
                    add(tagList, 0, entry);
                }
                // 新条目插入列表头：选中索引随之前移，保持选中的条目不变。
                // (A new entry is inserted at the list head; shift the selected
                // index forward so the selected entry stays the same.)
                if (nbt.hasKey("Sel")) {
                    nbt.setInteger("Sel", nbt.getInteger("Sel") + 1);
                }
                return count;
            }
        } else {
            return 0;
        }
    }

    /**
     * 取出并返回袋内首条目（跳过损坏条目；列表清空时移除 {@code "Items"} 键）。
     * 空袋返回 null。
     */
    public static ItemStack removeFirst(ItemStack stack) {
        NBTTagCompound nbt = ItemStackFuture.getOrCreateNbt(stack);
        if (!nbt.hasKey("Items")) {
            return null;
        }
        NBTTagList tagList = nbt.getTagList("Items", 10);
        List<NBTBase> list = toList(tagList);
        if (tagList.tagCount() == 0) {
            return null;
        }
        // Skip entries that failed to load (e.g. mod removed)
        ItemStack removed = null;
        while (tagList.tagCount() > 0 && removed == null) {
            NBTTagCompound entry = tagList.getCompoundTagAt(0);
            removed = ItemStack.loadItemStackFromNBT(entry);
            list.remove(0);
        }
        if (tagList.tagCount() == 0) {
            stack.stackTagCompound.removeTag("Items");
            stack.stackTagCompound.removeTag("Sel");
        } else if (removed != null && nbt.hasKey("Sel")) {
            // 移除列表头后选中索引随之前移；选中的正是被移除项时清除选中（闭合）。
            // (After removing the list head the selected index shifts forward;
            // if the removed entry was the selected one, the selection is cleared.)
            int sel = nbt.getInteger("Sel");
            if (sel <= 0) {
                nbt.removeTag("Sel");
            } else {
                nbt.setInteger("Sel", sel - 1);
            }
        }
        return removed;
    }

    /**
     * 取出并返回选中索引对应的条目，随后清除选中（取出即闭合）。未选中时返回 null；
     * 选中条目损坏时同样移除该条目并清除选中，返回 null。
     * <p>Take and return the entry at the selected index, then clear the selection
     * (taking closes the bundle). Returns null when nothing is selected; a corrupt
     * selected entry is removed and the selection cleared as well.
     */
    public static ItemStack removeSelected(ItemStack stack) {
        int idx = getSelectedIndex(stack);
        if (idx < 0) {
            return null;
        }
        NBTTagCompound nbt = ItemStackFuture.getOrCreateNbt(stack);
        NBTTagList tagList = nbt.getTagList("Items", 10);
        if (idx >= tagList.tagCount()) {
            return null;
        }
        List<NBTBase> list = toList(tagList);
        NBTTagCompound entry = tagList.getCompoundTagAt(idx);
        ItemStack removed = ItemStack.loadItemStackFromNBT(entry);
        list.remove(idx);
        nbt.removeTag("Sel");
        if (tagList.tagCount() == 0) {
            stack.stackTagCompound.removeTag("Items");
        }
        return removed;
    }

    /**
     * 将袋内全部物品倒出（仅服务端实体真正弹出），并清空 {@code "Items"} 键。
     *
     * @return 袋子原本是否有内容
     */
    public static boolean dropAll(ItemStack stack, EntityPlayer player) {
        NBTTagCompound nbt = ItemStackFuture.getOrCreateNbt(stack);
        if (!nbt.hasKey("Items")) {
            return false;
        }
        if (player instanceof EntityPlayerMP) {
            NBTTagList tagList = nbt.getTagList("Items", 10);
            for (int i = 0; i < tagList.tagCount(); ++i) {
                ItemStack itemStack = ItemStack.loadItemStackFromNBT(tagList.getCompoundTagAt(i));
                if (itemStack != null) {
                    player.dropPlayerItemWithRandomChoice(itemStack, true);
                }
            }
        }
        stack.stackTagCompound.removeTag("Items");
        return true;
    }

    // ==================== 选中索引（未来滚轮选择，已预埋） ====================

    /**
     * 读取选中索引。未写入过 {@code "Sel"}（从未选择）时返回 -1；越界钳制为 -1。
     * 注意：有内容但未显式选择同样返回 -1 —— 选中是显式状态（完成时态语义），
     * 只有写过 {@code "Sel"} 才算“已选中”。
     */
    public static int getSelectedIndex(ItemStack stack) {
        NBTTagCompound nbt = stack != null ? stack.stackTagCompound : null;
        if (nbt == null || !nbt.hasKey("Sel")) {
            return -1;
        }
        NBTTagList tagList = nbt.getTagList("Items", 10);
        int sel = nbt.getInteger("Sel");
        return sel >= 0 && sel < tagList.tagCount() ? sel : -1;
    }

    /**
     * 返回选中索引对应的条目；未选中、越界或条目损坏时返回 null。
     * 渲染层用它显示“袋口选中的物品”（open 状态下被展示的那一项）。
     */
    public static ItemStack getSelectedStack(ItemStack stack) {
        int idx = getSelectedIndex(stack);
        if (idx < 0) {
            return null;
        }
        NBTTagCompound nbt = stack != null ? stack.stackTagCompound : null;
        if (nbt == null) {
            return null;
        }
        NBTTagList tagList = nbt.getTagList("Items", 10);
        return idx < tagList.tagCount() ? ItemStack.loadItemStackFromNBT(tagList.getCompoundTagAt(idx)) : null;
    }

    /**
     * 写入选中索引；-1 时移除 {@code "Sel"} 键。
     */
    public static void setSelectedIndex(ItemStack stack, int index) {
        NBTTagCompound nbt = ItemStackFuture.getOrCreateNbt(stack);
        if (index < 0) {
            nbt.removeTag("Sel");
        } else {
            nbt.setInteger("Sel", index);
        }
    }

    // ==================== 黑名单 ====================

    /**
     * 物品是否允许装入袋子（委托配置黑名单）。
     */
    public static boolean isAllowed(ItemStack stack) {
        return ConfigDMod.backpackHelper.isAllowed(stack);
    }

    // ==================== 内部工具 ====================

    private static Optional<NBTTagCompound> canMergeStack(ItemStack stack, NBTTagList items) {
        if (stack != null && stack.getItem() instanceof BundleItem) {
            return Optional.empty();
        }
        return toList(items).stream()
                .filter(NBTTagCompound.class::isInstance)
                .map(NBTTagCompound.class::cast)
                .filter(entry -> {
                    ItemStack loaded = ItemStack.loadItemStackFromNBT(entry);
                    return loaded != null && ItemStackFuture.canCombine(loaded, stack);
                })
                .findFirst();
    }

    /**
     * 直接访问 NBTTagList 的内部存储列表（1.7.10 无公开 API，反射获取）。
     * 原 NBTTagListFuture 内联至此 —— 该工具类仅被旧 bundle 类使用。
     */
    private static List<NBTBase> toList(NBTTagList tagList) {
        return ReflectionHelper.getPrivateValue(NBTTagList.class, tagList, "tagList", "field_74747_a");
    }

    private static void add(NBTTagList tagList, int index, NBTBase element) {
        if (tagList.tagCount() == 0) {
            tagList.appendTag(element);
        } else {
            toList(tagList).add(index, element);
        }
    }
}
