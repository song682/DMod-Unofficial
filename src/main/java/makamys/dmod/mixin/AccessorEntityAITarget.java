package makamys.dmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;

/**
 * Accessor interface for the private members of EntityAITarget.
 * Replaces the corresponding entries in dmod_at.cfg
 * (field_75303_a / field_75302_c / field_75301_b / func_75295_a).
 * <p>
 * 访问 EntityAITarget 私有成员的 Accessor 接口，替代 dmod_at.cfg 中对应的访问权限提升条目。
 */
@Mixin(EntityAITarget.class)
public interface AccessorEntityAITarget {

    /** @return whether only nearby targets are considered. 是否只考虑可轻松到达的目标。 */
    @Accessor("nearbyOnly")
    boolean getNearbyOnly();

    /** @return the current target search status. 当前目标搜索状态。 */
    @Accessor("targetSearchStatus")
    int getTargetSearchStatus();

    /**
     * Sets the target search status.
     * <p>
     * 设置目标搜索状态。
     */
    @Accessor("targetSearchStatus")
    void setTargetSearchStatus(int status);

    /** @return the current target search delay. 当前目标搜索延迟。 */
    @Accessor("targetSearchDelay")
    int getTargetSearchDelay();

    /**
     * Sets the target search delay.
     * <p>
     * 设置目标搜索延迟。
     */
    @Accessor("targetSearchDelay")
    void setTargetSearchDelay(int delay);

    /**
     * Checks whether this AI can find a short path to the given target.
     * <p>
     * 检查是否能够找到通往目标的短路径。
     *
     * @param target the candidate target. 候选目标。
     * @return true if the target can be easily reached. 能否轻松到达目标。
     */
    @Invoker("canEasilyReach")
    boolean invokeCanEasilyReach(EntityLivingBase target);
}
