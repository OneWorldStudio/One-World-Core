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

package com.oneworldstudiomc.config;

import com.oneworldstudiomc.OneWorldCoreStart;
import com.mohistmc.i18n.i18n;
import com.mohistmc.yaml.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OneWorldCoreConfigUtil {

    private static final File LEGACY_MOHIST_YML = new File("mohist-config", "mohist.yml");
    public static final File LEGACY_STUDIO_YML = new File("oneworldstudio-config", "oneworldstudio.yml");
    public static final File LEGACY_CORE_STUDIO_YML = new File("oneworldcore-config", "oneworldstudio.yml");
    public static final File CORE_YML = new File("oneworldcore-config", "oneworldcore.yml");
    public static final File UPDATE_YML = new File("oneworldcore-config", "oneworldupdate.yml");
    public static YamlConfiguration yml = new YamlConfiguration();

    public static void init() {
        try {
            if (!CORE_YML.getParentFile().exists()) {
                CORE_YML.getParentFile().mkdirs();
            }
            if (!CORE_YML.exists()) {
                migrateLegacyMainConfig(CORE_YML, LEGACY_CORE_STUDIO_YML, LEGACY_STUDIO_YML, LEGACY_MOHIST_YML);
            }
            if (!CORE_YML.exists()) {
                CORE_YML.createNewFile();
            }
            repairCorruptedBinaryTags(CORE_YML);
            loadMainConfigSafely();
            migrateLegacyKeyPrefixes();
        } catch (Exception e) {
            System.out.println("File init exception!");
        }
    }

    public static boolean INSTALLATIONFINISHED() {
        return !getBooleanCompat("oneworldcore.installation-finished", "oneworldstudio.installation-finished", false);
    }

    public static boolean CHECK_UPDATE_AUTO_DOWNLOAD() {
        return getUpdateBoolean("auto-download", false);
    }

    public static boolean CHECK_LIBRARIES() {
        boolean value = getBooleanCompat("oneworldcore.libraries.check", "oneworldstudio.libraries.check", true);
        save();
        return value;
    }

    public static boolean CHECK_UPDATE() {
        return getUpdateBoolean("enabled", true);
    }

    public static boolean APPLY_DOWNLOADED_UPDATE_ON_START() {
        return getUpdateBoolean("auto-apply", true);
    }

    public static String UPDATE_DOWNLOAD_DIRECTORY() {
        YamlConfiguration updateConfig = loadUpdateConfig();
        return updateConfig.getString("download-directory", "updates");
    }

    public static boolean aBoolean(String key, boolean defaultReturn) {
        if (yml.get(key) == null && key.startsWith("oneworldcore.")) {
            String studioKey = "oneworldstudio." + key.substring("oneworldcore.".length());
            if (yml.get(studioKey) != null) {
                return yml.getBoolean(studioKey, defaultReturn);
            }
            return defaultReturn;
        }
        return yml.getBoolean(key, defaultReturn);
    }

    public static void i18n() {
        OneWorldCoreStart.i18n = new i18n(OneWorldCoreStart.class.getClassLoader(), CORE_LANG());
    }

    public static void save() {
        try {
            yml.save(CORE_YML);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String CORE_LANG() {
        String value = getStringCompat("oneworldcore.lang", "oneworldstudio.lang", Locale.getDefault().toString());
        save();
        return value;
    }

    public static void setInstallationFinished(boolean finished) {
        yml.set("oneworldcore.installation-finished", finished);
    }

    private static void loadMainConfigSafely() {
        yml = new YamlConfiguration();
        try {
            yml.load(CORE_YML);
            return;
        } catch (Throwable firstLoadFailure) {
            if (repairCorruptedBinaryTags(CORE_YML)) {
                yml = new YamlConfiguration();
                try {
                    yml.load(CORE_YML);
                    return;
                } catch (Throwable ignored) {
                }
            }

            backupBrokenConfig(CORE_YML);
            try {
                Files.writeString(CORE_YML.toPath(), "", StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }

            yml = new YamlConfiguration();
            try {
                yml.load(CORE_YML);
            } catch (Throwable ignored) {
            }
            System.out.println("[OneWorldCore] Recreated invalid main config after load error: " + firstLoadFailure.getMessage());
        }
    }

    private static void backupBrokenConfig(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        try {
            File backup = new File(file.getParentFile(), "oneworldcore.yml.broken-" + System.currentTimeMillis() + ".bak");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[OneWorldCore] Backed up invalid config to " + backup.getPath());
        } catch (Exception ignored) {
        }
    }

    private static boolean repairCorruptedBinaryTags(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> fixed = new ArrayList<>(lines.size());
            boolean changed = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String normalized = line.toLowerCase(Locale.ROOT);
                boolean malformedBinaryTag = normalized.contains("!<tag%3ayaml.org%2c2002%3abinary>")
                        || normalized.contains("!<tag:yaml.org,2002:binary>")
                        || normalized.contains("!!binary");
                if (!malformedBinaryTag) {
                    fixed.add(line);
                    continue;
                }

                int indent = leadingSpaces(line);
                String trimmed = line.trim();
                int keySeparator = trimmed.indexOf(':');
                if (keySeparator <= 0) {
                    fixed.add(line);
                    continue;
                }

                String key = trimmed.substring(0, keySeparator).trim();
                String fallback = "msg".equalsIgnoreCase(key)
                        ? "'[Server] Items will be cleared after %seconds% seconds'"
                        : "''";
                fixed.add(" ".repeat(indent) + key + ": " + fallback);
                changed = true;

                while (i + 1 < lines.size()) {
                    String next = lines.get(i + 1);
                    if (next.trim().isEmpty()) {
                        i++;
                        continue;
                    }
                    if (leadingSpaces(next) > indent) {
                        i++;
                        continue;
                    }
                    break;
                }
            }

            if (changed) {
                backupBrokenConfig(file);
                Files.write(file.toPath(), fixed, StandardCharsets.UTF_8);
                System.out.println("[OneWorldCore] Repaired invalid binary YAML entries in " + file.getPath());
            }
            return changed;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static int leadingSpaces(String value) {
        int i = 0;
        while (i < value.length() && value.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static boolean getBooleanCompat(String modernKey, String secondaryKey, String legacyKey, boolean defaultValue) {
        boolean value;
        if (yml.get(modernKey) != null) {
            value = yml.getBoolean(modernKey, defaultValue);
        } else if (secondaryKey != null && yml.get(secondaryKey) != null) {
            value = yml.getBoolean(secondaryKey, defaultValue);
        } else {
            value = yml.getBoolean(legacyKey, defaultValue);
        }
        yml.set(modernKey, value);
        return value;
    }

    private static boolean getBooleanCompat(String modernKey, String secondaryKey, boolean defaultValue) {
        return getBooleanCompat(modernKey, secondaryKey, secondaryKey, defaultValue);
    }

    private static boolean getUpdateBoolean(String key, boolean defaultValue) {
        YamlConfiguration updateConfig = loadUpdateConfig();
        return updateConfig.getBoolean(key, defaultValue);
    }

    private static YamlConfiguration loadUpdateConfig() {
        YamlConfiguration updateConfig = YamlConfiguration.loadConfiguration(UPDATE_YML);
        updateConfig.addDefault("enabled", true);
        updateConfig.addDefault("auto-download", false);
        updateConfig.addDefault("auto-apply", true);
        updateConfig.addDefault("download-directory", "updates");
        updateConfig.options().copyDefaults(true);
        try {
            if (!UPDATE_YML.getParentFile().exists()) {
                UPDATE_YML.getParentFile().mkdirs();
            }
            updateConfig.save(UPDATE_YML);
        } catch (Exception ignored) {
        }
        return updateConfig;
    }

    private static String getStringCompat(String modernKey, String secondaryKey, String legacyKey, String defaultValue) {
        String value;
        if (yml.get(modernKey) != null) {
            value = yml.getString(modernKey, defaultValue);
        } else if (secondaryKey != null && yml.get(secondaryKey) != null) {
            value = yml.getString(secondaryKey, defaultValue);
        } else {
            value = yml.getString(legacyKey, defaultValue);
        }
        yml.set(modernKey, value);
        return value;
    }

    private static String getStringCompat(String modernKey, String legacyKey, String defaultValue) {
        return getStringCompat(modernKey, legacyKey, legacyKey, defaultValue);
    }

    private static void migrateLegacyMainConfig(File modernTarget, File... legacyFiles) {
        if (legacyFiles == null) {
            return;
        }
        for (File legacyFile : legacyFiles) {
            if (legacyFile == null || !legacyFile.exists()) {
                continue;
            }
            try {
                Files.move(legacyFile.toPath(), modernTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception moveFailed) {
                try {
                    Files.copy(legacyFile.toPath(), modernTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void migrateLegacyKeyPrefixes() {
        try {
            java.lang.reflect.Method getKeysMethod = yml.getClass().getMethod("getKeys", boolean.class);
            Object keysObject = getKeysMethod.invoke(yml, true);
            if (!(keysObject instanceof Iterable<?> iterable)) {
                return;
            }

            java.util.List<String> keys = new java.util.ArrayList<>();
            for (Object key : iterable) {
                if (key instanceof String keyString) {
                    keys.add(keyString);
                }
            }

            for (String key : keys) {
                String modernKey = null;
                if (key.startsWith("mohist.")) {
                    modernKey = "oneworldcore." + key.substring("mohist.".length());
                } else if (key.startsWith("oneworldstudio.")) {
                    modernKey = "oneworldcore." + key.substring("oneworldstudio.".length());
                }
                if (modernKey == null || modernKey.equals(key)) {
                    continue;
                }

                Object modernValue = yml.get(modernKey);
                if (modernValue == null) {
                    yml.set(modernKey, yml.get(key));
                }
                yml.set(key, null);
            }
            yml.set("mohist", null);
            yml.set("oneworldstudio", null);
            save();
        } catch (Throwable ignored) {
        }
    }
}
