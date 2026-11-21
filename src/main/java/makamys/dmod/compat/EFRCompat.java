package makamys.dmod.compat;

import cpw.mods.fml.common.Loader;

/**
 * Et Futurum Requiem presence check. This class intentionally references no EFR
 * types, so it can run safely when the mod is absent.
 */
public final class EFRCompat {

    public static final String MOD_ID = "etfuturum";

    private EFRCompat() {
    }

    public static boolean isLoaded() {
        return Loader.isModLoaded(MOD_ID);
    }
}
