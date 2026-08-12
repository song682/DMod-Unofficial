package makamys.dmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;

/**
 * Accessor interface for the private members of EntityAINearestAttackableTarget.
 * Replaces the corresponding entries in dmod_at.cfg (field_75308_c / field_75309_a).
 * <p>
 * 访问 EntityAINearestAttackableTarget 私有成员的 Accessor 接口，替代 dmod_at.cfg 中对应的访问权限提升条目。
 */
@Mixin(EntityAINearestAttackableTarget.class)
public interface AccessorEntityAINearestAttackableTarget {

    /** @return the reciprocal chance of selecting a target. 目标选取概率的倒数。 */
    @Accessor("targetChance")
    int getTargetChance();

    /** @return the currently selected target. 当前选中的目标。 */
    @Accessor("targetEntity")
    EntityLivingBase getTargetEntity();

    /**
     * Sets the currently selected target.
     * <p>
     * 设置当前选中的目标。
     */
    @Accessor("targetEntity")
    void setTargetEntity(EntityLivingBase target);
}
