package makamys.dmod.future.item;

import java.util.Arrays;
import java.util.List;

import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import makamys.dmod.mixin.AccessorItemFood;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public abstract class ItemFuture extends Item implements IItemFuture {

    public boolean onStackClicked(ItemStack stack, Slot slot, int button, EntityPlayer player) {
        return false;
    }

    public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, int button, EntityPlayer player) {
        return false;
    }

    public static ItemStack finishUsing(Item dis, ItemStack stack, World world, EntityLivingBase user) {
        if (dis instanceof ItemFood) {
            ItemFood food = (ItemFood) dis;
            world.playSound(user.posX, user.posY, user.posZ, "random.eat", 1.0F, 1.0F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.4F, false);
            AccessorItemFood foodAccessor = (AccessorItemFood) food;
            if (!world.isRemote && foodAccessor.getPotionId() > 0 && world.rand.nextFloat() < foodAccessor.getPotionEffectProbability()) {
                user.addPotionEffect(new PotionEffect(foodAccessor.getPotionId(), foodAccessor.getPotionDuration() * 20, foodAccessor.getPotionAmplifier()));
            }
            if (!(user instanceof EntityPlayer) || !((EntityPlayer) user).capabilities.isCreativeMode) {
                ItemStackFuture.decrement(stack, 1);
            }
        }
        return stack;
    }

    public static boolean canBeNested(Item dis) {
        return true; // TODO don't allow shulker box, configurable blacklist?
    }

    public boolean getItemBarHasColor(ItemStack stack) {
        return false;
    }

    public int getItemBarColor(ItemStack stack) {
        return 0x00FF00;
    }

    @SideOnly(Side.CLIENT)
    public void appendTooltip(ItemStack stack, World world, List<String> tooltip) {}

    @cpw.mods.fml.common.Optional.Method(modid = "CodeChickenCore")
    @SideOnly(Side.CLIENT)
    public List<ITooltipLineHandler> getTooltipHandlers(ItemStack stack){
        return Arrays.asList();
    }

}
