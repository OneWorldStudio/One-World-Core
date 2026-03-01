package com.oneworldstudiomc;

import com.oneworldstudiomc.eventhandler.EventDispatcherRegistry;
import com.mohistmc.i18n.i18n;
import com.oneworldstudiomc.plugins.MohistProxySelector;
import com.oneworldstudiomc.util.VersionInfo;
import java.net.ProxySelector;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;

@Mod("oneworldstudio")
@OnlyIn(Dist.DEDICATED_SERVER)
public class MohistMC {
    public static final String NAME = "OneWorldCore";
    public static Logger LOGGER = LogManager.getLogger();
    public static i18n i18n;
    public static String version = "1.20.1";
    public static String modid = "oneworldstudio";
    public static ClassLoader classLoader;
    public static VersionInfo versionInfo;

    public MohistMC() {
        classLoader = MohistMC.class.getClassLoader();

        //TODO: do something when mod loading
        LOGGER.info("OneWorldCore mod loading.....");
        LOGGER.info("All Rights Reserved - One World Studio 2019-2026");
        EventDispatcherRegistry.init();
        ProxySelector.setDefault(new MohistProxySelector(ProxySelector.getDefault()));
    }

    public static void initVersion() {
        String mohist_lang = MohistConfig.yml.getString("mohist.lang", Locale.getDefault().toString());
        i18n = new i18n(MohistMC.class.getClassLoader(), mohist_lang);

        Map<String, String> arguments = new HashMap<>();
        String craftVersion = CraftServer.class.getPackage().getImplementationVersion();
        String[] cbs = craftVersion != null ? craftVersion.split("-") : new String[0];
        arguments.put("oneworldstudio", (MohistMC.class.getPackage().getImplementationVersion() != null) ? MohistMC.class.getPackage().getImplementationVersion() : version);
        arguments.put("bukkit", cbs.length > 0 ? cbs[0] : "unknown");
        arguments.put("craftbukkit", cbs.length > 1 ? cbs[1] : "unknown");
        arguments.put("spigot", cbs.length > 2 ? cbs[2] : "unknown");
        arguments.put("forge", ForgeVersion.getVersion());
        versionInfo = new VersionInfo(arguments);
    }
}
