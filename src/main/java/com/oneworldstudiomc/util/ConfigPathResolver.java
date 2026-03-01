package com.oneworldstudiomc.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ConfigPathResolver {

    private static final File LEGACY_MOHIST_ROOT = new File("mohist-config");
    private static final File LEGACY_STUDIO_ROOT = new File("oneworldstudio-config");
    private static final File MODERN_ROOT = new File("oneworldcore-config");

    private ConfigPathResolver() {
    }

    public static File resolve(String relativePath) {
        return resolve(new File(MODERN_ROOT, relativePath),
                new File(LEGACY_STUDIO_ROOT, relativePath),
                new File(LEGACY_MOHIST_ROOT, relativePath));
    }

    public static File resolveMainConfigFile() {
        return resolve(new File(MODERN_ROOT, "oneworldstudio.yml"),
                new File(LEGACY_STUDIO_ROOT, "oneworldstudio.yml"),
                new File(LEGACY_MOHIST_ROOT, "mohist.yml"));
    }

    public static File resolve(File modernFile, File... legacyFiles) {
        File parent = modernFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (legacyFiles != null) {
            for (File legacyFile : legacyFiles) {
                if (legacyFile == null || !legacyFile.exists()) {
                    continue;
                }

                try {
                    Files.move(legacyFile.toPath(), modernFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    cleanupLegacyTree();
                    break;
                } catch (IOException moveFailed) {
                    try {
                        Files.copy(legacyFile.toPath(), modernFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        break;
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        return modernFile;
    }

    public static void cleanupLegacyTree() {
        deleteEmptyDirs(LEGACY_MOHIST_ROOT);
        deleteEmptyDirs(LEGACY_STUDIO_ROOT);
    }

    private static boolean deleteEmptyDirs(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return true;
        }

        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }

        boolean empty = true;
        for (File child : children) {
            if (child.isDirectory()) {
                if (!deleteEmptyDirs(child)) {
                    empty = false;
                }
            } else {
                empty = false;
            }
        }

        if (empty) {
            dir.delete();
            return true;
        }
        return false;
    }
}
