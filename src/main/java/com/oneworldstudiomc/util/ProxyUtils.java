package com.oneworldstudiomc.util;

import com.oneworldstudiomc.MohistConfig;
import org.spigotmc.SpigotConfig;

public class ProxyUtils {

    public static boolean is() {
        return MohistConfig.velocity_enabled || SpigotConfig.bungee;
    }
}
