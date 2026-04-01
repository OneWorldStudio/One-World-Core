package com.oneworldstudiomc.paper.plugin.configuration;

public final class SimplePluginMeta implements PluginMeta, io.papermc.paper.plugin.configuration.PluginMeta {

    private final String name;
    private final String version;
    private final boolean foliaSupported;

    public SimplePluginMeta(String name, String version, boolean foliaSupported) {
        this.name = name;
        this.version = version;
        this.foliaSupported = foliaSupported;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public boolean isFoliaSupported() {
        return this.foliaSupported;
    }
}
