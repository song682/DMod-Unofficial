package makamys.dmod.compat;

import ganymedes01.etfuturum.ModBlocks;

/**
 * Et Futurum Requiem banner integration. Referencing this class loads EFR
 * types, so only call it after {@link EFRCompat#isLoaded()} returned
 * {@code true}.
 */
public final class EFRBannerCompat {

    private EFRBannerCompat() {
    }

    /**
     * @return whether EFR registered its banner block. EFR's own feature toggle
     *         can disable banners even when the mod is present.
     */
    public static boolean isBannerAvailable() {
        return ModBlocks.BANNER != null
                && ModBlocks.BANNER.isEnabled()
                && ModBlocks.BANNER.get() != null;
    }
}
