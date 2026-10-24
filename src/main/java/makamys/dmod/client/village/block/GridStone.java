package makamys.dmod.client.village.block;

import static makamys.dmod.DModConstants.MODID;

import java.util.HashMap;
import java.util.Map;

import decok.dfcdvadstf.catframe.model.impl.ModernBlock;
import decok.dfcdvadstf.catframe.model.state.BlockStateDefinition;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import decok.dfcdvadstf.catframe.model.state.property.StateDefinitions;
import makamys.dmod.DGuiHandler;
import makamys.dmod.DMod;
import makamys.dmod.client.DModTabs;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Grindstone block — ported from 26.1.2 {@code GrindstoneBlock}.
 * <p>
 * Attach face (floor / wall / ceiling) and horizontal facing are packed into
 * the 4-bit metadata as {@code face << 2 | facing}, matching the borrowed
 * high-version {@code blockstates/grindstone.json} (which declares the
 * {@code face} and {@code facing} variants in this order).
 * <p>
 * The typed {@link BlockStateDefinition} is what makes the rotation-bearing
 * variants work: CatFrame's model pipeline resolves the variant through the
 * state definition and bakes it with the variant's {@code x} / {@code y}
 * rotation, so the wall ({@code x: 90}) and ceiling ({@code x: 180}) shapes
 * are drawn exactly as in the high version.
 */
public class GridStone extends ModernBlock {

    // Attach face, index order matches the "face" property values below.
    public static final int FACE_FLOOR = 0;
    public static final int FACE_WALL = 1;
    public static final int FACE_CEILING = 2;

    // Horizontal facing, index order matches the "facing" property values below.
    public static final int FACING_NORTH = 0;
    public static final int FACING_EAST = 1;
    public static final int FACING_SOUTH = 2;
    public static final int FACING_WEST = 3;

    private static final String[] FACE_NAMES = {"floor", "wall", "ceiling"};
    private static final String[] FACING_NAMES = {"north", "east", "south", "west"};

    /** 1.7.10 yaw quadruple (0 = south, 1 = west, 2 = north, 3 = east) to our facing index. */
    private static final int[] YAW_TO_FACING = {
            FACING_SOUTH, FACING_WEST, FACING_NORTH, FACING_EAST
    };

    public static final Property<String> FACE = StateDefinitions.stringProp("face", FACE_NAMES);
    public static final Property<String> FACING = StateDefinitions.stringProp("facing", FACING_NAMES);

    private final BlockStateDefinition<GridStone> stateDefinition;

    public GridStone() {
        super(Material.rock);
        this.setBlockName("dmod.gridstone");
        // Fallback icon (break particles / textures missing from a resource pack);
        // the visible geometry comes from the JSON model below.
        this.setBlockTextureName("dmod:grindstone_side");
        this.setBlockstate(MODID, "grindstone");
        this.setHardness(2.0F);
        this.setResistance(6.0F);
        this.setStepSound(soundTypeStone);
        this.setHarvestLevel("pickaxe", 0);
        this.setLightOpacity(0);
        this.setCreativeTab(DModTabs.dmodTabInstance);

        this.stateDefinition = new BlockStateDefinition.Builder<GridStone>(this)
                .add(FACE, FACING)
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
        props.put(FACE.getName(), FACE_NAMES[getFace(metadata)]);
        props.put(FACING.getName(), FACING_NAMES[getFacing(metadata)]);
        return props;
    }

    // ==================== Metadata <-> face / facing ====================

    public static int getFace(int metadata) {
        int face = (metadata >> 2) & 3;
        return face > FACE_CEILING ? FACE_FLOOR : face;
    }

    public static int getFacing(int metadata) {
        return metadata & 3;
    }

    public static int getMetadata(int face, int facing) {
        return ((face & 3) << 2) | (facing & 3);
    }

    // ==================== Placement ====================

    /**
     * Mirrors 26.1.2 {@code FaceAttachedHorizontalDirectionalBlock#getStateForPlacement}:
     * the dominant look direction picks the attach face, and a wall mount faces
     * away from the player while a floor / ceiling mount faces with the player.
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z, getMetadataForPlacement(placer), 3);
    }

    private static int getMetadataForPlacement(EntityLivingBase placer) {
        int yawIndex = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int lookFacing = YAW_TO_FACING[yawIndex];

        if (placer.rotationPitch > 45.0F) {
            return getMetadata(FACE_FLOOR, lookFacing);
        } else if (placer.rotationPitch < -45.0F) {
            return getMetadata(FACE_CEILING, lookFacing);
        } else {
            // Wall mount: the model faces away from the wall the player looks at.
            return getMetadata(FACE_WALL, (lookFacing + 2) & 3);
        }
    }

    // ==================== Shape ====================

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        AxisAlignedBB bounds = getStateBounds(world.getBlockMetadata(x, y, z));
        this.setBlockBounds((float) bounds.minX, (float) bounds.minY, (float) bounds.minZ,
                (float) bounds.maxX, (float) bounds.maxY, (float) bounds.maxZ);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        AxisAlignedBB bounds = getStateBounds(world.getBlockMetadata(x, y, z));
        return AxisAlignedBB.getBoundingBox(bounds.minX + x, bounds.minY + y, bounds.minZ + z,
                bounds.maxX + x, bounds.maxY + y, bounds.maxZ + z);
    }

    /**
     * Approximates the high-version shape set: the model spans x / z 2..14 and
     * reaches full height, while a wall mount extends through the full block
     * depth along its facing axis (it is attached to a wall).
     */
    private static AxisAlignedBB getStateBounds(int metadata) {
        double minX = 2.0D / 16.0D;
        double minZ = 2.0D / 16.0D;
        double maxX = 14.0D / 16.0D;
        double maxZ = 14.0D / 16.0D;
        double minY = 0.0D;
        double maxY = 1.0D;

        if (getFace(metadata) == FACE_WALL) {
            minY = 2.0D / 16.0D;
            maxY = 14.0D / 16.0D;
            int facing = getFacing(metadata);
            if (facing == FACING_NORTH || facing == FACING_SOUTH) {
                minZ = 0.0D;
                maxZ = 1.0D;
            } else {
                minX = 0.0D;
                maxX = 1.0D;
            }
        }

        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ==================== Rendering / interaction ====================

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
            float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(DMod.instance, DGuiHandler.GRIDSTONE_GUI_ID, world, x, y, z);
        }
        return true;
    }
}
