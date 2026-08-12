package makamys.dmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.passive.EntityAnimal;

/**
 * Accessor interface for the private members of EntityAIMate.
 * Replaces the corresponding entries in dmod_at.cfg (func_75388_i / field_75390_d / field_75391_e).
 * <p>
 * 访问 EntityAIMate 私有成员的 Accessor 接口，替代 dmod_at.cfg 中对应的访问权限提升条目。
 */
@Mixin(EntityAIMate.class)
public interface AccessorEntityAIMate {

    /** @return the animal this AI operates on. 执行本 AI 的动物。 */
    @Accessor("theAnimal")
    EntityAnimal getTheAnimal();

    /** @return the animal that was chosen as the mate. 被选为配偶的动物。 */
    @Accessor("targetMate")
    EntityAnimal getTargetMate();

    /**
     * Invokes the vanilla baby spawning logic of EntityAIMate.
     * <p>
     * 调用 EntityAIMate 原版的繁殖生成幼崽逻辑。
     */
    @Invoker("spawnBaby")
    void invokeSpawnBaby();
}
