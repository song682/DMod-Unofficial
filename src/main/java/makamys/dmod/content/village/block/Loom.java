package makamys.dmod.content.village.block;

import static makamys.dmod.init.DModConstants.MODID;

import java.util.HashMap;
import java.util.Map;

import decok.dfcdvadstf.catframe.model.impl.ModernBlock;
import decok.dfcdvadstf.catframe.model.state.BlockStateDefinition;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import decok.dfcdvadstf.catframe.model.state.property.StateDefinitions;
import makamys.dmod.DGuiHandler;
import makamys.dmod.DMod;
import makamys.dmod.content.DModTabs;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Loom block — ported from 26.1.2 {@code LoomBlock}.
 * <p>
 * The four horizontal facings are packed into the low two metadata bits, with
 * {@code 0..3} mapping to north / east / south / west. The front face is placed
 * towards the player, mirroring the high-version
 * {@code getStateForPlacement} ({@code getHorizontalDirection().getOpposite()}).
 */
public class Loom extends ModernBlock {

    public static final int FACING_NORTH = 0;
    public static final int FACING_EAST = 1;
    public static final int FACING_SOUTH = 2;
    public static final int FACING_WEST = 3;

    private static final String[] FACING_NAMES = {"north", "east", "south", "west"};

    /** 1.7.10 yaw quadruple (0 = south, 1 = west, 2 = north, 3 = east) to front-facing index. */
    private static final int[] YAW_TO_FACING = {
            FACING_NORTH, FACING_EAST, FACING_SOUTH, FACING_WEST
    };

    public static final Property<String> FACING = StateDefinitions.stringProp("facing", FACING_NAMES);

    private final BlockStateDefinition<Loom> stateDefinition;

    public Loom() {
        super(Material.wood);
        this.setBlockName("dmod.loom");
        // Fallback icon (break particles / when the JSON model cannot be resolved).
        this.setBlockTextureName("dmod:loom_front");
        this.setBlockstate(MODID, "loom");
        this.setHardness(2.5F);
        this.setResistance(2.5F);
        this.setStepSound(soundTypeWood);
        this.setHarvestLevel("axe", 0);
        this.setCreativeTab(DModTabs.dmodTabInstance);

        this.stateDefinition = new BlockStateDefinition.Builder<Loom>(this)
                .add(FACING)
                .create();
    }

    @Override
    public BlockStateDefinition<?> getStateDefinition() {
        return stateDefinition;
    }

    /**
     * Legacy property fallback for the model pipeline: identical mapping to the
     * typed definition, so rendering stays correct even if the typed definition
     * was not registered on the client yet.
     */
    @Override
    public Map<String, String> getStateProperties(IBlockAccess world, int x, int y, int z, int metadata) {
        Map<String, String> props = new HashMap<String, String>();
        props.put(FACING.getName(), FACING_NAMES[getFacing(metadata)]);
        return props;
    }

    // ==================== Metadata <-> facing ====================

    public static int getFacing(int metadata) {
        return metadata & 3;
    }

    // ==================== Placement ====================

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z, getMetadataForPlacement(placer), 3);
    }

    private static int getMetadataForPlacement(EntityLivingBase placer) {
        int yawIndex = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        return YAW_TO_FACING[yawIndex];
    }

    // ==================== GUI ====================

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(DMod.instance, DGuiHandler.LOOM_GUI_ID, world, x, y, z);
        }
        return true;
    }
}
