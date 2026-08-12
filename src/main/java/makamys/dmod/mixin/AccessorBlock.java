package makamys.dmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Accessor interface exposing the protected Block#dropBlockAsItem(World, int, int, int, ItemStack) method.
 * Replaces the corresponding entry in dmod_at.cfg (func_149642_a).
 * <p>
 * 暴露 Block 的 protected dropBlockAsItem 方法的 Invoker 接口，替代 dmod_at.cfg 中对应的访问权限提升条目。
 */
@Mixin(Block.class)
public interface AccessorBlock {

    /**
     * Drops the given ItemStack as an entity in the world at the specified position.
     * <p>
     * 在指定位置将 ItemStack 以掉落物形式生成到世界中。
     *
     * @param world     the world. 世界实例。
     * @param x         the x coordinate. X 坐标。
     * @param y         the y coordinate. Y 坐标。
     * @param z         the z coordinate. Z 坐标。
     * @param itemstack the stack to drop. 要掉落的物品堆。
     */
    @Invoker("dropBlockAsItem")
    void callDropBlockAsItem(World world, int x, int y, int z, ItemStack itemstack);
}
