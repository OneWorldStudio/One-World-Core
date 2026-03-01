package com.oneworldstudiomc.util;

import com.oneworldstudiomc.OneWorldCoreStart;

/**
 * @author Mgazul by MohistMC
 * @date 2023/9/23 3:19:38
 */
public class I18n {

    public static String as(String key) {
        return OneWorldCoreStart.i18n.as(key);
    }

    public static String as(String key, Object... objects) {
        return OneWorldCoreStart.i18n.as(key, objects);
    }
}
