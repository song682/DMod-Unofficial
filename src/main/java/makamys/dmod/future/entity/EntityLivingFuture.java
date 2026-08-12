package makamys.dmod.future.entity;

import makamys.dmod.future.entity.passive.EntityAnimalFuture;
import makamys.dmod.future.item.ItemStackFuture;
import makamys.dmod.mixin.AccessorItemFood;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class EntityLivingFuture {
    public static ItemStack eatFood(EntityLivingBase dis, World world, ItemStack stack) {
        if(dis instanceof EntityAnimalFuture) {
            return ((EntityAnimalFuture)dis).eatFood(world, stack);
        } else {
            return eatFoodBody(dis, world, stack);
        }
    }
    
    public static ItemStack eatFoodBody(EntityLivingBase dis, World world, ItemStack stack) {
        if (stack.getItem() instanceof ItemFood) {
            world.playSound(dis.posX, dis.posY, dis.posZ, ((EntityLivingFutured)dis).getEatSound(stack), 1.0F, 1.0F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.4F, false);
            EntityLivingFuture.applyFoodEffects(dis, stack, world, dis);
            if (!(dis instanceof EntityPlayer) || !((EntityPlayer) dis).capabilities.isCreativeMode) {
                ItemStackFuture.decrement(stack, 1);
            }
        }
        return stack;
    }
    
    public static void applyFoodEffects(EntityLivingBase dis, ItemStack stack, World world, EntityLivingBase targetEntity) {
        Item item = stack.getItem();
        if (item instanceof ItemFood) {
            ItemFood food = (ItemFood)item;
            // Access the private potion fields through the mixin accessor (replaces dmod_at.cfg entries).
            // 通过 mixin accessor 访问私有药水字段（替代 dmod_at.cfg 条目）。
            AccessorItemFood foodAccessor = (AccessorItemFood)food;
            if (!world.isRemote && foodAccessor.getPotionId() > 0 && world.rand.nextFloat() < foodAccessor.getPotionEffectProbability())
            {
                dis.addPotionEffect(new PotionEffect(foodAccessor.getPotionId(), foodAccessor.getPotionDuration() * 20, foodAccessor.getPotionAmplifier()));
            }
        }

    }
}
