package makamys.dmod.compat;

import static makamys.dmod.DModConstants.LOGGER;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.qzz.dfdvdsf.jarfile.JarVersionGuesser;
import net.minecraft.launchwrapper.Launch;

/**
 * Early (mixin {@code onLoad}-time) detection of the GTNH fork of Et Futurum
 * Requiem.
 * <p>
 * The GTNH fork ships its own foxes plus {@code MixinEntityLivingBase} /
 * {@code MixinEntityWolf} that overlap DMod's fox mixins. When it is present,
 * DMod's foxes must stay completely off (mixins unapplied, entities
 * unregistered). That decision has to be made at
 * {@link makamys.dmod.MixinConfigPlugin#onLoad(String)} time, which is far
 * earlier than FML's {@code Loader} is usable, so detection works purely from
 * the jar file names in the mods directory via {@link JarVersionGuesser}: the
 * GTNH fork is published as {@code etfuturum-<version>-GTNH.jar}, whereas the
 * official build ({@code etfuturum-2.6.2.jar}) carries no {@code -GTNH} suffix.
 * <p>
 * jarutils is installed separately (it is not repackaged into DMod's jar), so it
 * may not be on the classpath this early; {@link #ensureJarUtilsOnClasspath}
 * adds it via {@code LaunchClassLoader.addURL} when needed.
 * <p>
 * Everything here is fail-safe: any {@link Throwable} is swallowed and treated
 * as "not detected", so a detection problem can never crash the game. This is
 * the deliberate replacement for an earlier filename-based detector that was
 * abandoned after it caused startup crashes.
 */
public class EtFuturumGTNHDetector {

    private static final String ETFUTURUM_JAR_NAME = "etfuturum";
    private static final String GTNH_VERSION_MARKER = "-gtnh";

    /** Cached result; {@code null} until the first successful (or failed) scan. */
    private static Boolean gtnhPresent = null;

    private EtFuturumGTNHDetector() {
        // Static utility, no instances.
    }

    /**
     * Whether the GTNH fork of Et Futurum Requiem is present in the mods
     * directory. The scan runs once and the result is cached. Never throws.
     *
     * @return {@code true} only if a {@code etfuturum-*-GTNH} jar was found
     */
    public static boolean isGTNHPresent() {
        if (gtnhPresent == null) {
            boolean result;
            try {
                result = detect();
            } catch (Throwable t) {
                // Never let detection crash the game; assume "not present" so
                // foxes follow the user's config instead.
                LOGGER.warn("Et Futurum Requiem (GTNH) detection failed; assuming not present.", t);
                result = false;
            }
            gtnhPresent = result;
        }
        return gtnhPresent;
    }

    private static boolean detect() throws Exception {
        File modsDir = new File(Launch.minecraftHome, "mods");
        if (!modsDir.isDirectory()) {
            return false;
        }

        List<File> jars = listModJars(modsDir);
        // jarutils is installed separately and may not be reachable this early;
        // make sure JarVersionGuesser can be loaded before we call it.
        ensureJarUtilsOnClasspath(jars);

        for (File jar : jars) {
            JarVersionGuesser.Guess guess = JarVersionGuesser.guess(jar.getName());
            String name = guess.name();
            String version = guess.version();
            if (name != null && version != null
                    && name.equalsIgnoreCase(ETFUTURUM_JAR_NAME)
                    && version.toLowerCase(Locale.ROOT).contains(GTNH_VERSION_MARKER)) {
                LOGGER.info("Detected GTNH fork of Et Futurum Requiem (" + jar.getName()
                        + ", version " + version + "); DMod foxes will be disabled.");
                return true;
            }
        }
        return false;
    }

    private static List<File> listModJars(File modsDir) throws Exception {
        try (Stream<Path> walk = Files.walk(modsDir.toPath())) {
            return walk.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(EtFuturumGTNHDetector::isJar)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static boolean isJar(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".jar") || n.endsWith(".zip");
    }

    /**
     * Makes jarutils reachable on the {@link Launch} class loader if it is not
     * already, by locating its jar among the scanned mods and calling
     * {@code addURL}. The jar is identified by file name (the published artifact
     * is {@code jar-utils-*.jar}); this runs before any {@link JarVersionGuesser}
     * reference so the class is resolved from the completed classpath.
     */
    private static void ensureJarUtilsOnClasspath(List<File> jars) {
        for (File jar : jars) {
            String n = jar.getName().toLowerCase(Locale.ROOT);
            if (n.contains("jar-utils") || n.contains("jarutils")) {
                try {
                    URL url = jar.toURI().toURL();
                    if (!isOnClasspath(url)) {
                        Launch.classLoader.addURL(url);
                        LOGGER.info("Added " + jar.getName() + " to the classpath for early GTNH detection.");
                    }
                    return;
                } catch (Exception e) {
                    LOGGER.warn("Failed to add " + jar.getName() + " to the classpath.", e);
                }
            }
        }
        // Not finding the jar here is normal in a dev workspace (jarutils sits on
        // the classpath via lib/) and whenever mods/ holds no jars; a genuine
        // failure instead surfaces as a throw from JarVersionGuesser, caught in
        // isGTNHPresent(). So this is informational, not a warning.
        LOGGER.debug("jarutils jar not found in the mods directory; assuming it is already on the classpath.");
    }

    private static boolean isOnClasspath(URL url) {
        for (URL existing : Launch.classLoader.getURLs()) {
            if (existing.sameFile(url)) {
                return true;
            }
        }
        return false;
    }
}
