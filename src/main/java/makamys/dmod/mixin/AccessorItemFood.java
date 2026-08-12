package makamys.dmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.item.ItemFood;

/**
 * Accessor interface for the private potion fields of ItemFood.
 * Replaces the corresponding entries in dmod_at.cfg (field_77851_ca / field_77850_cb / field_77858_cd / field_77857_cc).
 * <p>
 * 访问 ItemFood 私有药水字段的 Accessor 接口，替代 dmod_at.cfg 中对应的访问权限提升条目。
 */
@Mixin(ItemFood.class)
public interface AccessorItemFood {

    /** @return the potion effect id of the food. 食物的药水效果 ID。 */
    @Accessor("potionId")
    int getPotionId();

    /** @return the potion effect duration of the food. 食物的药水效果持续时间。 */
    @Accessor("potionDuration")
    int getPotionDuration();

    /** @return the potion effect amplifier of the food. 食物的药水效果等级。 */
    @Accessor("potionAmplifier")
    int getPotionAmplifier();

    /** @return the probability of the potion effect occurring. 药水效果触发概率。 */
    @Accessor("potionEffectProbability")
    float getPotionEffectProbability();
}
