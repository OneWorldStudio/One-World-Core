/*
 * Mohist - OneWorldCore
 * Copyright (C) 2018-2024.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.oneworldstudiomc;

import com.oneworldstudiomc.action.v_1_20_1;
import com.oneworldstudiomc.config.OneWorldCoreConfigUtil;
import com.oneworldstudiomc.feature.AutoDeleteMods;
import com.oneworldstudiomc.feature.CustomLibraries;
import com.oneworldstudiomc.feature.DefaultLibraries;
import com.oneworldstudiomc.feature.ExceptionHandler;
import com.mohistmc.i18n.i18n;
import com.mohistmc.tools.JarTool;
import com.mohistmc.tools.MojangEulaUtil;
import com.mohistmc.tools.ZipUtil;
import com.oneworldstudiomc.util.DataParser;
import com.oneworldstudiomc.util.MohistModuleManager;
import cpw.mods.bootstraplauncher.BootstrapLauncher;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

public class OneWorldCoreStart {
    private static final String LEGACY_CLASS_PATH_PREFIX = "-DlegacyClassPath=";
    private static final Set<String> FILTERED_LEGACY_MODULES = Set.of("org.yaml.snakeyaml");

    private static final String ANSI_RESET = "\u001B[0m";
    private static final Map<Character, String[]> LOGO_GLYPHS = Map.of(
            ' ', new String[]{"   ", "   ", "   ", "   ", "   ", "   ", "   "},
            'O', new String[]{" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "},
            'N', new String[]{"#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #"},
            'E', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"},
            'W', new String[]{"#   #", "#   #", "#   #", "# # #", "# # #", "## ##", "#   #"},
            'R', new String[]{"#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"},
            'L', new String[]{"#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"},
            'D', new String[]{"#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "},
            'C', new String[]{" ####", "#    ", "#    ", "#    ", "#    ", "#    ", " ####"}
    );
    private static final String STARTUP_BANNER = buildStartupBanner();

    public static final List<String> mainArgs = new ArrayList<>();
    public static String MCVERSION;
    public static i18n i18n;
    public static JarTool jarTool;

    public static String getVersion() {
        return (OneWorldCoreStart.class.getPackage().getImplementationVersion() != null) ? OneWorldCoreStart.class.getPackage().getImplementationVersion() : "unknown";
    }

    public static void main(String[] args) throws Exception {
        mainArgs.addAll(List.of(args));
        jarTool = new JarTool(OneWorldCoreStart.class);
        DataParser.parseVersions();
        DataParser.parseLaunchArgs();
        sanitizeLaunchArgs();
        OneWorldCoreConfigUtil.init();
        OneWorldCoreConfigUtil.i18n();
        if (i18n.isCN()) {
            Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
        }

        boolean showLogo = OneWorldCoreConfigUtil.aBoolean("oneworldcore.show_logo",
                OneWorldCoreConfigUtil.aBoolean("oneworldstudio.show_logo", OneWorldCoreConfigUtil.aBoolean("mohist.show_logo", true)));
        if (OneWorldCoreConfigUtil.INSTALLATIONFINISHED() && showLogo) {
            String welcomeMessage = resolveWelcomeMessage();
            System.out.printf("%n%s%n%s - %s, Java(%s) %s PID: %s%n",
                    STARTUP_BANNER,
                    welcomeMessage,
                    getVersion(),
                    System.getProperty("java.class.version"),
                    System.getProperty("java.version"),
                    ManagementFactory.getRuntimeMXBean().getName().split("@")[0]
            );
            System.out.println("All Rights Reserved - One World Studio 2019-2026");
            if (i18n.isCN()) {
                System.out.println("+------------------------------------------------------+");
                System.out.println("|                                                      |");
                System.out.println("| Official site: https://oneworldstudio.dev           |");
                System.out.println("| Author: One World Studio [Unitei]                   |");
                System.out.println("|                                                      |");
                System.out.println("+------------------------------------------------------+");
            }
        }

        if (System.getProperty("log4j.configurationFile") == null) {
            System.setProperty("log4j.configurationFile", "log4j2_oneworldstudio.xml");
        }

        ZipUtil.getFileContent(OneWorldCoreStart.class.getClassLoader().getResourceAsStream("META-INF/libraries"));
        if (OneWorldCoreConfigUtil.INSTALLATIONFINISHED() && OneWorldCoreConfigUtil.CHECK_LIBRARIES()) {
            DefaultLibraries.run();
        }

        CustomLibraries.loadCustomLibs();
        if (OneWorldCoreConfigUtil.INSTALLATIONFINISHED()) {
            v_1_20_1.run();
        }

        AutoDeleteMods.jar();

        List<String> forgeArgs = new ArrayList<>();
        for (String arg : DataParser.launchArgs.stream().filter(s ->
                        s.startsWith("--launchTarget")
                                || s.startsWith("--fml.forgeVersion")
                                || s.startsWith("--fml.mcVersion")
                                || s.startsWith("--fml.forgeGroup")
                                || s.startsWith("--fml.mcpVersion"))
                .toList()) {
            forgeArgs.add(arg.split(" ")[0]);
            forgeArgs.add(arg.split(" ")[1]);
        }
        new MohistModuleManager(DataParser.launchArgs);

        if (!MojangEulaUtil.hasAcceptedEULA()) {
            System.out.println(i18n.as("eula"));
            while (!"true".equals(new Scanner(System.in).nextLine().trim())) {
                System.out.println(i18n.as("eula_notrue"));
            }
            MojangEulaUtil.writeInfos(i18n.as("eula.text", "https://account.mojang.com/documents/minecraft_eula") + "\n" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "\neula=true");
        }
        String[] args_ = Stream.concat(forgeArgs.stream(), mainArgs.stream()).toArray(String[]::new);
        BootstrapLauncher.main(args_);
    }

    private static void sanitizeLaunchArgs() {
        sanitizeLegacyClasspathModules();
    }

    private static void sanitizeLegacyClasspathModules() {
        int legacyClasspathIndex = -1;
        for (int i = 0; i < DataParser.launchArgs.size(); i++) {
            if (DataParser.launchArgs.get(i).startsWith(LEGACY_CLASS_PATH_PREFIX)) {
                legacyClasspathIndex = i;
                break;
            }
        }

        if (legacyClasspathIndex < 0) {
            return;
        }

        Set<String> extractedModules = new HashSet<>();
        Path extractedLibsDir = Path.of("libs");
        if (Files.isDirectory(extractedLibsDir)) {
            extractedModules.addAll(findMatchingModulesInDirectory(extractedLibsDir, FILTERED_LEGACY_MODULES));
        }
        Path modsDir = Path.of("mods");
        if (Files.isDirectory(modsDir)) {
            extractedModules.addAll(findMatchingEmbeddedModulesInMods(modsDir, FILTERED_LEGACY_MODULES));
        }
        if (extractedModules.isEmpty()) {
            return;
        }

        String legacyClasspath = DataParser.launchArgs.get(legacyClasspathIndex).substring(LEGACY_CLASS_PATH_PREFIX.length());
        List<String> sanitizedEntries = new ArrayList<>();
        List<String> removedEntries = new ArrayList<>();

        for (String entry : legacyClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String trimmedEntry = entry.trim();
            if (trimmedEntry.isEmpty()) {
                continue;
            }

            Path entryPath = Path.of(trimmedEntry);
            String moduleName = findModuleName(entryPath).orElse(null);
            if (moduleName != null && extractedModules.contains(moduleName)) {
                removedEntries.add(trimmedEntry);
                continue;
            }

            sanitizedEntries.add(trimmedEntry);
        }

        if (removedEntries.isEmpty()) {
            return;
        }

        DataParser.launchArgs.set(legacyClasspathIndex, LEGACY_CLASS_PATH_PREFIX + String.join(File.pathSeparator, sanitizedEntries));
        System.out.println("Filtered duplicate legacy modules: " + String.join(", ", extractedModules));
    }

    private static Set<String> findMatchingModulesInDirectory(Path root, Set<String> moduleNames) {
        Set<String> matches = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .map(OneWorldCoreStart::findModuleName)
                    .flatMap(Optional::stream)
                    .filter(moduleNames::contains)
                    .forEach(matches::add);
        } catch (IOException ignored) {
        }
        return matches;
    }

    private static Set<String> findMatchingEmbeddedModulesInMods(Path modsDir, Set<String> moduleNames) {
        Set<String> matches = new HashSet<>();
        try (Stream<Path> paths = Files.walk(modsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .forEach(path -> inspectEmbeddedModules(path, moduleNames, matches));
        } catch (IOException ignored) {
        }
        return matches;
    }

    private static void inspectEmbeddedModules(Path jarPath, Set<String> moduleNames, Set<String> matches) {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".jar")) {
                    continue;
                }

                Optional<String> moduleName = findEmbeddedModuleName(zipFile, entry);
                moduleName.filter(moduleNames::contains).ifPresent(matches::add);
            }
        } catch (IOException ignored) {
        }
    }

    private static Optional<String> findEmbeddedModuleName(ZipFile zipFile, ZipEntry entry) {
        Path tempFile = null;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            tempFile = Files.createTempFile("owc-module-", ".jar");
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return findModuleName(tempFile);
        } catch (IOException ignored) {
            return Optional.empty();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Optional<String> findModuleName(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            return ModuleFinder.of(path.toAbsolutePath().normalize())
                    .findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .map(descriptor -> descriptor.name())
                    .findFirst();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String buildStartupBanner() {
        if (!supportsAnsi()) {
            return """
  #######  ##    ## ########      ##      ##  #######  ########  ##       ########       ######   #######  ########  ########
 ##     ## ###   ## ##           ## ##  ## ## ##     ## ##     ## ##       ##            ##    ## ##     ## ##     ## ##
 ##     ## ####  ## ##           ##  ####  ## ##     ## ##     ## ##       ##            ##       ##     ## ##     ## ##
 ##     ## ## ## ## ######       ##   ##   ## ##     ## ########  ##       ##            ##       ##     ## ########  ######
 ##     ## ##  #### ##           ##        ## ##     ## ##   ##   ##       ##            ##       ##     ## ##   ##   ##
 ##     ## ##   ### ##           ##        ## ##     ## ##    ##  ##       ##            ##    ## ##     ## ##    ##  ##
  #######  ##    ## ########     ##        ##  #######  ##     ## ########  ##             ######   #######  ##     ## ########
""";
        }

        final String text = "ONE WORLD CORE";
        final int glyphHeight = 7;
        final int gap = 1;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            String[] glyph = LOGO_GLYPHS.getOrDefault(text.charAt(i), LOGO_GLYPHS.get(' '));
            width += glyph[0].length() + (i + 1 < text.length() ? gap : 0);
        }

        boolean[][] pixels = new boolean[glyphHeight][width];
        int cursor = 0;
        for (int i = 0; i < text.length(); i++) {
            String[] glyph = LOGO_GLYPHS.getOrDefault(text.charAt(i), LOGO_GLYPHS.get(' '));
            for (int y = 0; y < glyphHeight; y++) {
                for (int x = 0; x < glyph[y].length(); x++) {
                    if (glyph[y].charAt(x) == '#') {
                        pixels[y][cursor + x] = true;
                    }
                }
            }
            cursor += glyph[0].length() + (i + 1 < text.length() ? gap : 0);
        }

        int outWidth = width + 1;
        int outHeight = glyphHeight + 1;
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                boolean main = y < glyphHeight && x < width && pixels[y][x];
                boolean shadow = !main && y > 0 && x > 0 && y - 1 < glyphHeight && x - 1 < width && pixels[y - 1][x - 1];
                if (main) {
                    int[] color = getMainPixelColor(pixels, x, y, width, glyphHeight);
                    out.append("\u001B[48;2;").append(color[0]).append(';').append(color[1]).append(';').append(color[2]).append("m  ");
                } else if (shadow) {
                    out.append("\u001B[48;2;66;20;4m  ");
                } else {
                    out.append(ANSI_RESET).append("  ");
                }
            }
            out.append(ANSI_RESET).append('\n');
        }
        return out.toString();
    }

    private static int[] getMainPixelColor(boolean[][] pixels, int x, int y, int width, int height) {
        boolean top = y == 0 || !pixels[y - 1][x];
        boolean left = x == 0 || !pixels[y][x - 1];
        boolean bottom = y == height - 1 || !pixels[y + 1][x];
        boolean right = x == width - 1 || !pixels[y][x + 1];

        if (top || left) {
            return new int[]{255, 230, 128};
        }
        if (bottom || right) {
            return new int[]{142, 64, 13};
        }

        return switch (y) {
            case 0 -> new int[]{255, 220, 110};
            case 1 -> new int[]{255, 198, 82};
            case 2 -> new int[]{245, 166, 50};
            case 3 -> new int[]{229, 141, 37};
            case 4 -> new int[]{209, 118, 28};
            default -> new int[]{178, 95, 22};
        };
    }

    private static boolean supportsAnsi() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return true;
        }
        String term = System.getenv("TERM");
        return term != null && !term.equalsIgnoreCase("dumb");
    }

    private static String resolveWelcomeMessage() {
        String message = i18nValue("oneworldcore.launch.welcomemessage");
        if (message == null) {
            message = i18nValue("mohist.launch.welcomemessage");
        }
        if (message == null) {
            message = "Thanks for using OneWorldCore";
        }
        return message
                .replace("Mohist", "OneWorldCore")
                .replace("OneWorldStudio", "OneWorldCore")
                .replace("One World Studio", "OneWorldCore");
    }

    private static String i18nValue(String key) {
        try {
            String value = i18n.as(key);
            if (value == null || value.isBlank() || key.equals(value)) {
                return null;
            }
            return value;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
