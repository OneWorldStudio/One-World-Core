package com.oneworldstudiomc.util;

import com.oneworldstudiomc.OneWorldCoreStart;
import com.mohistmc.tools.FileUtils;
import com.mohistmc.tools.OSUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DataParser {

    public static final HashMap<String, String> versionMap = new HashMap<>();
    public static final List<String> launchArgs = new ArrayList<>();

    public static void parseVersions() {
        versionMap.put("forge", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/forge.txt").get(0));
        versionMap.put("minecraft", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/minecraft.txt").get(0));
        versionMap.put("mcp", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/mcp.txt").get(0));
        versionMap.put("oneworldstudio", FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "versions/oneworldstudio.txt").get(0));

        OneWorldCoreStart.MCVERSION = versionMap.get("minecraft");
    }

    public static void parseLaunchArgs() {
        OSUtil.OS os = OSUtil.getOS();
        String osName = (os != null && os.equals(OSUtil.OS.WINDOWS)) ? "win" : "unix";
        launchArgs.addAll(FileUtils.readFileFromJar(DataParser.class.getClassLoader(), "data/" + osName + "_args.txt"));
    }
}
