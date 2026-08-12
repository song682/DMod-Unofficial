package makamys.dmod.bundle;

/**
 * 16 种 bundle 染色的单一数据源 (Single source of truth for the 16 bundle dye colors).
 * <p>
 * The enum ordinal doubles as the item metadata (bundle meta = modern 1.8+ color
 * ordering: 0=white ... 15=black), so ordinal order MUST NOT be changed — old
 * saves identify colors by metadata.
 * <p>
 * 枚举 ordinal 即物品 metadata（bundle meta 采用 1.8+ 新颜色序：0=white ... 15=black），
 * 因此枚举顺序严禁改动——旧存档通过 metadata 识别颜色。
 * <p>
 * The dye mapping below follows the vanilla legacy dye order -> modern color
 * order conversion. Note this FIXES a mapping bug in the old DModItems dye
 * recipes where dyes 7/8/9 (light_gray / gray / pink) produced the wrong colors.
 * <p>
 * 染料映射采用原版旧序染料 -> 新序颜色的标准转换。注意：这修复了旧 DModItems
 * 染色配方中 7/8/9 号染料（light_gray / gray / pink）产出颜色错位的 bug。
 */
public enum BundleColor {

    WHITE("white", 15),        // meta 0  <- dye 15 (white)
    ORANGE("orange", 14),      // meta 1  <- dye 14 (orange)
    MAGENTA("magenta", 13),    // meta 2  <- dye 13 (magenta)
    LIGHT_BLUE("light_blue", 12), // meta 3 <- dye 12 (light blue)
    YELLOW("yellow", 11),      // meta 4  <- dye 11 (yellow)
    LIME("lime", 10),          // meta 5  <- dye 10 (lime)
    PINK("pink", 9),           // meta 6  <- dye 9  (pink)
    GRAY("gray", 8),           // meta 7  <- dye 8  (gray)
    LIGHT_GRAY("light_gray", 7), // meta 8 <- dye 7  (light gray)
    CYAN("cyan", 6),           // meta 9  <- dye 6  (cyan)
    PURPLE("purple", 5),       // meta 10 <- dye 5  (purple)
    BLUE("blue", 4),           // meta 11 <- dye 4  (blue)
    BROWN("brown", 3),         // meta 12 <- dye 3  (brown)
    GREEN("green", 2),         // meta 13 <- dye 2  (green)
    RED("red", 1),             // meta 14 <- dye 1  (red)
    BLACK("black", 0);         // meta 15 <- dye 0  (black)

    /** Texture prefix for this color, e.g. "white" -> white_bundle.png */
    private final String texturePrefix;

    /** Legacy vanilla dye metadata (0-15) used in dye recipes */
    private final int dyeMeta;

    BundleColor(String texturePrefix, int dyeMeta) {
        this.texturePrefix = texturePrefix;
        this.dyeMeta = dyeMeta;
    }

    /**
     * 返回该颜色对应的物品 metadata (new color ordering, equals ordinal).
     *
     * @return bundle metadata for this color (0-15)
     */
    public int getBundleMeta() {
        return ordinal();
    }

    /**
     * 返回旧序染料 metadata，用于染色配方输入。
     *
     * @return legacy dye metadata (0-15)
     */
    public int getDyeMeta() {
        return dyeMeta;
    }

    /**
     * 返回纹理前缀，如 "white"。
     *
     * @return texture name prefix
     */
    public String getTexturePrefix() {
        return texturePrefix;
    }

    /**
     * 从物品 damage 解析颜色（低 4 位），越界回退白色。
     *
     * @param damage item damage
     * @return the color for the given damage
     */
    public static BundleColor fromDamage(int damage) {
        int index = damage & 0xF;
        BundleColor[] values = values();
        return index >= 0 && index < values.length ? values[index] : WHITE;
    }
}
