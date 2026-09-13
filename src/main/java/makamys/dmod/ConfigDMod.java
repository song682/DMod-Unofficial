package makamys.dmod;

import static makamys.dmod.DModConstants.LOGGER;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.EnumUtils;


import makamys.mclib.config.item.BackpackConfigHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;

public class ConfigDMod {
    

    public static boolean enableBundle;



    public static List<Item> bundleCraftingItems;
    public static boolean compactBundleGUI;
    public static boolean modernBundle;
    public static boolean showBundleFullness;
    public static boolean durabilityBarColor;
    
    public static BackpackConfigHelper backpackHelper;
    
    private static List<Item> resolveItemListOrDefault(Configuration config, String propName, String propCat, String[] propDefault, String propComment, Item... defaults){
        String[] list = config.getStringList(propName, propCat, propDefault, propComment);
        List<Item> items = new ArrayList<>();
        for(String itemStr : list) {
            Object itemObj = Item.itemRegistry.getObject(itemStr);
            if(itemObj != null) {
                items.add((Item)itemObj);
            }
        }
        if(items.isEmpty() && list.length > 0) {
            LOGGER.debug("Couldn't resolve any of the items in " + propCat + "." + propName + ", falling back to defaults");
            items = Arrays.asList(defaults);
        }
        
        LOGGER.debug("Resolved " + propCat + "." + propName + " to " + items.stream().map(i -> i.getUnlocalizedName()).collect(Collectors.toList()));
        return items;
    }
    
    // nice copypasta
    private static List<Class<Entity>> resolveEntityClassListOrDefault(Configuration config, String propName, String propCat, String[] propDefault, String propComment, Class<Entity>... defaults){
        String[] list = config.getStringList(propName, propCat, propDefault, propComment);
        List<Class<Entity>> items = new ArrayList<>();
        for(String itemStr : list) {
            Object itemObj = EntityList.stringToClassMapping.get(itemStr);
            if(itemObj != null) {
                items.add((Class<Entity>)itemObj);
            }
        }
        if(items.isEmpty() && list.length > 0) {
            LOGGER.debug("Couldn't resolve any of the entity names in " + propCat + "." + propName + ", falling back to defaults");
            items = Arrays.asList(defaults);
        }
        
        LOGGER.debug("Resolved " + propCat + "." + propName + " to " + items.stream().map(e -> EntityList.classToStringMapping.get(e)).collect(Collectors.toList()));
        return items;
    }
    
    private static <E extends Enum> E getEnum(Configuration config, String propName, String propCat, E propDefault, String propComment) {
        return getEnum(config, propName, propCat, propDefault, propComment, false);
    }
    
    private static <E extends Enum> E getEnum(Configuration config, String propName, String propCat, E propDefault, String propComment, boolean lowerCase) {
        Map enumMap = EnumUtils.getEnumMap(propDefault.getClass());
        String[] valuesStr = (String[])enumMap.keySet().toArray(new String[]{});
        String defaultString = propDefault.toString();
        if(lowerCase) defaultString = defaultString.toLowerCase();
        return (E)enumMap.get(config.getString(propName, propCat, defaultString, propComment, valuesStr).toUpperCase());
    }
    
    public static void reload() {
        reload(false);
    }
    
    public static void reload(boolean early) {
        LOGGER.debug("Loading config (" + (early ? "Early" : "Late") + ")");
        
        File configFile = new File(Launch.minecraftHome, "config/dmod.cfg");
        Configuration config = new Configuration(configFile);
        
        config.load();
        
        enableBundle = config.getBoolean("enableBundle", "_features", true, "");
        
        durabilityBarColor = config.getBoolean("durabilityBarColor", "Mixins", true, "Change the durability bar color of certain items (bundles)");
        
        // Deprecated: kept only for config-file compatibility (the unified
        // BundleTooltipRenderer no longer consumes this key).
        compactBundleGUI = config.getBoolean("compactBundleGUI", "bundle", false, "Remove extra spacing between rows in the bundle tooltip.");
        modernBundle = enableBundle && config.getBoolean("modernBundle", "bundle", false, "Render the bundle with the modern (1.21+) style: colored capacity bar on the item (blue while filling, red when full), and unlocks other modern-style behaviors. Requires enableBundle=true. See https://minecraft.wiki/w/Bundle.");
        showBundleFullness = config.getBoolean("showBundleFullness", "bundle", false, "Show the occupancy count (x/64) text line in the bundle tooltip. Hidden by default to match the modern (1.21+) tooltip look.");
        
        if(!early) {
            bundleCraftingItems = 
                    resolveItemListOrDefault(config, "bundleCraftingItems", "bundle", new String[]{"etfuturum:rabbit_hide"}, "Falls back to leather if none of the items can be resolved", Items.leather);
            backpackHelper = new BackpackConfigHelper(Arrays.asList(config.getStringList("bundleItemBlacklist", "bundle", Stream.of(
                    new String[]{"etfuturum:shulker_box"},
                    BackpackConfigHelper.NON_NESTABLE_BACKPACK_BLACKLIST).flatMap(Stream::of).toArray(String[]::new),
                    "Items that aren't allowed in bundles" + BackpackConfigHelper.CONFIG_DESCRIPTION_SUFFIX)));
        }
        
        if (config.hasChanged()) 
        {
            config.save();
        }
    }
    

    
    public static enum ForceableBoolean { TRUE, FALSE, FORCE }
    
}
