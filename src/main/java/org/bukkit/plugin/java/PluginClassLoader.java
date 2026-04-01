package org.bukkit.plugin.java;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.oneworldstudiomc.OneWorldCore;
import com.oneworldstudiomc.bukkit.pluginfix.PluginFixManager;
import com.oneworldstudiomc.bukkit.remapping.ClassLoaderRemapper;
import com.oneworldstudiomc.bukkit.remapping.Remapper;
import com.oneworldstudiomc.bukkit.remapping.RemappingClassLoader;
import com.oneworldstudiomc.plugins.PluginHooks;
import cpw.mods.modlauncher.EnumerationHelper;
import cpw.mods.modlauncher.TransformingClassLoader;
import io.izzel.tools.product.Product;
import io.izzel.tools.product.Product2;
import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.SimplePluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A ClassLoader for plugins, to allow shared classes across multiple plugins
 */
public final class PluginClassLoader extends URLClassLoader implements RemappingClassLoader {
    private static final String PROTOCOLLIB_PLUGIN_NAME = "ProtocolLib";
    private final JavaPluginLoader loader;
    private final Map<String, Class<?>> classes = new ConcurrentHashMap<String, Class<?>>();
    private final PluginDescriptionFile description;
    private final File dataFolder;
    private final File file;
    private final JarFile jar;
    private final Manifest manifest;
    private final URL url;
    private final ClassLoader libraryLoader;
    final JavaPlugin plugin;
    private JavaPlugin pluginInit;
    private IllegalStateException pluginState;
    private final Set<String> seenIllegalAccess = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private ClassLoaderRemapper remapper;

    @Override
    public ClassLoaderRemapper getRemapper() {
        if (remapper == null) {
            remapper = Remapper.createClassLoaderRemapper(this);
        }
        return remapper;
    }

    PluginClassLoader(@NotNull final JavaPluginLoader loader, @Nullable final ClassLoader parent, @NotNull final PluginDescriptionFile description, @NotNull final File dataFolder, @NotNull final File file, @Nullable ClassLoader libraryLoader) throws IOException, InvalidPluginException, MalformedURLException {
        super(new URL[] {file.toURI().toURL()}, parent);
        Preconditions.checkArgument(loader != null, "Loader cannot be null");

        this.loader = loader;
        this.description = description;
        this.dataFolder = dataFolder;
        this.file = file;
        this.jar = new JarFile(file);
        this.manifest = jar.getManifest();
        this.url = file.toURI().toURL();
        this.libraryLoader = libraryLoader;

        try {
            Class<?> jarClass;
            try {
                jarClass = Class.forName(description.getMain(), true, this);
            } catch (ClassNotFoundException ex) {
                throw new InvalidPluginException(OneWorldCore.i18n.as("mohist.i18n.26", description.getMain()), ex);
            }

            Class<? extends JavaPlugin> pluginClass;
            try {
                pluginClass = jarClass.asSubclass(JavaPlugin.class);
            } catch (ClassCastException ex) {
                throw new InvalidPluginException(OneWorldCore.i18n.as("mohist.i18n.27", description.getMain()), ex);
            }

            plugin = pluginClass.newInstance();
        } catch (IllegalAccessException ex) {
            throw new InvalidPluginException("No public constructor", ex);
        } catch (InstantiationException ex) {
            throw new InvalidPluginException("Abnormal plugin type", ex);
        }
        if (PluginHooks.hook(plugin)) {
            ((TransformingClassLoader) OneWorldCore.classLoader).addChild(this);
        }
    }

