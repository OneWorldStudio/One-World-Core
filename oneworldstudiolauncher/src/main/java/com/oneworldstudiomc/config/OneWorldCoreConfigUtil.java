/*
 * Mohist - MohistMC
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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class OneWorldCoreConfigUtil {

    private static final File LEGACY_MOHIST_YML = new File("mohist-config", "mohist.yml");
    public static final File LEGACY_STUDIO_YML = new File("oneworldstudio-config", "oneworldstudio.yml");
    public static final File mohistyml = new File("oneworldcore-config", "oneworldstudio.yml");
    public static final YamlConfiguration yml = YamlConfiguration.loadConfiguration(mohistyml);

    public static void init() {
        try {
            if (!mohistyml.getParentFile().exists()) {
                mohistyml.getParentFile().mkdirs();
            }
            if (!mohistyml.exists()) {
                if (LEGACY_STUDIO_YML.exists()) {
                    Files.copy(LEGACY_STUDIO_YML.toPath(), mohistyml.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else if (LEGACY_MOHIST_YML.exists()) {
                    Files.copy(LEGACY_MOHIST_YML.toPath(), mohistyml.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    mohistyml.createNewFile();
                }
            }
            yml.load(mohistyml);
        } catch (Exception e) {
            System.out.println("File init exception!");
        }
    }

    public static boolean INSTALLATIONFINISHED() {
        return !getBooleanCompat("oneworldcore.installation-finished", "oneworldstudio.installation-finished", "mohist.installation-finished", false);
    }

    public static boolean CHECK_UPDATE_AUTO_DOWNLOAD() {
        boolean value = getBooleanCompat("oneworldcore.check_update_auto_download", "oneworldstudio.check_update_auto_download", "mohist.check_update_auto_download", false);
        save();
        return value;
    }

    public static boolean CHECK_LIBRARIES() {
        boolean value = getBooleanCompat("oneworldcore.libraries.check", "oneworldstudio.libraries.check", "mohist.libraries.check", true);
        save();
        return value;
    }

    public static boolean CHECK_UPDATE() {
        boolean value = getBooleanCompat("oneworldcore.check_update", "oneworldstudio.check_update", "mohist.check_update", true);
        save();
        return value;
    }

    public static boolean aBoolean(String key, boolean defaultReturn) {
        if (yml.get(key) == null && key.startsWith("oneworldcore.")) {
            String studioKey = "oneworldstudio." + key.substring("oneworldcore.".length());
            if (yml.get(studioKey) != null) {
                return yml.getBoolean(studioKey, defaultReturn);
            }
            String legacyKey = "mohist." + key.substring("oneworldcore.".length());
            return yml.getBoolean(legacyKey, defaultReturn);
        }
        return yml.getBoolean(key, defaultReturn);
    }

    public static void i18n() {
        OneWorldCoreStart.i18n = new i18n(OneWorldCoreStart.class.getClassLoader(), MOHISTLANG());
    }

    public static void save() {
        try {
            yml.save(mohistyml);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String MOHISTLANG() {
        String value = getStringCompat("oneworldcore.lang", "oneworldstudio.lang", "mohist.lang", Locale.getDefault().toString());
        save();
        return value;
    }

    public static void setInstallationFinished(boolean finished) {
        yml.set("oneworldcore.installation-finished", finished);
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
        return getStringCompat(modernKey, null, legacyKey, defaultValue);
    }
}
