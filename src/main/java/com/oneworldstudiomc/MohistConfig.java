package com.oneworldstudiomc;

import com.google.common.base.Throwables;
import com.oneworldstudiomc.api.ServerAPI;
import com.oneworldstudiomc.api.color.ColorsAPI;
import com.oneworldstudiomc.commands.BackupWorldCommand;
import com.oneworldstudiomc.commands.BansCommand;
import com.oneworldstudiomc.commands.DumpCommand;
import com.oneworldstudiomc.commands.GetPluginListCommand;
import com.oneworldstudiomc.commands.ItemsCommand;
import com.oneworldstudiomc.commands.MohistCommand;
import com.oneworldstudiomc.commands.PermissionCommand;
import com.oneworldstudiomc.commands.PingCommand;
import com.oneworldstudiomc.commands.PluginCommand;
import com.oneworldstudiomc.commands.ShowsCommand;
import com.oneworldstudiomc.plugins.MohistPlugin;
import com.oneworldstudiomc.util.ConfigPathResolver;
import com.oneworldstudiomc.util.YamlUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public class MohistConfig {

    private static final List<String> HEADER = Arrays.asList("""
            This is the main configuration file for OneWorldCore.
            As you can see, there's tons to configure. Some options may impact gameplay, so use
            with caution, and make sure you know what each option does before configuring.
            For a reference for any variable inside this file, check out the OneWorldCore docs.

            If you need help with the configuration or have any questions related to Spigot,
            join us at the Discord or drop by our forums and leave a post.

            Website: https://oneworldstudio.dev/
            
            """.split("\\n"));
    /*========================================================================*/
    public static YamlConfiguration config;
    static int version;
    static Map<String, Command> commands;
    private static File CONFIG_FILE;

    private static final File LEGACY_MOHIST_YML = new File("mohist-config", "mohist.yml");
    public static File mohistyml = ConfigPathResolver.resolveMainConfigFile();
    public static YamlConfiguration yml = YamlConfiguration.loadConfiguration(mohistyml);

    public static void init(File configFile) {
        mohistyml = ConfigPathResolver.resolveMainConfigFile();
        configFile = resolveConfigFile(configFile);
        ensureConfigFileExists(configFile);

        if (!configFile.exists() && LEGACY_MOHIST_YML.exists()) {
            try {
                File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.copy(LEGACY_MOHIST_YML.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }

        if (!mohistyml.exists() && LEGACY_MOHIST_YML.exists()) {
            try {
                if (!mohistyml.getParentFile().exists()) {
                    mohistyml.getParentFile().mkdirs();
                }
                Files.copy(LEGACY_MOHIST_YML.toPath(), mohistyml.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }

        try {
            yml.load(mohistyml);
        } catch (Exception ignored) {
        }

        CONFIG_FILE = configFile;
        config = new YamlConfiguration();
        try {
            config.load(CONFIG_FILE);
        } catch (IOException | InvalidConfigurationException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load OneWorldCore settings file, please correct your syntax errors", ex);
            Throwables.throwIfUnchecked(ex);
        }

        config.options().setHeader(HEADER);
        config.options().copyDefaults(true);

        commands = new HashMap<>();
        commands.put("mohist", new MohistCommand("mohist"));
        commands.put("oneworldcore", new MohistCommand("oneworldcore"));
        commands.put("getpluginlist", new GetPluginListCommand("getpluginlist"));
        commands.put("dump", new DumpCommand("dump"));
        commands.put("plugin", new PluginCommand("plugin"));
        commands.put("backupworld", new BackupWorldCommand("backupworld"));
        commands.put("items", new ItemsCommand("items"));
        commands.put("permission", new PermissionCommand("permission"));
        commands.put("bans", new BansCommand("bans"));
        commands.put("shows", new ShowsCommand("shows"));
        commands.put("ping", new PingCommand("ping"));

        MohistPlugin.registerCommands(commands);

        version = getInt("config-version", 1);
        set("config-version", 1);
        readConfig();

        try {
            Class.forName("org.sqlite.JDBC");
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Throwable t) {
            throw new RuntimeException("Error initializing OneWorldCore", t);
        }
    }

    private static File resolveConfigFile(File configFile) {
        try {
            if (configFile.toPath().equals(LEGACY_MOHIST_YML.toPath())) {
                return mohistyml;
            }
        } catch (Exception ignored) {
        }
        return configFile;
    }

    private static void ensureConfigFileExists(File configFile) {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (configFile.exists()) {
            return;
        }

        try {
            if (!configFile.toPath().equals(mohistyml.toPath()) && mohistyml.exists()) {
                Files.copy(mohistyml.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            if (!configFile.toPath().equals(LEGACY_MOHIST_YML.toPath()) && LEGACY_MOHIST_YML.exists()) {
                Files.copy(LEGACY_MOHIST_YML.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            configFile.createNewFile();
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        YamlUtils.save(mohistyml, yml);
    }

    public static void registerCommands() {
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            MinecraftServer.getServer().server.getCommandMap().register(entry.getKey(), "OneWorldCore", entry.getValue());
        }
    }

    static void readConfig() {
        for (Method method : MohistConfig.class.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                    try {
                        method.setAccessible(true);
                        method.invoke(null);
                    } catch (InvocationTargetException ex) {
                        Throwables.throwIfUnchecked(ex.getCause());
                    } catch (Exception ex) {
                        Bukkit.getLogger().log(Level.SEVERE, "Error invoking " + method, ex);
                    }
                }
            }
        }

        try {
            config.save(CONFIG_FILE);
        } catch (IOException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not save " + CONFIG_FILE, ex);
        }
    }

    private static void set(String path, Object val) {
        config.set(path, val);
    }

    private static boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path, config.getBoolean(path));
    }

    private static int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path, config.getInt(path));
    }

    private static <T> List<String> getStringList(String path, T def) {
        config.addDefault(path, def);
        return config.getStringList(path);
    }

    private static String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path, config.getString(path));
    }

    private static double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path, config.getDouble(path));
    }

    public static String mohist_lang() {
        return yml.getString("oneworldcore.lang",
                yml.getString("oneworldstudio.lang", yml.getString("mohist.lang", Locale.getDefault().toString())));
    }
    public static String motd() {
        return ColorsAPI.of(MohistConfig.motdFirstLine) + "\n" + ColorsAPI.of(MohistConfig.motdSecondLine);
    }

    public static boolean isProxyOnlineMode() {
        return org.bukkit.Bukkit.getOnlineMode()  || (velocity_enabled && velocity_onlineMode);
    }

    public static boolean show_logo;
    public static String mohist_lang;
    public static boolean check_update;
    public static int maximumRepairCost;
    public static boolean enchantment_fix;
    public static int max_enchantment_level;

    public static boolean player_modlist_blacklist_enable;
    public static List<String> player_modlist_blacklist;

    public static boolean server_modlist_whitelist_enable;
    public static String server_modlist_whitelist;
    public static int maxBees;
    public static boolean bookAnimationTick;
    public static boolean networkmanager_debug;
    public static List<String> networkmanager_intercept;
    public static boolean keepinventory_global;
    public static boolean keepinventory_inventory;
    public static boolean keepinventory_permission_enable;
    public static String keepinventory_inventory_permission;
    public static boolean keepinventory_exp;
    public static String keepinventory_exp_permission;

    // Thread Priority
    public static int server_thread;

    public static boolean clear_item;
    public static boolean clear_enable;
    public static List<String> clear_item_whitelist;
    public static String clear_item_msg;
    public static String clear_countdown_msg;
    public static int clear_time;

    public static boolean clear_noitem;
    public static List<String> clear_noitem_whitelist;
    public static String clear_noitem_msg;

    // Ban
    public static boolean ban_item_enable;
    public static List<String> ban_item_materials;
    public static boolean ban_entity_enable;
    public static List<String> ban_entity_types;

    public static boolean ban_enchantment_enable;
    public static List<String> ban_enchantment_list;

    public static boolean motdEnable;
    public static String motdFirstLine;
    public static String motdSecondLine;
    public static String pingCommandOutput;

    // Ban events
    public static boolean doFireTick;
    public static boolean explosion;

    public static boolean worldmanage;

    public static boolean bukkitpermissionshandler;
    public static boolean velocity_enabled;
    public static boolean velocity_onlineMode;
    public static String velocity_secret;

    public static boolean recipe_warn;

    public static boolean tpa_enable;
    public static boolean tpa_permissions_enable;
    public static boolean back_enable;
    public static boolean back_permissions_enable;
    public static boolean permissions_debug_enable;
    public static boolean permissions_debug_console;
    public static boolean permissions_send_player;

    public static boolean watchdog_spigot;
    public static boolean watchdog_mohist;
    public static boolean async_save_world;

    //Messaes
    public static String message_require_forge;

    public static String server_mod_name;

    public static boolean deepseek_enable;
    public static String deepseek_apikey;
    public static String deepseek_model;
    public static String deepseek_system;
    public static String deepseek_command;
    public static String deepseek_chatfromat;

    public static boolean custom_no_villager;
    public static boolean custom_entity_tp_end;
    public static boolean custom_entity_tp_nether;
    public static boolean custom_raid_no_emerald;
    public static int custom_lava_speed_normal;
    public static int custom_lava_speed_nether;
    public static String ping_status_version;

    private static void mohist() {
        show_logo = getBooleanCompat("oneworldcore.show_logo", "oneworldstudio.show_logo", "mohist.show_logo", true);
        mohist_lang = getStringCompat("oneworldcore.lang", "oneworldstudio.lang", "mohist.lang", Locale.getDefault().toString());
        check_update = getBooleanCompat("oneworldcore.check_update", "oneworldstudio.check_update", "mohist.check_update", true);
        ping_status_version = getStringCompat("oneworldcore.ping_status_version", "oneworldstudio.ping_status_version", "mohist.ping_status_version", "oneworldcore 1.20.1");
        watchdog_spigot = getBooleanCompat("oneworldcore.watchdog_spigot", "oneworldstudio.watchdog_spigot", "mohist.watchdog_spigot", true);
        watchdog_mohist = getBooleanCompat("oneworldcore.watchdog_mohist", "oneworldstudio.watchdog_mohist", "mohist.watchdog_mohist", false);
        maximumRepairCost = getInt("anvilfix.maximumrepaircost", 40);
        enchantment_fix = getBoolean("anvilfix.enchantment_fix", false);
        max_enchantment_level = getInt("anvilfix.max_enchantment_level", 32767);
        player_modlist_blacklist_enable = getBoolean("player_modlist_blacklist.enable", false);
        player_modlist_blacklist = getStringList("player_modlist_blacklist.list", new ArrayList<>());
        server_modlist_whitelist_enable = getBoolean("server_modlist_whitelist.enable", false);
        server_modlist_whitelist = getString("server_modlist_whitelist.list", ServerAPI.modlists_All.toString().replace(", mohist", ""));
        maxBees = getInt("custom.max-bees-in-hive", 3);
        bookAnimationTick = getBoolean("enchantment-table-book-animation-tick", false);
        networkmanager_debug = getBoolean("networkmanager.debug", false);
        networkmanager_intercept = getStringList("networkmanager.intercept", new ArrayList<>());
        keepinventory_global = getBoolean("keepinventory.global.enable", false);
        keepinventory_inventory = getBoolean("keepinventory.global.inventory", true);
        keepinventory_permission_enable = getBoolean("keepinventory.permission.enable", false);
        keepinventory_inventory_permission = getString("keepinventory.permission.inventory", "mohist.keepinventory.inventory");
        keepinventory_exp = getBoolean("keepinventory.global.exp", true);
        keepinventory_exp_permission = getString("keepinventory.permission.exp", "mohist.keepinventory.exp");
        server_thread = getInt("threadpriority.server_thread", 8);

        clear_enable = getBoolean("entity.clear.enable", false);
        clear_time = getInt("entity.clear.time", 1800);
        clear_countdown_msg = getString("entity.clear.countdown.msg", "[Server] В§cItems will be cleared after %seconds% secondsпјЃ");

        clear_item = getBoolean("entity.clear.item.enable", false);
        clear_item_whitelist = getStringList("entity.clear.item.whitelist", new ArrayList<>());
        clear_item_msg = getString("entity.clear.item.msg", "[Server] Cleaned up %size% drop item");

        clear_noitem = getBoolean("entity.clear.noitem.enable", false);
        clear_noitem_whitelist = getStringList("entity.clear.noitem.whitelist", new ArrayList<>());
        clear_noitem_msg = getString("entity.clear.noitem.msg", "[Server] Cleaned up %size% monster");

        ban_item_enable = getBoolean("ban.item.enable" , false);
        ban_item_materials = getStringList("ban.item.list", new ArrayList<>());
        ban_entity_enable = getBoolean("ban.entity.enable", false);
        ban_entity_types = getStringList("ban.entity.list", new ArrayList<>());
        ban_enchantment_enable = getBoolean("ban.enchantment.enable", false);
        ban_enchantment_list = getStringList("ban.enchantment.list", new ArrayList<>());
        motdEnable = getBoolean("motd.enable", false);
        motdFirstLine = getString("motd.firstline", "<RAINBOW1>A Minecraft Server</RAINBOW>");
        motdSecondLine = getString("motd.secondline", "");

        pingCommandOutput = getString("settings.messages.ping-command-output", "В§2%s's ping is %sms");

        doFireTick = getBoolean("events.fire_tick", false);
        explosion = getBoolean("events.explosion", false);
        bukkitpermissionshandler = getBoolean("forge.bukkitpermissionshandler", true);
        worldmanage = getBoolean("worldmanage", true);
        velocity_enabled = getBoolean("velocity.enabled", false);
        velocity_onlineMode = getBoolean("velocity.onlineMode", false);
        velocity_secret = getString("velocity.secret", "");

        recipe_warn = getBoolean("recipe.warn", false);
        tpa_enable = getBoolean("tpa.enable", false);
        tpa_permissions_enable = getBoolean("tpa.permissions", true);
        back_enable = getBoolean("back.enable", false);
        back_permissions_enable = getBoolean("back.permissions", true);

        permissions_debug_enable = getBoolean("permissions.debug.enable", false);
        permissions_debug_console = getBoolean("permissions.debug.console", false);
        permissions_send_player = getBoolean("permissions.debug.player", false);
        async_save_world = getBoolean("world.async_save", false);

        message_require_forge = getString("message.require_forge", "This server has mods that require Forge to be installed on the client. Contact your server admin for more details.");
        server_mod_name = getString("server_mod_name", "oneworldcore");

        deepseek_enable = getBoolean("deepseek.enable", false);
        deepseek_apikey = getString("deepseek.apikey", "oneworldcore");
        deepseek_model = getString("deepseek.model", "deepseek-chat");
        deepseek_system = getString("deepseek.system", "Your name is Xiaoxiaomo, you are 18 years old, and you are a cute girl!");
        deepseek_command = getString("deepseek.command", "ai");
        deepseek_chatfromat = getString("deepseek.chatfromat", "<е°Џе°ЏеўЁ> %s");

        custom_no_villager = getBoolean("custom.no_villager", false);
        custom_entity_tp_end = getBoolean("custom.entity_tp_end", true);
        custom_entity_tp_nether = getBoolean("custom.entity_tp_nether", true);
        custom_raid_no_emerald = getBoolean("custom.raid_no_emerald", false);
        custom_lava_speed_normal = getInt("custom.lava_speed.normal", 30);
        custom_lava_speed_nether = getInt("custom.lava_speed.nether", 10);

        getBoolean("keepinventory.world.inventory", false);
        getBoolean("keepinventory.world.exp", false);
    }

    private static boolean getBooleanCompat(String modernPath, String secondPath, String legacyPath, boolean def) {
        boolean value;
        if (config.contains(modernPath)) {
            value = getBoolean(modernPath, def);
        } else if (secondPath != null && config.contains(secondPath)) {
            value = getBoolean(secondPath, def);
        } else {
            value = getBoolean(legacyPath, def);
        }
        set(modernPath, value);
        return value;
    }

    private static boolean getBooleanCompat(String modernPath, String legacyPath, boolean def) {
        return getBooleanCompat(modernPath, null, legacyPath, def);
    }

    private static String getStringCompat(String modernPath, String secondPath, String legacyPath, String def) {
        String value;
        if (config.contains(modernPath)) {
            value = getString(modernPath, def);
        } else if (secondPath != null && config.contains(secondPath)) {
            value = getString(secondPath, def);
        } else {
            value = getString(legacyPath, def);
        }
        set(modernPath, value);
        return value;
    }

    private static String getStringCompat(String modernPath, String legacyPath, String def) {
        return getStringCompat(modernPath, null, legacyPath, def);
    }
}