    @Override
    public URL getResource(String name) {
        Objects.requireNonNull(name);
        URL url = findResource(name);
        if (url == null) {
            if (getParent() != null) {
                url = getParent().getResource(name);
            }
        }
        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);
        @SuppressWarnings("unchecked")
        Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration<?>[2];
        if (getParent()!= null) {
            tmp[1] = getParent().getResources(name);
        }
        tmp[0] = findResources(name);
        return EnumerationHelper.merge(tmp[0], tmp[1]);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return loadClass0(name, resolve, true, true);
    }

    Class<?> loadClass0(@NotNull String name, boolean resolve, boolean checkGlobal, boolean checkLibraries) throws ClassNotFoundException {
        ClassNotFoundException firstError = null;
        try {
            Class<?> result = super.loadClass(name, resolve);

            // SPIGOT-6749: Library classes will appear in the above, but we don't want to return them to other plugins
            if (checkGlobal || result.getClassLoader() == this) {
                return result;
            }
        } catch (ClassNotFoundException ex) {
            firstError = ex;
        }

        Class<?> nmsInner = tryResolveNmsInnerClass(name, resolve);
        if (nmsInner != null) {
            return nmsInner;
        }

        if (checkLibraries && libraryLoader != null) {
            try {
                return libraryLoader.loadClass(name);
            } catch (ClassNotFoundException ex) {
            }
        }

        if (checkGlobal) {
            // This ignores the libraries of other plugins, unless they are transitive dependencies.
            Class<?> result = loader.getClassByName(name, resolve, description);

            if (result != null) {
                // If the class was loaded from a library instead of a PluginClassLoader, we can assume that its associated plugin is a transitive dependency and can therefore skip this check.
                if (result.getClassLoader() instanceof PluginClassLoader) {
                    PluginDescriptionFile provider = ((PluginClassLoader) result.getClassLoader()).description;

                    if (provider != description
                            && !seenIllegalAccess.contains(provider.getName())
                            && !((SimplePluginManager) loader.server.getPluginManager()).isTransitiveDepend(description, provider)) {

                        seenIllegalAccess.add(provider.getName());
                        // Suppress noisy cross-plugin access warning:
                        // "Loaded class ... which is not a depend or softdepend of this plugin."
                    }
                }

                return result;
            }
        }

        nmsInner = tryResolveNmsInnerClass(name, resolve);
        if (nmsInner != null) {
            return nmsInner;
        }

        throw firstError == null ? new ClassNotFoundException(name) : new ClassNotFoundException(name, firstError);
    }

    @Nullable
    private Class<?> tryResolveNmsInnerClass(@NotNull String name, boolean resolve) {
        if (!name.startsWith("net.minecraft.") || !name.contains("$")) {
            return null;
        }

        ClassLoader nmsLoader = OneWorldCore.classLoader;
        if (nmsLoader != null && nmsLoader != this) {
            try {
                return Class.forName(name, false, nmsLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        try {
            this.getRemapper().tryDefineClass(name.replace('.', '/'));
            return super.loadClass(name, resolve);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.startsWith("org.bukkit.") || name.startsWith("net.minecraft.")) {
            throw new ClassNotFoundException(name);
        }
        Class<?> result = classes.get(name);

        if (result == null) {
            String path = name.replace('.', '/').concat(".class");
            URL url = this.findResource(path);

            if (url != null) {
                final boolean bypassProtocolLibRemap = shouldBypassRemapPipeline();

                URLConnection connection;
                Callable<byte[]> byteSource;
                try {
                    connection = url.openConnection();
                    connection.connect();
                    byteSource = () -> {
                        try (InputStream is = connection.getInputStream()) {
                            byte[] classBytes = ByteStreams.toByteArray(is);
                            if (!bypassProtocolLibRemap) {
                                classBytes = Remapper.SWITCH_TABLE_FIXER.apply(classBytes);
                                classBytes = Bukkit.getUnsafe().processClass(description, path, classBytes);
                            }
                            classBytes = PluginFixManager.injectPluginFix(name, classBytes); // Mohist - Inject plugin fix
                            return classBytes;
                        }
                    };
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }

                Product2<byte[], CodeSource> classBytes = bypassProtocolLibRemap
                        ? loadUnmappedClass(name, byteSource, connection)
                        : this.getRemapper().remapClass(name, byteSource, connection);

                int dot = name.lastIndexOf('.');
                if (dot != -1) {
                    String pkgName = name.substring(0, dot);
                    if (getPackage(pkgName) == null) {
                        try {
                            if (manifest != null) {
                                definePackage(pkgName, manifest, url);
                            } else {
                                definePackage(pkgName, null, null, null, null, null, null, null);
                            }
                        } catch (IllegalArgumentException ex) {
                            if (getPackage(pkgName) == null) {
                                throw new IllegalStateException(OneWorldCore.i18n.as("mohist.i18n.30", pkgName));
                            }
                        }
                    }
                }

                result = defineClass(name, classBytes._1, 0, classBytes._1.length, classBytes._2);
            }

            if (result == null) {
                result = super.findClass(name);
            }

            loader.setClass(name, result);
            classes.put(name, result);
        }

        return result;
    }

    private boolean shouldBypassRemapPipeline() {
        return PROTOCOLLIB_PLUGIN_NAME.equalsIgnoreCase(this.description.getName());
    }

    private Product2<byte[], CodeSource> loadUnmappedClass(String className, Callable<byte[]> byteSource, URLConnection connection) throws ClassNotFoundException {
        try {
            byte[] bytes = byteSource.call();
            URL sourceUrl;
            CodeSigner[] signers;
            if (connection instanceof JarURLConnection jarConnection) {
                sourceUrl = jarConnection.getJarFileURL();
                signers = jarConnection.getJarEntry() != null ? jarConnection.getJarEntry().getCodeSigners() : null;
            } else {
                sourceUrl = connection.getURL();
                signers = null;
            }
            return Product.of(bytes, new CodeSource(sourceUrl, signers));
        } catch (Exception e) {
            throw new ClassNotFoundException(className, e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            jar.close();
        }
    }

    @NotNull
    Collection<Class<?>> getClasses() {
        return classes.values();
    }

    synchronized void initialize(@NotNull JavaPlugin javaPlugin) {
        Preconditions.checkArgument(javaPlugin != null, "Initializing plugin cannot be null");
        Preconditions.checkArgument(javaPlugin.getClass().getClassLoader() == this, "Cannot initialize plugin outside of this class loader");
        if (this.plugin != null || this.pluginInit != null) {
            throw new IllegalArgumentException("Plugin already initialized!", pluginState);
        }

        pluginState = new IllegalStateException("Initial initialization");
        this.pluginInit = javaPlugin;

        javaPlugin.init(loader, loader.server, description, dataFolder, file, this);
    }
}
