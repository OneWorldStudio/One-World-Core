package com.oneworldstudiomc.bukkit.pluginfix;

import java.util.function.Consumer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.entity.EntityType;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ARETURN;

public class PluginFixManager {

    private static final Set<Object> REGISTERED_MYTHICDUNGEONS_BQ3_BRIDGE =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final AtomicBoolean CUSTOMNAMEPLATES_FALLBACK_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean CUSTOMNAMEPLATES_FALLBACK_ERROR_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean ECO_UNREGISTER_FALLBACK_LOGGED = new AtomicBoolean(false);
    private static final Set<Object> REGISTERED_TOPMINION_AUTOSAVE =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public static final Executor MAIN_EXECUTOR_COMPAT = runnable -> {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            server.executeIfPossible(runnable);
        } else {
            runnable.run();
        }
    };

    public static void sendChunkCompat(Object lock, Object worldServerObj, int chunkX, int chunkZ) {
        Runnable sendTask = () -> {
            Object monitor = lock != null ? lock : PluginFixManager.class;
            synchronized (monitor) {
                try {
                    Object chunkSource = invokeNoArgs(worldServerObj, "k", "getChunkSource");
                    Object levelChunk = getChunkForSend(worldServerObj, chunkSource, chunkX, chunkZ);
                    if (levelChunk != null) {
                        Object lightEngine = findLightEngine(chunkSource);
                        Object packet = buildChunkPacket(levelChunk, lightEngine);
                        if (packet != null && broadcastChunkPacket(worldServerObj, packet)) {
                            return;
                        }
                    }

                    Object bukkitWorld = invokeNoArgs(worldServerObj, "getWorld");
                    if (bukkitWorld instanceof org.bukkit.World world) {
                        ensureBukkitChunkLoaded(world, chunkX, chunkZ);
                        world.refreshChunk(chunkX, chunkZ);
                        return;
                    }

                    try {
                        Method isChunkLoaded = bukkitWorld.getClass().getMethod("isChunkLoaded", int.class, int.class);
                        Object loaded = isChunkLoaded.invoke(bukkitWorld, chunkX, chunkZ);
                        if (loaded instanceof Boolean loadedFlag && !loadedFlag) {
                            Method getChunkAt = bukkitWorld.getClass().getMethod("getChunkAt", int.class, int.class);
                            getChunkAt.invoke(bukkitWorld, chunkX, chunkZ);
                        }
                    } catch (Throwable ignored) {
                    }

                    Method refreshChunk = bukkitWorld.getClass().getMethod("refreshChunk", int.class, int.class);
                    refreshChunk.invoke(bukkitWorld, chunkX, chunkZ);
                } catch (Throwable ignored) {
                }
            }
        };

        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            try {
                server.execute(sendTask);
                return;
            } catch (Throwable ignored) {
            }
        }
        sendTask.run();
    }

    public static void resendChunkAndNeighborsCompat(Object lock, Object worldServerObj, int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                sendChunkCompat(lock, worldServerObj, chunkX + dx, chunkZ + dz);
            }
        }
    }

    private static void ensureBukkitChunkLoaded(org.bukkit.World world, int chunkX, int chunkZ) {
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.getChunkAt(chunkX, chunkZ);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object getChunkForSend(Object worldServerObj, Object chunkSource, int chunkX, int chunkZ) {
        if (chunkSource != null) {
            Object chunk = invokeTwoInts(chunkSource, "getChunkAtIfLoadedImmediately", chunkX, chunkZ);
            if (chunk == null) {
                chunk = invokeTwoInts(chunkSource, "getChunkAtIfCachedImmediately", chunkX, chunkZ);
            }
            if (chunk == null) {
                chunk = invokeTwoInts(chunkSource, "getChunkNow", chunkX, chunkZ);
            }
            if (chunk != null) {
                return chunk;
            }
        }

        Object chunk = invokeTwoInts(worldServerObj, "d", chunkX, chunkZ);
        if (chunk != null) {
            return chunk;
        }
        chunk = invokeTwoInts(worldServerObj, "getChunk", chunkX, chunkZ);
        if (chunk != null) {
            return chunk;
        }

        try {
            Object bukkitWorld = invokeNoArgs(worldServerObj, "getWorld");
            if (bukkitWorld instanceof org.bukkit.World world) {
                ensureBukkitChunkLoaded(world, chunkX, chunkZ);
                org.bukkit.Chunk bukkitChunk = world.getChunkAt(chunkX, chunkZ);
                return invokeNoArgs(bukkitChunk, "getHandle");
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object findLightEngine(Object chunkSource) {
        if (chunkSource == null) {
            return null;
        }
        Object direct = invokeNoArgs(chunkSource, "a", "getLightEngine");
        if (direct != null) {
            return direct;
        }
        for (Method method : chunkSource.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String returnName = method.getReturnType().getName().toLowerCase(Locale.ROOT);
            if (!returnName.contains("lightengine")) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(chunkSource);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object buildChunkPacket(Object levelChunk, Object lightEngine) {
        if (levelChunk == null || lightEngine == null) {
            return null;
        }
        try {
            ClassLoader classLoader = levelChunk.getClass().getClassLoader();
            Class<?> packetClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket",
                    false,
                    classLoader
            );
            for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length != 5) {
                    continue;
                }
                if (!params[0].isAssignableFrom(levelChunk.getClass())
                        || !params[1].isAssignableFrom(lightEngine.getClass())) {
                    continue;
                }
                constructor.setAccessible(true);
                return constructor.newInstance(levelChunk, lightEngine, null, null, false);
            }
            for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length != 4) {
                    continue;
                }
                if (!params[0].isAssignableFrom(levelChunk.getClass())
                        || !params[1].isAssignableFrom(lightEngine.getClass())) {
                    continue;
                }
                constructor.setAccessible(true);
                return constructor.newInstance(levelChunk, lightEngine, null, null);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean broadcastChunkPacket(Object worldServerObj, Object packet) {
        try {
            Object bukkitWorld = invokeNoArgs(worldServerObj, "getWorld");
            if (!(bukkitWorld instanceof org.bukkit.World world)) {
                return false;
            }
            boolean sent = false;
            for (org.bukkit.entity.Player player : world.getPlayers()) {
                if (sendPacketToPlayer(player, packet)) {
                    sent = true;
                }
            }
            return sent;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sendPacketToPlayer(org.bukkit.entity.Player player, Object packet) {
        try {
            Object handle = invokeNoArgs(player, "getHandle");
            if (handle == null) {
                return false;
            }

            Object connection = null;
            for (Field field : handle.getClass().getDeclaredFields()) {
                String typeName = field.getType().getName();
                if (!typeName.contains("PlayerConnection") && !typeName.contains("ServerGamePacketListenerImpl")) {
                    continue;
                }
                field.setAccessible(true);
                connection = field.get(handle);
                if (connection != null) {
                    break;
                }
            }
            if (connection == null) {
                return false;
            }

            for (Method method : connection.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (!param.isAssignableFrom(packet.getClass())) {
                    continue;
                }
                String methodName = method.getName().toLowerCase(Locale.ROOT);
                if (!methodName.equals("a") && !methodName.contains("send")) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(connection, packet);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object invokeNoArgs(Object owner, String... names) {
        if (owner == null) {
            return null;
        }
        for (String name : names) {
            try {
                Method method = owner.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (Throwable ignored) {
            }
            try {
                Method method = owner.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object invokeTwoInts(Object owner, String name, int first, int second) {
        if (owner == null) {
            return null;
        }
        try {
            Method method = owner.getClass().getMethod(name, int.class, int.class);
            method.setAccessible(true);
            return method.invoke(owner, first, second);
        } catch (Throwable ignored) {
        }
        try {
            Method method = owner.getClass().getDeclaredMethod(name, int.class, int.class);
            method.setAccessible(true);
            return method.invoke(owner, first, second);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static List<?> nearbyPlayersCompat(Object worldServerObj, Object chunkPosObj) {
        List<?> playersFromChunkMap = tryNearbyPlayersFromChunkMap(worldServerObj, chunkPosObj);
        if (playersFromChunkMap != null && !playersFromChunkMap.isEmpty()) {
            return playersFromChunkMap;
        }

        List<Object> fallbackPlayers = new ArrayList<>();
        try {
            Object bukkitWorld = invokeNoArgs(worldServerObj, "getWorld");
            if (bukkitWorld instanceof org.bukkit.World world) {
                for (org.bukkit.entity.Player player : world.getPlayers()) {
                    Object handle = invokeNoArgs(player, "getHandle");
                    if (handle != null) {
                        fallbackPlayers.add(handle);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return fallbackPlayers;
    }

    private static List<?> tryNearbyPlayersFromChunkMap(Object worldServerObj, Object chunkPosObj) {
        try {
            Object chunkSource = invokeNoArgs(worldServerObj, "k", "getChunkSource");
            if (chunkSource == null) {
                return null;
            }

            Object chunkMap = null;
            for (Field field : chunkSource.getClass().getDeclaredFields()) {
                String fieldTypeName = field.getType().getName().toLowerCase(Locale.ROOT);
                if (!fieldTypeName.contains("playerchunkmap") && !fieldTypeName.contains("chunkmap")) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(chunkSource);
                if (value != null) {
                    chunkMap = value;
                    break;
                }
            }
            if (chunkMap == null) {
                return null;
            }

            for (Method method : chunkMap.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params[1] != boolean.class) {
                    continue;
                }
                if (chunkPosObj != null && !params[0].isAssignableFrom(chunkPosObj.getClass())) {
                    continue;
                }
                if (!List.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                method.setAccessible(true);
                Object value = method.invoke(chunkMap, chunkPosObj, false);
                if (value instanceof List<?> list) {
                    return list;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Method findMethodCompat(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }
        Class<?>[] expectedParameters = parameterTypes == null ? new Class<?>[0] : parameterTypes;

        Method direct = findMatchingMethod(owner.getMethods(), name, expectedParameters, true);
        if (direct != null) {
            return direct;
        }

        direct = findMatchingMethod(owner.getDeclaredMethods(), name, expectedParameters, true);
        if (direct != null) {
            return direct;
        }

        if (("createItemStack".equals(name) || "a".equals(name)) && owner.getName().endsWith(".ItemInput")) {
            direct = findMatchingMethod(owner.getMethods(), "a", expectedParameters, true);
            if (direct != null) {
                return direct;
            }
            direct = findMatchingMethod(owner.getDeclaredMethods(), "a", expectedParameters, true);
            if (direct != null) {
                return direct;
            }
            direct = findMatchingMethod(owner.getMethods(), "m_120980_", expectedParameters, true);
            if (direct != null) {
                return direct;
            }
            direct = findMatchingMethod(owner.getDeclaredMethods(), "m_120980_", expectedParameters, true);
            if (direct != null) {
                return direct;
            }
        }

        Method fallback = findMatchingMethod(owner.getMethods(), name, expectedParameters, false);
        if (fallback != null) {
            return fallback;
        }

        fallback = findMatchingMethod(owner.getDeclaredMethods(), name, expectedParameters, false);
        if (fallback != null) {
            return fallback;
        }

        // Mojang-mapped/SRG servers may expose ItemInput#createItemStack with synthetic names.
        if ("createItemStack".equals(name) || "a".equals(name)) {
            Method heuristic = findItemStackFactoryLikeMethod(owner, expectedParameters);
            if (heuristic != null) {
                heuristic.setAccessible(true);
                return heuristic;
            }
        }

        // Holder#value became SRG method names; choose a no-arg non-void fallback.
        if ((("value".equals(name) || "a".equals(name)) && expectedParameters.length == 0)
                && owner.getName().endsWith(".Holder")) {
            Method heuristic = findNoArgNonVoidMethod(owner);
            if (heuristic != null) {
                heuristic.setAccessible(true);
                return heuristic;
            }
        }

        return null;
    }

    public static Method needMethodCompat(Class<?> owner, String name, Class<?>... parameterTypes) {
        Method method = findMethodCompat(owner, name, parameterTypes);
        if (method == null && "asBukkitCopy".equals(name)) {
            method = findCraftItemStackAsBukkitCopyCompat(owner, parameterTypes);
        }
        if (method != null) {
            method.setAccessible(true);
            return method;
        }
        throw new RuntimeException(new NoSuchMethodException(owner.getName() + "." + name));
    }

    public static Constructor<?> findConstructorCompat(Class<?> owner, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }

        Class<?>[] expectedParameters = parameterTypes == null ? new Class<?>[0] : parameterTypes;
        for (Class<?> ownerCandidate : constructorOwnerCandidatesCompat(owner)) {
            Constructor<?> constructor = findMatchingConstructorCompat(ownerCandidate.getDeclaredConstructors(), expectedParameters);
            if (constructor != null) {
                return constructor;
            }

            constructor = findMatchingConstructorCompat(ownerCandidate.getConstructors(), expectedParameters);
            if (constructor != null) {
                return constructor;
            }
        }

        if (isBlockPositionLikeClassCompat(owner)) {
            Constructor<?> constructor = findBlockPositionConstructorCompat(owner, expectedParameters);
            if (constructor != null) {
                return constructor;
            }
        }

        return null;
    }

    public static Constructor<?> needConstructorCompat(Class<?> owner, Class<?>... parameterTypes) {
        Constructor<?> constructor = findConstructorCompat(owner, parameterTypes);
        if (constructor != null) {
            constructor.setAccessible(true);
            return constructor;
        }
        throw new RuntimeException(new NoSuchMethodException(owner.getName() + ".<init>"));
    }

    private static Constructor<?> findMatchingConstructorCompat(Constructor<?>[] constructors, Class<?>[] expectedParameters) {
        if (constructors == null) {
            return null;
        }
        for (Constructor<?> constructor : constructors) {
            if (!constructorParameterTypesMatchCompat(constructor.getParameterTypes(), expectedParameters)) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor;
        }
        return null;
    }

    private static boolean constructorParameterTypesMatchCompat(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!isInvocationCompatibleParameterCompat(actual[i], expected[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInvocationCompatibleParameterCompat(Class<?> parameterType, Class<?> argumentType) {
        if (parameterType == null || argumentType == null) {
            return false;
        }

        Class<?> normalizedParameter = resolveLegacyNmsAliasClassCompat(parameterType);
        Class<?> normalizedArgument = resolveLegacyNmsAliasClassCompat(argumentType);
        if ((normalizedParameter != parameterType || normalizedArgument != argumentType)
                && isInvocationCompatibleParameterCoreCompat(normalizedParameter, normalizedArgument)) {
            return true;
        }

        return isInvocationCompatibleParameterCoreCompat(parameterType, argumentType);
    }

    private static boolean isInvocationCompatibleParameterCoreCompat(Class<?> parameterType, Class<?> argumentType) {
        Class<?> boxedParameter = boxType(parameterType);
        Class<?> boxedArgument = boxType(argumentType);
        if (boxedParameter == boxedArgument) {
            return true;
        }
        if (boxedParameter.isAssignableFrom(boxedArgument)) {
            return true;
        }
        if (boxedArgument.isAssignableFrom(boxedParameter)) {
            return true;
        }

        Class<?> primitiveParameter = primitiveTypeOfCompat(parameterType);
        Class<?> primitiveArgument = primitiveTypeOfCompat(argumentType);
        if (primitiveParameter != null && primitiveArgument != null) {
            return isWideningPrimitiveConversionCompat(primitiveArgument, primitiveParameter);
        }

        return false;
    }

    private static Class<?> primitiveTypeOfCompat(Class<?> type) {
        if (type == null) {
            return null;
        }
        if (type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.class) {
            return Boolean.TYPE;
        }
        if (type == Byte.class) {
            return Byte.TYPE;
        }
        if (type == Short.class) {
            return Short.TYPE;
        }
        if (type == Character.class) {
            return Character.TYPE;
        }
        if (type == Integer.class) {
            return Integer.TYPE;
        }
        if (type == Long.class) {
            return Long.TYPE;
        }
        if (type == Float.class) {
            return Float.TYPE;
        }
        if (type == Double.class) {
            return Double.TYPE;
        }
        return null;
    }

    private static boolean isWideningPrimitiveConversionCompat(Class<?> from, Class<?> to) {
        if (from == to) {
            return true;
        }
        if (from == Byte.TYPE) {
            return to == Short.TYPE || to == Integer.TYPE || to == Long.TYPE || to == Float.TYPE || to == Double.TYPE;
        }
        if (from == Short.TYPE) {
            return to == Integer.TYPE || to == Long.TYPE || to == Float.TYPE || to == Double.TYPE;
        }
        if (from == Character.TYPE) {
            return to == Integer.TYPE || to == Long.TYPE || to == Float.TYPE || to == Double.TYPE;
        }
        if (from == Integer.TYPE) {
            return to == Long.TYPE || to == Float.TYPE || to == Double.TYPE;
        }
        if (from == Long.TYPE) {
            return to == Float.TYPE || to == Double.TYPE;
        }
        if (from == Float.TYPE) {
            return to == Double.TYPE;
        }
        return false;
    }

    private static List<Class<?>> constructorOwnerCandidatesCompat(Class<?> owner) {
        List<Class<?>> candidates = new ArrayList<>();
        candidates.add(owner);

        Class<?> resolved = resolveLegacyNmsAliasClassCompat(owner);
        if (resolved != null && resolved != owner && !candidates.contains(resolved)) {
            candidates.add(resolved);
        }
        return candidates;
    }

    private static Class<?> resolveLegacyNmsAliasClassCompat(Class<?> type) {
        if (type == null) {
            return null;
        }

        String name = type.getName();
        if ("net.minecraft.core.BlockPosition".equals(name)) {
            Class<?> resolved = tryLoad(type.getClassLoader(), "net.minecraft.core.BlockPos");
            return resolved != null ? resolved : type;
        }

        int aliasSeparator = name.lastIndexOf('$');
        if (aliasSeparator < 0 || !name.startsWith("net.minecraft.server.")) {
            return type;
        }

        String alias = name.substring(aliasSeparator + 1);
        Class<?> resolved = switch (alias) {
            case "BlockPosition" -> tryLoad(type.getClassLoader(),
                    "net.minecraft.core.BlockPos",
                    "net.minecraft.core.BlockPosition");
            case "ShapeDetectorBlock" -> tryLoad(type.getClassLoader(),
                    "net.minecraft.world.level.block.state.pattern.BlockInWorld",
                    "net.minecraft.world.level.block.state.pattern.ShapeDetectorBlock");
            case "IWorldReader" -> tryLoad(type.getClassLoader(),
                    "net.minecraft.world.level.LevelReader",
                    "net.minecraft.world.level.IWorldReader");
            default -> null;
        };

        return resolved != null ? resolved : type;
    }

    private static boolean isBlockPositionLikeClassCompat(Class<?> owner) {
        if (owner == null) {
            return false;
        }
        String name = owner.getName();
        return name.contains("BlockPosition") || name.endsWith("BlockPos");
    }

    private static Constructor<?> findBlockPositionConstructorCompat(Class<?> owner, Class<?>[] expectedParameters) {
        ClassLoader classLoader = owner.getClassLoader();
        String ownerName = owner.getName();
        String ownerBlockPosName = ownerName.replace("BlockPosition", "BlockPos");

        Class<?> candidate = tryLoad(classLoader,
                "net.minecraft.core.BlockPos",
                ownerBlockPosName,
                ownerName);
        if (candidate != null) {
            Constructor<?> constructor = findMatchingConstructorCompat(candidate.getDeclaredConstructors(), expectedParameters);
            if (constructor != null) {
                return constructor;
            }
            constructor = findMatchingConstructorCompat(candidate.getConstructors(), expectedParameters);
            if (constructor != null) {
                return constructor;
            }
        }

        return null;
    }

    private static Method findCraftItemStackAsBukkitCopyCompat(Class<?> owner, Class<?>[] parameterTypes) {
        if (owner == null || !owner.getName().endsWith(".CraftItemStack")) {
            return null;
        }
        Method method = findCraftItemStackAsBukkitCopyCompat(owner.getMethods(), parameterTypes);
        if (method != null) {
            return method;
        }
        return findCraftItemStackAsBukkitCopyCompat(owner.getDeclaredMethods(), parameterTypes);
    }

    private static Method findCraftItemStackAsBukkitCopyCompat(Method[] methods, Class<?>[] parameterTypes) {
        for (Method method : methods) {
            if (!"asBukkitCopy".equals(method.getName())) {
                continue;
            }
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!"org.bukkit.inventory.ItemStack".equals(method.getReturnType().getName())) {
                continue;
            }

            Class<?> actualParam = method.getParameterTypes()[0];
            if (parameterTypes != null && parameterTypes.length == 1 && parameterTypes[0] != null) {
                Class<?> expectedParam = parameterTypes[0];
                Class<?> actualBoxed = boxType(actualParam);
                Class<?> expectedBoxed = boxType(expectedParam);
                if (actualBoxed != expectedBoxed
                        && !actualBoxed.isAssignableFrom(expectedBoxed)
                        && !expectedBoxed.isAssignableFrom(actualBoxed)) {
                    String actualSimple = actualBoxed.getSimpleName();
                    String expectedSimple = expectedBoxed.getSimpleName();
                    if (!"ItemStack".equals(actualSimple) || !"ItemStack".equals(expectedSimple)) {
                        continue;
                    }
                }
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    public static boolean isCommandSourceGetServerMethodCompat(Method method) {
        if (method == null) {
            return false;
        }
        if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        if (method.getParameterCount() != 0) {
            return false;
        }

        String methodName = method.getName();
        if ("getServer".equals(methodName)) {
            return true;
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }
        String returnTypeName = returnType.getName().toLowerCase(Locale.ROOT);
        return returnTypeName.contains("minecraftserver");
    }

    public static Object rpgRegionsOptionalOrElseGetServerMethodCompat(
            java.util.Optional<?> optional,
            java.util.function.Supplier<?> supplier
    ) {
        if (optional != null && optional.isPresent()) {
            return optional.get();
        }

        Method fallback = resolveRpgRegionsCommandSourceGetServerMethodCompat(supplier);
        if (fallback != null) {
            return fallback;
        }

        Object throwable = supplier == null ? null : supplier.get();
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        if (throwable instanceof Throwable th) {
            throw new RuntimeException(th);
        }
        if (throwable != null) {
            throw new IllegalStateException(String.valueOf(throwable));
        }
        throw new IllegalStateException("Could not find CommandSourceStack#getServer.");
    }

    private static Method resolveRpgRegionsCommandSourceGetServerMethodCompat(java.util.function.Supplier<?> supplier) {
        Class<?> parserClass = null;
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

        if (supplier != null) {
            try {
                Class<?> supplierClass = supplier.getClass();
                if (isRpgRegionsBlockPredicateParserClassCompat(supplierClass)) {
                    parserClass = supplierClass;
                } else if (isRpgRegionsBlockPredicateParserClassCompat(supplierClass.getEnclosingClass())) {
                    parserClass = supplierClass.getEnclosingClass();
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            Class<?> initialParserClass = parserClass;
            java.lang.StackWalker walker =
                    java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
            parserClass = walker.walk(stream -> stream
                    .map(java.lang.StackWalker.StackFrame::getDeclaringClass)
                    .filter(PluginFixManager::isRpgRegionsBlockPredicateParserClassCompat)
                    .findFirst()
                    .orElse(initialParserClass));
        } catch (Throwable ignored) {
        }

        if (parserClass != null && !looksLikeRpgRegionsBlockPredicateParserCompat(parserClass)) {
            parserClass = null;
        }

        if (parserClass == null) {
            String[] parserNames = new String[] {
                    "rpgregions-libs.commandframework.bukkit.parser.BlockPredicateParser",
                    "rpgregions.libs.commandframework.bukkit.parser.BlockPredicateParser"
            };
            for (String parserName : parserNames) {
                try {
                    parserClass = loadClassFromAnyLoaderCompat(parserName, contextLoader);
                    if (parserClass != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (parserClass == null) {
            return null;
        }

        ClassLoader parserLoader = parserClass.getClassLoader();
        Class<?> commandSourceClass = null;
        Class<?> minecraftServerClass = null;

        for (Field field : parserClass.getDeclaredFields()) {
            if (field.getType() != Class.class) {
                continue;
            }
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(null);
            } catch (Throwable ignored) {
                continue;
            }
            if (!(value instanceof Class<?> clazz)) {
                continue;
            }

            String fieldName = field.getName();
            String className = clazz.getName().toLowerCase(Locale.ROOT);

            if ("COMMAND_LISTENER_WRAPPER_CLASS".equals(fieldName)
                    || className.contains("commandsourcestack")
                    || className.contains("commandlistenerwrapper")) {
                commandSourceClass = clazz;
            }
            if ("MINECRAFT_SERVER_CLASS".equals(fieldName) || className.contains("minecraftserver")) {
                minecraftServerClass = clazz;
            }
        }

        if (commandSourceClass == null) {
            String[] commandSourceCandidates = new String[] {
                    "net.minecraft.commands.CommandSourceStack",
                    "net.minecraft.commands.CommandListenerWrapper",
                    "net.minecraft.server.v1_20_R1$CommandSourceStack",
                    "net.minecraft.server.v1_20_R1$CommandListenerWrapper",
                    "net.minecraft.server.v1_20_R1.CommandSourceStack",
                    "net.minecraft.server.v1_20_R1.CommandListenerWrapper"
            };
            for (String candidate : commandSourceCandidates) {
                try {
                    commandSourceClass = loadClassFromAnyLoaderCompat(candidate, parserLoader, contextLoader);
                    if (commandSourceClass != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (minecraftServerClass == null) {
            String[] minecraftServerCandidates = new String[] {
                    "net.minecraft.server.MinecraftServer",
                    "net.minecraft.server.v1_20_R1$MinecraftServer",
                    "net.minecraft.server.v1_20_R1.MinecraftServer"
            };
            for (String candidate : minecraftServerCandidates) {
                try {
                    minecraftServerClass = loadClassFromAnyLoaderCompat(candidate, parserLoader, contextLoader);
                    if (minecraftServerClass != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (commandSourceClass == null) {
            return null;
        }

        Method best = null;
        int bestScore = Integer.MIN_VALUE;
        Method[] declaredMethods = commandSourceClass.getDeclaredMethods();
        Method[] publicMethods = commandSourceClass.getMethods();
        Method[][] methodGroups = new Method[][] {declaredMethods, publicMethods};

        for (Method[] methods : methodGroups) {
            for (Method method : methods) {
                if (method == null) {
                    continue;
                }
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }

                Class<?> returnType = method.getReturnType();
                if (returnType == null || returnType == Void.TYPE) {
                    continue;
                }

                String methodName = method.getName();
                String returnTypeName = returnType.getName();
                String returnTypeNameLower = returnTypeName.toLowerCase(Locale.ROOT);
                boolean directGetServer = "getServer".equals(methodName);
                boolean looksLikeServerReturn =
                        returnTypeNameLower.contains("minecraftserver")
                                || returnTypeNameLower.contains("dedicatedserver");
                boolean sameNameAsExpectedServer = minecraftServerClass != null
                        && returnTypeName.equals(minecraftServerClass.getName());
                boolean assignableToExpectedServer = minecraftServerClass != null
                        && (returnType.isAssignableFrom(minecraftServerClass)
                        || minecraftServerClass.isAssignableFrom(returnType));

                if (!directGetServer
                        && !isCommandSourceGetServerMethodCompat(method)
                        && !looksLikeServerReturn
                        && !sameNameAsExpectedServer
                        && !assignableToExpectedServer) {
                    continue;
                }

                int score = 0;
                if (directGetServer) {
                    score += 1000;
                }
                if ("m_81377_".equals(methodName)) {
                    score += 500;
                }
                if (looksLikeServerReturn) {
                    score += 300;
                }
                if (sameNameAsExpectedServer) {
                    score += 200;
                }
                if (assignableToExpectedServer) {
                    score += 100;
                }
                if (!methodName.startsWith("m_")) {
                    score += 20;
                }

                method.setAccessible(true);
                if (directGetServer) {
                    return method;
                }
                if (score > bestScore) {
                    best = method;
                    bestScore = score;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static boolean isRpgRegionsBlockPredicateParserClassCompat(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        if (!looksLikeRpgRegionsBlockPredicateParserCompat(clazz)) {
            return false;
        }
        String className = clazz.getName();
        return !className.contains("$$Lambda$") && !className.contains("$Lambda$");
    }

    private static boolean looksLikeRpgRegionsBlockPredicateParserCompat(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        String className = clazz.getName();
        if (className.endsWith(".BlockPredicateParser")) {
            return true;
        }
        return "BlockPredicateParser".equals(clazz.getSimpleName());
    }

    private static Method findItemStackFactoryLikeMethod(Class<?> owner, Class<?>[] expectedParameters) {
        Method method = findItemStackFactoryLikeMethod(owner.getMethods(), expectedParameters);
        if (method != null) {
            return method;
        }
        return findItemStackFactoryLikeMethod(owner.getDeclaredMethods(), expectedParameters);
    }

    private static Method findItemStackFactoryLikeMethod(Method[] methods, Class<?>[] expectedParameters) {
        for (Method method : methods) {
            if (!parameterTypesMatch(method.getParameterTypes(), expectedParameters)) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == Void.TYPE) {
                continue;
            }
            String returnTypeName = returnType.getName();
            if (returnTypeName.endsWith(".ItemStack") || "net.minecraft.world.item.ItemStack".equals(returnTypeName)) {
                return method;
            }
        }
        return null;
    }

    private static Method findNoArgNonVoidMethod(Class<?> owner) {
        Method method = findNoArgNonVoidMethod(owner.getMethods());
        if (method != null) {
            return method;
        }
        return findNoArgNonVoidMethod(owner.getDeclaredMethods());
    }

    private static Method findNoArgNonVoidMethod(Method[] methods) {
        for (Method method : methods) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) {
                continue;
            }
            return method;
        }
        return null;
    }

    private static Method findMatchingMethod(Method[] methods, String expectedName, Class<?>[] expectedParameters, boolean requireName) {
        for (Method method : methods) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (requireName && (expectedName == null || !method.getName().equals(expectedName))) {
                continue;
            }
            if (!parameterTypesMatch(method.getParameterTypes(), expectedParameters)) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static boolean parameterTypesMatch(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            Class<?> expectedType = expected[i];
            if (expectedType == null) {
                return false;
            }
            Class<?> actualBoxed = boxType(actual[i]);
            Class<?> expectedBoxed = boxType(expectedType);
            if (actualBoxed == expectedBoxed) {
                continue;
            }
            if (!actualBoxed.isAssignableFrom(expectedBoxed) && !expectedBoxed.isAssignableFrom(actualBoxed)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Void.TYPE) {
            return Void.class;
        }
        return type;
    }

    public static Object firstNonNullOrThrowCompat(java.util.function.Supplier<String> messageSupplier, Object[] values) {
        if (values != null) {
            for (Object value : values) {
                if (value != null) {
                    return value;
                }
            }
        }

        String message = null;
        try {
            if (messageSupplier != null) {
                message = messageSupplier.get();
            }
        } catch (Throwable ignored) {
        }

        Object fallback = cloudFirstNonNullFallbackCompat(message);
        if (fallback != null) {
            return fallback;
        }

        throw new IllegalArgumentException(message == null ? "No non-null value found" : message);
    }

    private static Object cloudFirstNonNullFallbackCompat(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);

        if (normalized.contains("createitemstack method on iteminput")) {
            return resolveCloudCreateItemStackMethodCompat();
        }
        if (normalized.contains("item field on iteminput")) {
            return resolveCloudItemInputItemFieldCompat();
        }
        if (normalized.contains("tag field on iteminput")) {
            return resolveCloudItemInputExtraDataFieldCompat();
        }
        if (normalized.contains("holder#value")) {
            return resolveCloudHolderValueMethodCompat();
        }
        if (normalized.contains("cloud not find net.minecraft.nbt.tag")) {
            return resolveCloudNbtTagClassCompat();
        }
        return null;
    }

    private static Method resolveCloudCreateItemStackMethodCompat() {
        Class<?> itemInputClass = resolveItemInputClassCompat();
        if (itemInputClass == null) {
            return null;
        }
        Method method = findMethodCompat(itemInputClass, "createItemStack", int.class, boolean.class);
        if (method == null) {
            method = findMethodCompat(itemInputClass, "a", int.class, boolean.class);
        }
        if (method == null) {
            method = findMethodCompat(itemInputClass, "m_120980_", int.class, boolean.class);
        }
        if (method != null) {
            method.setAccessible(true);
        }
        return method;
    }

    private static Field resolveCloudItemInputItemFieldCompat() {
        Class<?> itemInputClass = resolveItemInputClassCompat();
        if (itemInputClass == null) {
            return null;
        }

        for (String fieldName : new String[]{"f_120973_", "item", "b"}) {
            Field field = findFieldByNameCompat(itemInputClass, fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field;
            }
        }

        for (Field field : itemInputClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String typeName = field.getType().getName();
            if (!typeName.contains(".Holder") && !typeName.endsWith(".Item")) {
                continue;
            }
            field.setAccessible(true);
            return field;
        }
        return null;
    }

    private static Field resolveCloudItemInputExtraDataFieldCompat() {
        Class<?> itemInputClass = resolveItemInputClassCompat();
        if (itemInputClass == null) {
            return null;
        }

        for (String fieldName : new String[]{"f_120974_", "components", "tag", "c"}) {
            Field field = findFieldByNameCompat(itemInputClass, fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field;
            }
        }

        Field itemField = resolveCloudItemInputItemFieldCompat();
        for (Field field : itemInputClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (itemField != null && field.getName().equals(itemField.getName())) {
                continue;
            }
            Class<?> type = field.getType();
            if (type.isPrimitive()) {
                continue;
            }
            String typeName = type.getName();
            if (!typeName.contains("Tag")
                    && !typeName.contains("Component")
                    && !typeName.contains("Patch")
                    && !typeName.contains("Data")
                    && !typeName.contains("NBT")) {
                continue;
            }
            field.setAccessible(true);
            return field;
        }
        return null;
    }

    private static Method resolveCloudHolderValueMethodCompat() {
        Class<?> holderClass = resolveHolderClassCompat();
        if (holderClass == null) {
            return null;
        }

        for (String methodName : new String[]{"value", "a", "m_203334_"}) {
            Method method = findMethodCompat(holderClass, methodName);
            if (method != null && method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE) {
                method.setAccessible(true);
                return method;
            }
        }

        Method method = findNoArgNonVoidMethod(holderClass);
        if (method != null) {
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static Class<?> resolveCloudNbtTagClassCompat() {
        Field extraDataField = resolveCloudItemInputExtraDataFieldCompat();
        if (extraDataField != null) {
            Class<?> fieldType = extraDataField.getType();
            if (fieldType != null && fieldType != Object.class) {
                return fieldType;
            }
        }

        for (String name : new String[]{
                "net.minecraft.nbt.Tag",
                "net.minecraft.nbt.NBTBase",
                "net.minecraft.nbt.CompoundTag",
                "net.minecraft.nbt.NBTTagCompound"}) {
            Class<?> clazz = loadClassCompat(name);
            if (clazz != null) {
                return clazz;
            }
        }
        return Object.class;
    }

    private static Class<?> resolveItemInputClassCompat() {
        for (String className : new String[]{
                "net.minecraft.commands.arguments.item.ItemInput",
                "net.minecraft.commands.arguments.item.ArgumentPredicateItemStack"}) {
            Class<?> clazz = loadClassCompat(className);
            if (clazz != null) {
                return clazz;
            }
        }
        return null;
    }

    private static Class<?> resolveHolderClassCompat() {
        return loadClassCompat("net.minecraft.core.Holder");
    }

    private static Class<?> loadClassCompat(String className) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                return Class.forName(className, false, classLoader);
            }
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static byte[] injectPluginFix(String className, byte[] clazz) {
        String normalizedClassName = className == null ? "" : className.replace('/', '.');
        List<Consumer<ClassNode>> handlers = null;

        if (normalizedClassName.endsWith("PaperLib")) {
            return patch(clazz, PluginFixManager::removePaper);
        }
        if (normalizedClassName.endsWith(".impl.reobf_1_20_r1.Heart")) {
            return patch(clazz, PluginFixManager::fixSparrowHeartSendPacketLookup);
        }
        if (normalizedClassName.equals("net.momirealms.customfishing.bukkit.item.damage.CustomDurabilityItem")) {
            return patch(clazz, PluginFixManager::fixCustomFishingDurabilityOptionalGet);
        }
        if (normalizedClassName.equals("net.momirealms.customfishing.libraries.rtag.tag.TagBase")) {
            return patch(clazz, PluginFixManager::fixCustomFishingTagBaseGetValue);
        }
        if (normalizedClassName.endsWith(".bukkit.internal.CraftBukkitReflection")) {
            return patch(clazz, PluginFixManager::fixCloudCraftBukkitReflectionFindMethod);
        }
        if (normalizedClassName.startsWith("de.oliver.fancyholograms.")) {
            return patch(clazz, PluginFixManager::fixFancyHologramsLocationAccessors);
        }
        if (normalizedClassName.startsWith("world.bentobox.")) {
            handlers = appendPatchHandler(handlers, PluginFixManager::fixBentoBoxPatternTypeCompat);
        }
        if (normalizedClassName.equals("com.onarandombox.MultiverseCore.utils.WorldManager")) {
            return patch(clazz, MultiverseCore::fix);
        }
        if (normalizedClassName.startsWith("io.lumine.mythiccrucible.")) {
            handlers = appendPatchHandler(handlers, PluginFixManager::fixMythicCrucibleInventoryViewInvoke);
            handlers = appendPatchHandler(handlers, PluginFixManager::fixMythicPaperApiCompat);
        }
        if (normalizedClassName.startsWith("io.lumine.mythic.")) {
            handlers = appendPatchHandler(handlers, PluginFixManager::fixMythicEntityTypeConstants);
            handlers = appendPatchHandler(handlers, PluginFixManager::fixMythicAttributeApiCompat);
            handlers = appendPatchHandler(handlers, PluginFixManager::fixMythicPaperApiCompat);
        }
        if (normalizedClassName.equals("revxrsal.zapper.DependencyManager")) {
            return patch(clazz, PluginFixManager::fixZapperDependencyManager);
        }
        if (normalizedClassName.endsWith(".libs.lamp.bukkit.brigadier.CommodoreProvider")) {
            return patch(clazz, PluginFixManager::fixLampCommodoreProvider);
        }
        if (normalizedClassName.startsWith("com.willfp.libreforge.")) {
            handlers = appendPatchHandler(handlers, PluginFixManager::fixLibreforgePaperOnlyListeners);
        }
        Consumer<ClassNode> patcher = switch (normalizedClassName) {
            case "com.sk89q.worldedit.bukkit.BukkitAdapter" -> WorldEdit::handleBukkitAdapter;
            case "com.sk89q.worldedit.bukkit.adapter.BukkitImplLoader" -> WorldEdit::handleBukkitImplLoader;
            case "com.sk89q.worldedit.bukkit.adapter.Refraction" -> WorldEdit::handlePickName;
            case "com.sk89q.worldedit.bukkit.adapter.impl.v1_20_R1.PaperweightAdapter$SpigotWatchdog" -> WorldEdit::handleWatchdog;
            case "com.sk89q.worldedit.bukkit.WorldEditPlugin" -> PluginFixManager::fixWorldEditPluginMaterialKeys;
            case "com.sk89q.worldedit.registry.Registry" -> PluginFixManager::fixWorldEditRegistryRegister;
            case "com.comphenix.protocol.wrappers.WrappedChatComponent" -> PluginFixManager::fixProtocolLibWrappedChatComponent;
            case "nexus.slime.f3nperm.provider.ProtocolLibProvider" -> PluginFixManager::fixF3NPermProtocolLibProvider;
            case "itemsadder.m.aun" -> PluginFixManager::fixAdventureSerializerBuilderOptionsCompat;
            case "itemsadder.m.ajw" -> PluginFixManager::fixItemsAdderPacketConnectionCompat;
            case "itemsadder.m.ya" -> PluginFixManager::fixItemsAdderServerVersionGate;
            case "itemsadder.m.yc" -> PluginFixManager::fixItemsAdderNmsVersionSwitchMap;
            case "ia.sh.com.alessiodp.libby.LibraryManager" -> PluginFixManager::fixItemsAdderLibbyLibraryManager;
            case "ia.sh.com.alessiodp.libby.transitive.TransitiveDependencyHelper" -> PluginFixManager::fixItemsAdderLibbyTransitiveDependencyHelper;
            case "net.kyori.adventure.platform.bukkit.BukkitComponentSerializer" -> PluginFixManager::fixAdventureSerializerBuilderOptionsCompat;
            case "com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_20_R1.PaperweightFaweAdapter" -> PluginFixManager::fixFawePaperweightFaweAdapter;
            case "com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_20_R1.PaperweightBlockMaterial" -> PluginFixManager::fixFawePaperweightBlockMaterial;
            case "com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_20_R1.PaperweightPlatformAdapter" -> PluginFixManager::fixFawePaperweightPlatformAdapter;
            case "com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_20_R1.PaperweightGetBlocks" -> PluginFixManager::fixFawePaperweightGetBlocks;
            case "com.sk89q.worldedit.bukkit.adapter.ext.fawe.v1_20_R1.PaperweightAdapter$1" -> PluginFixManager::fixFawePaperweightPropertyCacheLoader;
            case "com.sk89q.worldedit.world.block.BlockTypesCache" -> PluginFixManager::fixWorldEditBlockTypesCache;
            case "com.sk89q.worldedit.world.block.BlockTypesCache$Settings" -> PluginFixManager::fixWorldEditBlockTypesCacheSettings;
            case "com.earth2me.essentials.utils.VersionUtil" -> PluginFixManager::fixEssentialsVersionUtil;
            case "com.sk89q.worldedit.bukkit.BukkitConfiguration" -> node -> {
                helloWorld(node, "I accept that I will receive no support with this flag enabled.", "mohist");
                helloWorld(node, "allow-editing-on-unsupported-versions", "mohist");
                helloWorld(node, "false", "mohist");
            };
            case "net.Zrips.CMILib.Reflections" -> node -> helloWorld(node, "bR", "f_36096_");
            case "net.coreprotect.listener.ListenerHandler" -> node -> helloWorld(node, "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer", "mohist");
            case "io.lumine.mythic.bukkit.utils.version.ServerVersion" -> PluginFixManager::fixMythicMobsServerVersion;
            case "io.lumine.mythic.bukkit.BukkitBootstrap" -> PluginFixManager::fixMythicBukkitBootstrap;
            case "io.lumine.mythic.bukkit.adapters.BukkitParticle" -> PluginFixManager::fixMythicBukkitParticleCompat;
            case "io.lumine.mythic.core.volatilecode.v1_20_R1.VolatileAIHandlerImpl" -> PluginFixManager::fixMythicMobsAIHandler;
            case "io.lumine.mythic.core.skills.mechanics.SoundEffect" -> PluginFixManager::fixFancyHologramsLocationAccessors;
            case "io.lumine.mythiccrucible.items.ItemManager" -> PluginFixManager::fixMythicCrucibleItemManager;
            case "io.lumine.mythiccrucible.items.recipes.crafting.recipes.vmp.VanillaInventoryMapping" -> PluginFixManager::fixMythicCrucibleVanillaInventoryMapping;
            case "io.lumine.mythiccrucible.items.recipes_pre121.crafting.recipes.vmp.VanillaInventoryMapping" -> PluginFixManager::fixMythicCrucibleVanillaInventoryMapping;
            case "com.moulberry.axiom.AxiomPaper" -> PluginFixManager::fixAxiomPaper;
            case "com.moulberry.axiom.AxiomReflection" -> PluginFixManager::fixAxiomReflection;
            case "com.moulberry.axiom.WorldExtension" -> PluginFixManager::fixAxiomWorldExtension;
            case "net.playavalon.mythicdungeons.MythicDungeons" -> PluginFixManager::fixMythicDungeonsMain;
            case "net.playavalon.mythicdungeons.listeners.AvalonListener" -> PluginFixManager::fixMythicDungeonsAvalonListener;
            case "io.lumine.mythiccrucible.skills.SkillEventListeners" -> PluginFixManager::fixMythicCrucibleSkillEventListeners;
            case "net.momirealms.customnameplates.bukkit.BukkitNetworkManager$ServerChannelHandler" -> PluginFixManager::fixCustomNameplatesServerChannelHandler;
            case "com.willfp.eco.internal.events.EcoEventManager" -> PluginFixManager::fixEcoEventManagerUnregisterListener;
            case "com.willfp.eco.internal.spigot.proxy.v1_20_R1.common.packet.display.PacketSetSlot" -> PluginFixManager::fixEcoPacketSetSlotAsync;
            case "com.willfp.eco.core.Prerequisite" -> PluginFixManager::fixEcoPrerequisite;
            case "com.willfp.libreforge.integrations.paper.impl.EffectElytraBoostSaveChance" -> PluginFixManager::fixLibreforgeElytraBoostSaveChance;
            case "com.sarry20.topminion.TopMinion" -> PluginFixManager::fixTopMinionMain;
            case "revxrsal.commands.bukkit.brigadier.MinecraftArgumentType" -> PluginFixManager::fixRevxrsalMinecraftArgumentType;
            case "revxrsal.commands.bukkit.brigadier.BrigadierRegistryHook" -> PluginFixManager::fixRevxrsalBrigadierRegistryHook;
            case "revxrsal.commands.bukkit.brigadier.BukkitBrigadierBridge" -> PluginFixManager::fixRevxrsalBukkitBrigadierBridge;
            case "fr.minuskube.inv.InventoryManager$InvListener" -> PluginFixManager::fixSmartInvsInvListener;
            case "fr.elias.npcs.server.listener.PotionInteractionListener" -> PluginFixManager::fixModeledNpcsPotionInteractionListener;
            case "me.ulrich.clans.library.scoreboardlibrary.implementation.packetAdapter.modern.PacketAccessors" -> PluginFixManager::fixUltimateClansPacketAccessors;
            case "me.ulrich.clans.library.scoreboardlibrary.implementation.packetAdapter.util.reflect.ReflectUtil" -> PluginFixManager::fixUltimateClansReflectUtil;
            case "rpgregions-libs.commandframework.bukkit.parser.BlockPredicateParser" -> PluginFixManager::fixRpgRegionsBlockPredicateParser;
            default -> null;
        };

        handlers = appendPatchHandler(handlers, patcher);
        return handlers == null ? clazz : patch(clazz, handlers);
    }

    private static byte[] patch(byte[] basicClass, Consumer<ClassNode> handler) {
        return patch(basicClass, java.util.List.of(handler));
    }

    private static byte[] patch(byte[] basicClass, List<Consumer<ClassNode>> handlers) {
        ClassNode node = new ClassNode();
        // Drop debug metadata (LocalVariableTable/LineNumberTable) while patching.
        // This avoids malformed duplicated LVT entries produced by some plugin bytecode combinations.
        new ClassReader(basicClass).accept(node, ClassReader.SKIP_DEBUG);
        for (Consumer<ClassNode> handler : handlers) {
            handler.accept(node);
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static List<Consumer<ClassNode>> appendPatchHandler(List<Consumer<ClassNode>> handlers, Consumer<ClassNode> handler) {
        if (handler == null) {
            return handlers;
        }
        if (handlers == null) {
            handlers = new ArrayList<>(4);
        }
        handlers.add(handler);
        return handlers;
    }

    private static void fixProtocolLibWrappedChatComponent(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"<clinit>".equals(methodNode.name) || !"()V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new LdcInsnNode(Type.getType("Lnet/minecraft/network/chat/Component$Serializer;")));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "SERIALIZER",
                    "Ljava/lang/Class;"
            ));
            toInject.add(new LdcInsnNode(Type.getType("Lnet/minecraft/network/chat/Component;")));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "COMPONENT",
                    "Ljava/lang/Class;"
            ));
            toInject.add(new LdcInsnNode(Type.getType("Lcom/google/gson/Gson;")));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "GSON_CLASS",
                    "Ljava/lang/Class;"
            ));
            toInject.add(new LdcInsnNode(Type.getType("Lnet/minecraft/network/chat/MutableComponent;")));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/util/Optional",
                    "of",
                    "(Ljava/lang/Object;)Ljava/util/Optional;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "MUTABLE_COMPONENT_CLASS",
                    "Ljava/util/Optional;"
            ));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "protocolLibCreateChatSerializerGson",
                    "()Ljava/lang/Object;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "GSON",
                    "Ljava/lang/Object;"
            ));
            toInject.add(new InsnNode(Opcodes.ACONST_NULL));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "DESERIALIZE",
                    "Lcom/comphenix/protocol/reflect/accessors/MethodAccessor;"
            ));
            toInject.add(new LdcInsnNode(Type.getObjectType(node.name)));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getClassLoader",
                    "()Ljava/lang/ClassLoader;",
                    false
            ));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "protocolLibCreateWrappedChatSerializeAccessor",
                    "(Ljava/lang/ClassLoader;)Ljava/lang/Object;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST,
                    "com/comphenix/protocol/reflect/accessors/MethodAccessor"
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "SERIALIZE_COMPONENT",
                    "Lcom/comphenix/protocol/reflect/accessors/MethodAccessor;"
            ));
            toInject.add(new LdcInsnNode(Type.getObjectType(node.name)));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getClassLoader",
                    "()Ljava/lang/ClassLoader;",
                    false
            ));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "protocolLibCreateCraftChatMessageAccessor",
                    "(Ljava/lang/ClassLoader;)Ljava/lang/Object;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST,
                    "com/comphenix/protocol/reflect/accessors/MethodAccessor"
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "CONSTRUCT_COMPONENT",
                    "Lcom/comphenix/protocol/reflect/accessors/MethodAccessor;"
            ));
            toInject.add(new InsnNode(Opcodes.ACONST_NULL));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "CONSTRUCT_TEXT_COMPONENT",
                    "Lcom/comphenix/protocol/reflect/accessors/ConstructorAccessor;"
            ));
            toInject.add(new InsnNode(Opcodes.ACONST_NULL));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    "CODEC",
                    "Lcom/comphenix/protocol/wrappers/codecs/WrappedCodec;"
            ));
            toInject.add(new InsnNode(Opcodes.RETURN));

            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            methodNode.maxStack = 3;
            methodNode.maxLocals = 0;
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixWorldEditPluginMaterialKeys(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            boolean changed = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !"org/bukkit/Material".equals(methodInsnNode.owner)
                        || !"getKey".equals(methodInsnNode.name)
                        || !"()Lorg/bukkit/NamespacedKey;".equals(methodInsnNode.desc)) {
                    continue;
                }
                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "materialGetKeyCompat";
                methodInsnNode.desc = "(Lorg/bukkit/Material;)Lorg/bukkit/NamespacedKey;";
                methodInsnNode.itf = false;
                changed = true;
            }
            if (changed) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixWorldEditRegistryRegister(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"register".equals(methodNode.name)
                    || !"(Ljava/lang/String;Lcom/sk89q/worldedit/registry/Keyed;)Lcom/sk89q/worldedit/registry/Keyed;".equals(methodNode.desc)) {
                continue;
            }

            InsnList replacement = new InsnList();
            replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
            replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
            replacement.add(new VarInsnNode(Opcodes.ALOAD, 2));
            replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "worldEditRegistryRegisterCompat",
                    "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
                    false
            ));
            replacement.add(new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.CHECKCAST,
                    "com/sk89q/worldedit/registry/Keyed"
            ));
            replacement.add(new InsnNode(ARETURN));
            methodNode.instructions = replacement;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixEssentialsVersionUtil(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("getServerSupportStatus".equals(methodNode.name)
                    && "()Lcom/earth2me/essentials/utils/VersionUtil$SupportStatus;".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new LdcInsnNode(Type.getObjectType(node.name)));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "essentialsGetServerSupportStatusCompat",
                        "(Ljava/lang/Class;)Ljava/lang/Object;",
                        false
                ));
                replacement.add(new org.objectweb.asm.tree.TypeInsnNode(
                        Opcodes.CHECKCAST,
                        "com/earth2me/essentials/utils/VersionUtil$SupportStatus"
                ));
                replacement.add(new InsnNode(ARETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("getSupportStatusClass".equals(methodNode.name)
                    && "()Ljava/lang/String;".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                replacement.add(new InsnNode(ARETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("isServerSupported".equals(methodNode.name)
                    && "()Z".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new InsnNode(Opcodes.ICONST_1));
                replacement.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixF3NPermProtocolLibProvider(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"update".equals(methodNode.name) || !"(Lorg/bukkit/entity/Player;)V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new FieldInsnNode(
                    Opcodes.GETFIELD,
                    node.name,
                    "plugin",
                    "Lnexus/slime/f3nperm/F3NPermPlugin;"
            ));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "f3npermProtocolLibUpdateCompat",
                    "(Ljava/lang/Object;Lorg/bukkit/entity/Player;)V",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.RETURN));

            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            methodNode.maxStack = 2;
            methodNode.maxLocals = 2;
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixAdventureSerializerBuilderOptionsCompat(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"<clinit>".equals(methodNode.name) || !"()V".equals(methodNode.desc)) {
                continue;
            }

            boolean patched = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEINTERFACE) {
                    continue;
                }
                if (!"net/kyori/adventure/text/serializer/gson/GsonComponentSerializer$Builder".equals(methodInsnNode.owner)) {
                    continue;
                }
                if (!"options".equals(methodInsnNode.name)) {
                    continue;
                }
                if (!"(Lnet/kyori/option/OptionState;)Lnet/kyori/adventure/text/serializer/gson/GsonComponentSerializer$Builder;".equals(methodInsnNode.desc)) {
                    continue;
                }

                // Some plugins bundle newer Adventure platform/text helpers but still link
                // against the server's older GsonComponentSerializer.Builder, which does not
                // expose Builder#options(OptionState). Drop only the unsupported call and keep
                // the rest of the static initializer intact.
                methodNode.instructions.set(methodInsnNode, new InsnNode(Opcodes.POP));
                patched = true;
            }

            if (patched) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixBentoBoxPatternTypeCompat(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            boolean patched = false;

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKESTATIC) {
                    continue;
                }
                if (!"org/bukkit/block/banner/PatternType".equals(methodInsnNode.owner)) {
                    continue;
                }
                if (!"valueOf".equals(methodInsnNode.name)) {
                    continue;
                }
                if (!"(Ljava/lang/String;)Lorg/bukkit/block/banner/PatternType;".equals(methodInsnNode.desc)) {
                    continue;
                }

                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "patternTypeValueOfCompat";
                methodInsnNode.itf = false;
                patched = true;
            }

            if (patched) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixItemsAdderServerVersionGate(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"qM".equals(methodNode.name) || !"()Z".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new InsnNode(Opcodes.ICONST_1));
            toInject.add(new InsnNode(Opcodes.IRETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixItemsAdderNmsVersionSwitchMap(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"<clinit>".equals(methodNode.name) || !"()V".equals(methodNode.desc)) {
                continue;
            }

            boolean patched = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.RETURN) {
                    continue;
                }

                InsnList fallbackMappings = new InsnList();
                addItemsAdderSwitchMapFallback(fallbackMappings, node.name, "v1_20_R1", 10);
                addItemsAdderSwitchMapFallback(fallbackMappings, node.name, "v1_20_R2", 10);
                addItemsAdderSwitchMapFallback(fallbackMappings, node.name, "v1_20_R3", 10);
                addItemsAdderSwitchMapFallback(fallbackMappings, node.name, "v1_20_4", 10);
                addItemsAdderSwitchMapFallback(fallbackMappings, node.name, "v1_20_5", 10);
                methodNode.instructions.insertBefore(insn, fallbackMappings);
                patched = true;
            }

            if (patched) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void addItemsAdderSwitchMapFallback(InsnList instructions, String switchMapOwner, String versionFieldName, int switchValue) {
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                switchMapOwner,
                "PU",
                "[I"
        ));
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "beer/devs/fastnbt/nms/Version",
                versionFieldName,
                "Lbeer/devs/fastnbt/nms/Version;"
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "beer/devs/fastnbt/nms/Version",
                "ordinal",
                "()I",
                false
        ));
        instructions.add(new IntInsnNode(Opcodes.BIPUSH, switchValue));
        instructions.add(new InsnNode(Opcodes.IASTORE));
    }

    private static void fixItemsAdderPacketConnectionCompat(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"<clinit>".equals(methodNode.name) || !"()V".equals(methodNode.desc)) {
                continue;
            }

            boolean patched = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode ldcInsnNode)) {
                    continue;
                }
                if (!(ldcInsnNode.cst instanceof Type type)) {
                    continue;
                }
                if (!"net/minecraft/server/network/ServerCommonPacketListenerImpl".equals(type.getInternalName())) {
                    continue;
                }

                ldcInsnNode.cst = Type.getObjectType("net/minecraft/server/network/ServerGamePacketListenerImpl");
                patched = true;
            }

            if (patched) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixItemsAdderLibbyLibraryManager(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"resolveTransitiveLibraries".equals(methodNode.name)
                    || !"(Lia/sh/com/alessiodp/libby/Library;)V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "itemsAdderResolveTransitiveLibrariesCompat",
                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.RETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            methodNode.maxStack = 2;
            methodNode.maxLocals = 2;
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixItemsAdderLibbyTransitiveDependencyHelper(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("<init>".equals(methodNode.name)
                    && "(Lia/sh/com/alessiodp/libby/LibraryManager;Ljava/nio/file/Path;)V".equals(methodNode.desc)) {
                InsnList toInject = new InsnList();
                toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/lang/Object",
                        "<init>",
                        "()V",
                        false
                ));
                toInject.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                methodNode.maxStack = 1;
                methodNode.maxLocals = 3;
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("findTransitiveLibraries".equals(methodNode.name)
                    && "(Lia/sh/com/alessiodp/libby/Library;)Ljava/util/Collection;".equals(methodNode.desc)) {
                InsnList toInject = new InsnList();
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/util/Collections",
                        "emptyList",
                        "()Ljava/util/List;",
                        false
                ));
                toInject.add(new InsnNode(Opcodes.ARETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                methodNode.maxStack = 1;
                methodNode.maxLocals = 2;
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    public static Object protocolLibCreateChatSerializerGson() {
        com.google.gson.GsonBuilder gsonBuilder = new com.google.gson.GsonBuilder();
        gsonBuilder.disableHtmlEscaping();
        gsonBuilder.registerTypeHierarchyAdapter(net.minecraft.network.chat.Component.class, new net.minecraft.network.chat.Component.Serializer());
        gsonBuilder.registerTypeHierarchyAdapter(net.minecraft.network.chat.Style.class, new net.minecraft.network.chat.Style.Serializer());
        gsonBuilder.registerTypeAdapterFactory(new net.minecraft.util.LowerCaseEnumTypeAdapterFactory());
        return gsonBuilder.create();
    }

    public static Object protocolLibCreateWrappedChatSerializeAccessor(ClassLoader protocolLibLoader) {
        try {
            Method toJson = net.minecraft.network.chat.Component.Serializer.class.getDeclaredMethod(
                    "toJson",
                    net.minecraft.network.chat.Component.class
            );
            return protocolLibCreateMethodAccessor(protocolLibLoader, toJson);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to create ProtocolLib chat serializer accessor", ex);
        }
    }

    public static Object protocolLibCreateCraftChatMessageAccessor(ClassLoader protocolLibLoader) {
        try {
            Method fromString = org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage.class.getDeclaredMethod(
                    "fromString",
                    String.class,
                    boolean.class
            );
            return protocolLibCreateMethodAccessor(protocolLibLoader, fromString);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to create ProtocolLib CraftChatMessage accessor", ex);
        }
    }

    private static Object protocolLibCreateMethodAccessor(ClassLoader protocolLibLoader, Method method) throws ReflectiveOperationException {
        Class<?> accessorsClass = Class.forName(
                "com.comphenix.protocol.reflect.accessors.Accessors",
                true,
                protocolLibLoader
        );
        Method accessorFactory = accessorsClass.getDeclaredMethod("getMethodAccessor", Method.class);
        return accessorFactory.invoke(null, method);
    }

    public static void f3npermProtocolLibUpdateCompat(Object plugin, org.bukkit.entity.Player player) {
        try {
            Method getPermissionLevel = plugin.getClass().getMethod("getF3NPermPermissionLevel", org.bukkit.entity.Player.class);
            Object permissionLevel = getPermissionLevel.invoke(plugin, player);
            if (permissionLevel == null) {
                throw new IllegalStateException("F3NPerm returned a null permission level");
            }

            Method toStatusByte = permissionLevel.getClass().getMethod("toStatusByte");
            Object statusValue = toStatusByte.invoke(permissionLevel);
            byte status = statusValue instanceof Number number ? number.byteValue() : ((Byte) statusValue).byteValue();

            if (!(player instanceof CraftPlayer craftPlayer)) {
                throw new IllegalStateException("Could not resolve CraftPlayer handle");
            }

            Entity entity = craftPlayer.getHandle();
            ClientboundEntityEventPacket packet = new ClientboundEntityEventPacket(entity, status);
            craftPlayer.getHandle().connection.send(packet);
        } catch (Throwable throwable) {
            Throwable cause = throwable;
            if (cause instanceof java.lang.reflect.InvocationTargetException invocationTargetException
                    && invocationTargetException.getCause() != null) {
                cause = invocationTargetException.getCause();
            }
            throw f3npermProviderExceptionCompat(plugin, "Could not send status packet!", cause);
        }
    }

    private static RuntimeException f3npermProviderExceptionCompat(Object plugin, String message, Throwable cause) {
        try {
            ClassLoader classLoader = plugin != null
                    ? plugin.getClass().getClassLoader()
                    : PluginFixManager.class.getClassLoader();
            Class<?> providerExceptionClass = Class.forName(
                    "nexus.slime.f3nperm.provider.ProviderException",
                    true,
                    classLoader
            );
            Constructor<?> constructor = providerExceptionClass.getConstructor(String.class, Throwable.class);
            Object providerException = constructor.newInstance(message, cause);
            if (providerException instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
        } catch (Throwable ignored) {
        }
        return new RuntimeException(message, cause);
    }

    private static void fixCloudCraftBukkitReflectionFindMethod(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"findMethod".equals(methodNode.name)
                    || !"(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(methodNode.desc)) {
                if ("needMethod".equals(methodNode.name)
                        && "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(methodNode.desc)) {
                    InsnList toInject = new InsnList();
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                    toInject.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(PluginFixManager.class),
                            "needMethodCompat",
                            "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                            false
                    ));
                    toInject.add(new InsnNode(ARETURN));
                    methodNode.instructions = toInject;
                    methodNode.tryCatchBlocks.clear();
                    clearLocalDebugInfo(methodNode);
                }
                if ("firstNonNullOrThrow".equals(methodNode.name)
                        && "(Ljava/util/function/Supplier;[Ljava/lang/Object;)Ljava/lang/Object;".equals(methodNode.desc)) {
                    InsnList toInject = new InsnList();
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    toInject.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(PluginFixManager.class),
                            "firstNonNullOrThrowCompat",
                            "(Ljava/util/function/Supplier;[Ljava/lang/Object;)Ljava/lang/Object;",
                            false
                    ));
                    toInject.add(new InsnNode(ARETURN));
                    methodNode.instructions = toInject;
                    methodNode.tryCatchBlocks.clear();
                    clearLocalDebugInfo(methodNode);
                }
                if ("findConstructor".equals(methodNode.name)
                        && "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/reflect/Constructor;".equals(methodNode.desc)) {
                    InsnList toInject = new InsnList();
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    toInject.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(PluginFixManager.class),
                            "findConstructorCompat",
                            "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/reflect/Constructor;",
                            false
                    ));
                    toInject.add(new InsnNode(ARETURN));
                    methodNode.instructions = toInject;
                    methodNode.tryCatchBlocks.clear();
                    clearLocalDebugInfo(methodNode);
                }
                if ("needConstructor".equals(methodNode.name)
                        && "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/reflect/Constructor;".equals(methodNode.desc)) {
                    InsnList toInject = new InsnList();
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    toInject.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(PluginFixManager.class),
                            "needConstructorCompat",
                            "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/reflect/Constructor;",
                            false
                    ));
                    toInject.add(new InsnNode(ARETURN));
                    methodNode.instructions = toInject;
                    methodNode.tryCatchBlocks.clear();
                    clearLocalDebugInfo(methodNode);
                }
                continue;
            }
            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "findMethodCompat",
                    "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                    false
            ));
            toInject.add(new InsnNode(ARETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixUltimateClansPacketAccessors(ClassNode node) {
        // Force 1.21.5+ detection probe to fail on 1.20.1 runtime.
        // Some remapped environments expose unexpected classes and trigger wrong field layout branch.
        helloWorld(node, "net.minecraft.world.item.component.BlocksAttacks", "net.minecraft.world.item.component.__force_missing__.BlocksAttacks");

        for (MethodNode methodNode : node.methods) {
            if (!"<clinit>".equals(methodNode.name)) {
                continue;
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof org.objectweb.asm.tree.VarInsnNode varInsnNode)) {
                    continue;
                }
                if (varInsnNode.getOpcode() != Opcodes.ILOAD || (varInsnNode.var != 2 && varInsnNode.var != 3)) {
                    continue;
                }
                // Force 1.20.5+ / 1.21.5+ feature branches off on the 1.20.1 runtime.
                methodNode.instructions.set(varInsnNode, new InsnNode(Opcodes.ICONST_0));
            }
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixUltimateClansReflectUtil(ClassNode node) {
        final String fieldAccessorInternal =
                "me/ulrich/clans/library/scoreboardlibrary/implementation/packetAdapter/util/reflect/FieldAccessor";

        for (MethodNode methodNode : node.methods) {
            if (!"findFieldUnchecked".equals(methodNode.name)
                    || !("(Ljava/lang/Class;ILjava/lang/Class;)L" + fieldAccessorInternal + ";").equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 1));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "findFieldUncheckedCompat",
                    "(Ljava/lang/Class;ILjava/lang/Class;)Ljava/lang/Object;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, fieldAccessorInternal));
            toInject.add(new InsnNode(ARETURN));

            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixRpgRegionsBlockPredicateParser(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!methodNode.desc.equals("(Ljava/lang/reflect/Method;)Z")) {
                continue;
            }

            boolean referencesMinecraftServerClass = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsnNode
                        && fieldInsnNode.getOpcode() == Opcodes.GETSTATIC
                        && "MINECRAFT_SERVER_CLASS".equals(fieldInsnNode.name)
                        && "Ljava/lang/Class;".equals(fieldInsnNode.desc)) {
                    referencesMinecraftServerClass = true;
                    break;
                }
            }
            if (!referencesMinecraftServerClass) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "isCommandSourceGetServerMethodCompat",
                    "(Ljava/lang/reflect/Method;)Z",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.IRETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }

        for (MethodNode methodNode : node.methods) {
            if (!"<clinit>".equals(methodNode.name)) {
                continue;
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (!"java/util/Optional".equals(methodInsnNode.owner)
                        || !"orElseThrow".equals(methodInsnNode.name)
                        || !"(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(methodInsnNode.desc)) {
                    continue;
                }
                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "rpgRegionsOptionalOrElseGetServerMethodCompat";
                methodInsnNode.desc = "(Ljava/util/Optional;Ljava/util/function/Supplier;)Ljava/lang/Object;";
                methodInsnNode.itf = false;
            }
            clearLocalDebugInfo(methodNode);
        }
    }

    public static Object findFieldUncheckedCompat(Class<?> owner, int index, Class<?> fieldType) {
        if (owner == null || fieldType == null) {
            throw new IllegalStateException("couldn't find field with class null on null at index " + index);
        }

        boolean requiresStringEnumBridge = false;
        List<Field> fields = getNonStaticDeclaredFieldsCompat(owner);
        Field matched = selectFieldByIndexCompat(fields, index, field -> field.getType() == fieldType);
        if (matched == null) {
            matched = selectFieldByIndexCompat(fields, index, field ->
                    fieldType.isAssignableFrom(field.getType()) || field.getType().isAssignableFrom(fieldType));
        }
        if (matched == null && fieldType.isEnum()) {
            matched = selectEnumCompatibleFieldByIndexCompat(fields, fieldType, index);
        }
        if (matched == null && fieldType.isEnum()) {
            matched = selectStringBackedEnumFieldCompat(fields, fieldType, index);
            requiresStringEnumBridge = matched != null;
        }

        if (matched == null) {
            throw new IllegalStateException(
                    "couldn't find field with class "
                            + fieldType.getSimpleName()
                            + " on "
                            + owner.getSimpleName()
                            + " at index "
                            + index
            );
        }

        try {
            if (requiresStringEnumBridge) {
                return createUltimateClansConvertingFieldAccessorCompat(matched);
            }
            return createUltimateClansFieldAccessorCompat(matched);
        } catch (Throwable throwable) {
            throw new RuntimeException("failed to unreflect field setter", throwable);
        }
    }

    private static List<Field> getNonStaticDeclaredFieldsCompat(Class<?> owner) {
        List<Field> fields = new ArrayList<>();
        for (Field field : owner.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            fields.add(field);
        }
        return fields;
    }

    private static Field selectFieldByIndexCompat(List<Field> fields, int index, java.util.function.Predicate<Field> matcher) {
        int found = 0;
        for (Field field : fields) {
            if (!matcher.test(field)) {
                continue;
            }
            if (found == index) {
                return field;
            }
            found++;
        }
        return null;
    }

    private static Field selectEnumCompatibleFieldByIndexCompat(List<Field> fields, Class<?> expectedEnumType, int index) {
        class RankedField {
            final Field field;
            final int score;
            final int order;

            RankedField(Field field, int score, int order) {
                this.field = field;
                this.score = score;
                this.order = order;
            }
        }

        List<RankedField> ranked = new ArrayList<>();
        int order = 0;
        for (Field field : fields) {
            Class<?> candidateType = field.getType();
            if (!candidateType.isEnum()) {
                order++;
                continue;
            }
            int score = enumCompatibilityScoreCompat(expectedEnumType, candidateType);
            if (score > 0) {
                ranked.add(new RankedField(field, score, order));
            }
            order++;
        }

        ranked.sort((first, second) -> {
            int byScore = Integer.compare(second.score, first.score);
            if (byScore != 0) {
                return byScore;
            }
            return Integer.compare(first.order, second.order);
        });

        if (index >= 0 && index < ranked.size()) {
            return ranked.get(index).field;
        }
        return null;
    }

    private static Field selectStringBackedEnumFieldCompat(List<Field> fields, Class<?> expectedEnumType, int index) {
        String expectedName = expectedEnumType.getSimpleName().toLowerCase(Locale.ROOT);
        int preferredIndex = index;
        if (expectedName.contains("visibility")) {
            preferredIndex = 0;
        } else if (expectedName.contains("collision")) {
            preferredIndex = 1;
        }

        int found = 0;
        for (Field field : fields) {
            if (field.getType() != String.class) {
                continue;
            }
            if (found == preferredIndex) {
                return field;
            }
            found++;
        }
        return null;
    }

    private static int enumCompatibilityScoreCompat(Class<?> expectedEnumType, Class<?> candidateEnumType) {
        if (expectedEnumType == null || candidateEnumType == null) {
            return 0;
        }
        if (!expectedEnumType.isEnum() || !candidateEnumType.isEnum()) {
            return 0;
        }

        Set<String> expectedConstants = enumConstantNamesCompat(expectedEnumType);
        Set<String> candidateConstants = enumConstantNamesCompat(candidateEnumType);
        if (expectedConstants.isEmpty() || candidateConstants.isEmpty()) {
            return 0;
        }

        int overlap = 0;
        for (String constant : expectedConstants) {
            if (candidateConstants.contains(constant)) {
                overlap++;
            }
        }
        if (overlap == 0) {
            return 0;
        }

        int score = overlap * 10;
        String expectedName = expectedEnumType.getSimpleName().toLowerCase(Locale.ROOT);
        String candidateName = candidateEnumType.getSimpleName().toLowerCase(Locale.ROOT);

        if (expectedName.contains("visibility") && candidateName.contains("visibility")) {
            score += 5;
        }
        if (expectedName.contains("collision") && candidateName.contains("collision")) {
            score += 5;
        }
        if (expectedName.contains("nametag") && candidateName.contains("visibility")) {
            score += 5;
        }

        return score;
    }

    private static Set<String> enumConstantNamesCompat(Class<?> enumType) {
        Set<String> names = new java.util.HashSet<>();
        Object[] constants = enumType.getEnumConstants();
        if (constants == null) {
            return names;
        }
        for (Object constant : constants) {
            if (constant instanceof Enum<?> enumConstant) {
                names.add(enumConstant.name());
            } else if (constant != null) {
                names.add(String.valueOf(constant));
            }
        }
        return names;
    }

    private static Object createUltimateClansFieldAccessorCompat(Field field) throws ReflectiveOperationException {
        field.setAccessible(true);
        Class<?> fieldAccessorClass = resolveUltimateClansFieldAccessorClassCompat(field.getDeclaringClass().getClassLoader());
        Constructor<?> accessorConstructor = fieldAccessorClass.getDeclaredConstructor(java.lang.invoke.MethodHandle.class);
        accessorConstructor.setAccessible(true);

        java.lang.invoke.MethodHandle setterHandle = resolveUltimateClansFieldSetterHandleCompat(field, fieldAccessorClass.getClassLoader());
        return accessorConstructor.newInstance(setterHandle);
    }

    private static Object createUltimateClansConvertingFieldAccessorCompat(Field field) throws ReflectiveOperationException {
        field.setAccessible(true);
        Class<?> fieldAccessorClass = resolveUltimateClansFieldAccessorClassCompat(field.getDeclaringClass().getClassLoader());
        Constructor<?> accessorConstructor = fieldAccessorClass.getDeclaredConstructor(java.lang.invoke.MethodHandle.class);
        accessorConstructor.setAccessible(true);

        Method setterMethod = PluginFixManager.class.getDeclaredMethod(
                "setFieldValueCompat",
                Field.class,
                Object.class,
                Object.class
        );
        setterMethod.setAccessible(true);
        java.lang.invoke.MethodHandle setterHandle = java.lang.invoke.MethodHandles.lookup()
                .unreflect(setterMethod)
                .bindTo(field)
                .asType(java.lang.invoke.MethodType.methodType(void.class, Object.class, Object.class));

        return accessorConstructor.newInstance(setterHandle);
    }

    private static Class<?> resolveUltimateClansFieldAccessorClassCompat(ClassLoader preferredLoader) throws ClassNotFoundException {
        try {
            java.lang.StackWalker walker = java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
            java.util.List<java.lang.StackWalker.StackFrame> frames = walker.walk(stream -> stream.toList());
            for (java.lang.StackWalker.StackFrame frame : frames) {
                Class<?> declaringClass = frame.getDeclaringClass();
                if (declaringClass == null) {
                    continue;
                }
                String className = declaringClass.getName();
                if (!className.endsWith(".ReflectUtil")) {
                    continue;
                }
                if (!className.contains(".scoreboardlibrary.")) {
                    continue;
                }
                for (Method method : declaringClass.getDeclaredMethods()) {
                    if (!"findFieldUnchecked".equals(method.getName())) {
                        continue;
                    }
                    if (method.getParameterCount() != 3) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] != Class.class || params[1] != int.class || params[2] != Class.class) {
                        continue;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType != null && returnType != Object.class) {
                        return returnType;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return loadClassFromAnyLoaderCompat(
                "me.ulrich.clans.library.scoreboardlibrary.implementation.packetAdapter.util.reflect.FieldAccessor",
                preferredLoader
        );
    }

    private static void setFieldValueCompat(Field field, Object target, Object value) {
        try {
            Object converted = coerceFieldValueCompat(field.getType(), value);
            field.set(target, converted);
        } catch (Throwable throwable) {
            throw new IllegalStateException("couldn't set value of field", throwable);
        }
    }

    private static Object coerceFieldValueCompat(Class<?> targetType, Object value) {
        if (value == null) {
            return null;
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if (targetType == String.class && value instanceof Enum<?> enumValue) {
            return enumValue.name().toLowerCase(Locale.ROOT);
        }
        if (targetType.isEnum() && value instanceof String stringValue) {
            String normalized = stringValue.toUpperCase(Locale.ROOT);
            for (Object constant : targetType.getEnumConstants()) {
                if (!(constant instanceof Enum<?> enumConstant)) {
                    continue;
                }
                if (enumConstant.name().equalsIgnoreCase(normalized)) {
                    return enumConstant;
                }
            }
        }
        return value;
    }

    private static java.lang.invoke.MethodHandle resolveUltimateClansFieldSetterHandleCompat(Field field, ClassLoader classLoader)
            throws ReflectiveOperationException {
        java.lang.invoke.MethodHandle setterHandle = null;

        try {
            Class<?> reflectUtilClass = loadClassFromAnyLoaderCompat(
                    "me.ulrich.clans.library.scoreboardlibrary.implementation.packetAdapter.util.reflect.ReflectUtil",
                    classLoader,
                    field.getDeclaringClass().getClassLoader()
            );
            Field lookupField = reflectUtilClass.getDeclaredField("LOOKUP");
            lookupField.setAccessible(true);
            Object lookupObject = lookupField.get(null);
            if (lookupObject instanceof java.lang.invoke.MethodHandles.Lookup lookup) {
                setterHandle = lookup.unreflectSetter(field);
            }
        } catch (Throwable ignored) {
        }

        if (setterHandle == null) {
            try {
                setterHandle = java.lang.invoke.MethodHandles.lookup().unreflectSetter(field);
            } catch (Throwable ignored) {
            }
        }

        if (setterHandle == null) {
            Method fieldSetMethod = Field.class.getMethod("set", Object.class, Object.class);
            setterHandle = java.lang.invoke.MethodHandles.lookup().unreflect(fieldSetMethod).bindTo(field);
        }

        return setterHandle.asType(java.lang.invoke.MethodType.methodType(void.class, Object.class, Object.class));
    }

    private static Class<?> loadClassFromAnyLoaderCompat(String name, ClassLoader... preferred) throws ClassNotFoundException {
        java.util.LinkedHashSet<ClassLoader> loaders = new java.util.LinkedHashSet<>();
        if (preferred != null) {
            for (ClassLoader loader : preferred) {
                if (loader != null) {
                    loaders.add(loader);
                }
            }
        }

        collectStackClassLoadersCompat(loaders);
        collectBukkitPluginClassLoadersCompat(loaders);

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            loaders.add(contextLoader);
        }

        ClassLoader ownLoader = PluginFixManager.class.getClassLoader();
        if (ownLoader != null) {
            loaders.add(ownLoader);
        }

        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        if (systemLoader != null) {
            loaders.add(systemLoader);
        }

        for (ClassLoader loader : loaders) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        return Class.forName(name);
    }

    private static void collectStackClassLoadersCompat(Set<ClassLoader> loaders) {
        if (loaders == null) {
            return;
        }
        try {
            java.lang.StackWalker walker = java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
            walker.forEach(frame -> {
                Class<?> declaringClass = frame.getDeclaringClass();
                if (declaringClass == null) {
                    return;
                }
                ClassLoader loader = declaringClass.getClassLoader();
                if (loader != null) {
                    loaders.add(loader);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void collectBukkitPluginClassLoadersCompat(Set<ClassLoader> loaders) {
        if (loaders == null) {
            return;
        }
        try {
            org.bukkit.plugin.PluginManager pluginManager = org.bukkit.Bukkit.getPluginManager();
            if (pluginManager == null) {
                return;
            }
            org.bukkit.plugin.Plugin[] plugins = pluginManager.getPlugins();
            if (plugins == null) {
                return;
            }
            for (org.bukkit.plugin.Plugin plugin : plugins) {
                if (plugin == null) {
                    continue;
                }
                ClassLoader loader = plugin.getClass().getClassLoader();
                if (loader != null) {
                    loaders.add(loader);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void removePaper(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (methodNode.name.equals("isPaper") && methodNode.desc.equals("()Z")) {
                InsnList toInject = new InsnList();
                toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(PluginFixManager.class), "isPaper", "()Z"));
                toInject.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = toInject;
            }
        }
    }

    private static void replaceReturn(ClassNode node, String methodName, Object idc) {
        for (MethodNode methodNode : node.methods) {
            if (methodNode.name.equals(methodName)) {
                InsnList toInject = new InsnList();

                toInject.add(new LdcInsnNode(idc));
                toInject.add(new InsnNode(ARETURN));

                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
            }
        }
    }

    public static boolean isPaper() {
        return true;
    }

    public static void initAxiomReflection() {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> reflectionClass = Class.forName("com.moulberry.axiom.AxiomReflection", false, classLoader);
            Field tickerField = reflectionClass.getDeclaredField("updateBlockEntityTicker");
            tickerField.setAccessible(true);

            Method tickerMethod = (Method) tickerField.get(null);
            if (tickerMethod != null) {
                return;
            }

            Class<?> chunkClass = tryLoad(classLoader,
                    "net.minecraft.world.level.chunk.LevelChunk",
                    "net.minecraft.world.level.chunk.Chunk");
            Class<?> blockEntityClass = tryLoad(classLoader,
                    "net.minecraft.world.level.block.entity.BlockEntity",
                    "net.minecraft.world.level.block.entity.TileEntity");

            if (chunkClass == null || blockEntityClass == null) {
                return;
            }

            Method method = findDeclared(chunkClass, "updateBlockEntityTicker", blockEntityClass);
            if (method == null) {
                for (Method candidate : chunkClass.getDeclaredMethods()) {
                    if (candidate.getParameterCount() != 1 || candidate.getReturnType() != Void.TYPE) {
                        continue;
                    }
                    Class<?> paramType = candidate.getParameterTypes()[0];
                    if (!paramType.isAssignableFrom(blockEntityClass) && !blockEntityClass.isAssignableFrom(paramType)) {
                        continue;
                    }
                    String name = candidate.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("ticker") || name.contains("entity") || name.startsWith("m_")) {
                        method = candidate;
                        break;
                    }
                }
            }

            if (method != null) {
                method.setAccessible(true);
                tickerField.set(null, method);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void updateAxiomBlockEntityTicker(Object chunk, Object blockEntity) {
        if (chunk == null || blockEntity == null) {
            return;
        }

        try {
            ClassLoader classLoader = chunk.getClass().getClassLoader();
            Class<?> reflectionClass = Class.forName("com.moulberry.axiom.AxiomReflection", false, classLoader);
            Field tickerField = reflectionClass.getDeclaredField("updateBlockEntityTicker");
            tickerField.setAccessible(true);
            Method tickerMethod = (Method) tickerField.get(null);
            if (tickerMethod != null) {
                tickerMethod.invoke(chunk, blockEntity);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method setBlockEntity = findDeclared(chunk.getClass(), "setBlockEntity", blockEntity.getClass());
            if (setBlockEntity == null) {
                for (Method candidate : chunk.getClass().getDeclaredMethods()) {
                    if (candidate.getParameterCount() != 1 || candidate.getReturnType() != Void.TYPE) {
                        continue;
                    }
                    if (!"setBlockEntity".equals(candidate.getName())) {
                        continue;
                    }
                    if (candidate.getParameterTypes()[0].isAssignableFrom(blockEntity.getClass())) {
                        setBlockEntity = candidate;
                        break;
                    }
                }
            }
            if (setBlockEntity != null) {
                setBlockEntity.setAccessible(true);
                setBlockEntity.invoke(chunk, blockEntity);
            }
        } catch (Throwable ignored) {
        }
    }

    public static int relightCompat(Object lightEngine, Set<?> chunks, Consumer<Object> chunkCallback, IntConsumer doneCallback) {
        if (chunks == null || chunks.isEmpty()) {
            if (doneCallback != null) {
                doneCallback.accept(0);
            }
            return 0;
        }

        if (lightEngine != null) {
            try {
                Method relight = findDeclared(lightEngine.getClass(), "relight", Set.class, Consumer.class, IntConsumer.class);
                if (relight != null) {
                    relight.setAccessible(true);
                    Object result = relight.invoke(lightEngine, chunks, chunkCallback, doneCallback);
                    if (result instanceof Integer value) {
                        return value;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        int count = 0;
        for (Object chunk : chunks) {
            if (chunkCallback != null) {
                chunkCallback.accept(chunk);
            }
            ++count;
        }
        if (doneCallback != null) {
            doneCallback.accept(count);
        }
        return count;
    }

    public static LevelChunk getChunkAtIfCachedImmediatelyCompat(Object chunkSource, int chunkX, int chunkZ) {
        if (chunkSource == null) {
            return null;
        }

        String[] preferredMethods = new String[] {
                "getChunkAtIfLoadedImmediately",
                "getChunkAtIfCachedImmediately",
                "getChunkNow"
        };
        for (String preferredMethod : preferredMethods) {
            try {
                Method method = chunkSource.getClass().getDeclaredMethod(preferredMethod, int.class, int.class);
                method.setAccessible(true);
                Object result = method.invoke(chunkSource, chunkX, chunkZ);
                return result instanceof LevelChunk levelChunk ? levelChunk : null;
            } catch (Throwable ignored) {
            }
        }

        for (Method method : chunkSource.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 2) {
                continue;
            }
            if (method.getParameterTypes()[0] != int.class || method.getParameterTypes()[1] != int.class) {
                continue;
            }
            if (!LevelChunk.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("chunk") && !name.startsWith("m_")) {
                continue;
            }

            try {
                method.setAccessible(true);
                Object result = method.invoke(chunkSource, chunkX, chunkZ);
                return result instanceof LevelChunk levelChunk ? levelChunk : null;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Class<?> tryLoad(ClassLoader classLoader, String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, classLoader);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findDeclared(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getDeclaredMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void fixAxiomReflection(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (methodNode.name.equals("init") && methodNode.desc.equals("()V")) {
                InsnList toInject = new InsnList();
                toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(PluginFixManager.class), "initAxiomReflection", "()V"));
                toInject.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if (methodNode.name.equals("updateBlockEntityTicker")) {
                InsnList toInject = new InsnList();
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(PluginFixManager.class), "updateAxiomBlockEntityTicker", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
                toInject.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
            }
        }
    }

    private static void fixAxiomWorldExtension(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (!"relight".equals(methodInsnNode.name)) {
                    continue;
                }
                if (!"(Ljava/util/Set;Ljava/util/function/Consumer;Ljava/util/function/IntConsumer;)I".equals(methodInsnNode.desc)) {
                    continue;
                }
                if (!"net/minecraft/server/level/LightEngineThreaded".equals(methodInsnNode.owner)
                        && !"net/minecraft/server/level/ThreadedLevelLightEngine".equals(methodInsnNode.owner)) {
                    continue;
                }

                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "relightCompat";
                methodInsnNode.desc = "(Ljava/lang/Object;Ljava/util/Set;Ljava/util/function/Consumer;Ljava/util/function/IntConsumer;)I";
                methodInsnNode.itf = false;
            }
        }
    }

    private static void fixMythicDungeonsAvalonListener(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"onLoadBeton".equals(methodNode.name)) {
                continue;
            }
            if (!"(Lorg/bukkit/event/server/PluginEnableEvent;)V".equals(methodNode.desc)) {
                continue;
            }
            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "applyMythicDungeonsBetonQuestCompat",
                    "(Ljava/lang/Object;)V",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.RETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixMythicDungeonsMain(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"onEnable".equals(methodNode.name) || !"()V".equals(methodNode.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode ldcInsnNode) || !(ldcInsnNode.cst instanceof String text)) {
                    continue;
                }
                if (text.contains("Incompatible BetonQuest version!")) {
                    ldcInsnNode.cst = "&eBetonQuest v3 bridge will be applied by OneWorldCore.";
                } else if (text.contains("BetonQuest compatibility has not been enabled.")) {
                    ldcInsnNode.cst = "&aBetonQuest compatibility bridge is being enabled.";
                }
            }
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                InsnList hook = new InsnList();
                hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "applyMythicDungeonsBetonQuestCompat",
                        "(Ljava/lang/Object;)V",
                        false
                ));
                methodNode.instructions.insertBefore(insn, hook);
            }
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixMythicMobsServerVersion(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"isPaper".equals(methodNode.name) || !"()Z".equals(methodNode.desc)) {
                continue;
            }
            InsnList toInject = new InsnList();
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "mythicIsPaperCompat",
                    "()Z",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.IRETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixMythicEntityTypeConstants(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            boolean changed = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (!(insn instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsnNode)) {
                    insn = next;
                    continue;
                }
                if (fieldInsnNode.getOpcode() != Opcodes.GETSTATIC
                        || !"org/bukkit/entity/EntityType".equals(fieldInsnNode.owner)
                        || !"Lorg/bukkit/entity/EntityType;".equals(fieldInsnNode.desc)) {
                    insn = next;
                    continue;
                }
                InsnList replacement = new InsnList();
                replacement.add(new LdcInsnNode(fieldInsnNode.name));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "resolveEntityTypeCompat",
                        "(Ljava/lang/String;)Lorg/bukkit/entity/EntityType;",
                        false
                ));
                methodNode.instructions.insertBefore(insn, replacement);
                methodNode.instructions.remove(insn);
                changed = true;
                insn = next;
            }
            if (changed) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixMythicAttributeApiCompat(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            boolean changed = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEINTERFACE
                        || !"org/bukkit/attribute/Attribute".equals(methodInsnNode.owner)
                        || !"getKey".equals(methodInsnNode.name)
                        || !"()Lorg/bukkit/NamespacedKey;".equals(methodInsnNode.desc)) {
                    continue;
                }
                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "attributeGetKeyCompat";
                methodInsnNode.desc = "(Lorg/bukkit/attribute/Attribute;)Lorg/bukkit/NamespacedKey;";
                methodInsnNode.itf = false;
                changed = true;
            }
            if (changed) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixMythicPaperApiCompat(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            boolean changed = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "org/bukkit/entity/Player".equals(methodInsnNode.owner)
                        && "getActiveItem".equals(methodInsnNode.name)
                        && "()Lorg/bukkit/inventory/ItemStack;".equals(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "playerGetActiveItemCompat";
                    methodInsnNode.desc = "(Lorg/bukkit/entity/Player;)Lorg/bukkit/inventory/ItemStack;";
                    methodInsnNode.itf = false;
                    changed = true;
                    continue;
                }
                if ((methodInsnNode.getOpcode() == Opcodes.INVOKEVIRTUAL || methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE)
                        && "org/bukkit/inventory/ItemStack".equals(methodInsnNode.owner)
                        && "getDataOrDefault".equals(methodInsnNode.name)
                        && isMythicDataComponentAccessDescriptor(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "itemStackGetDataOrDefaultCompat";
                    methodInsnNode.desc = "(Lorg/bukkit/inventory/ItemStack;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                    methodInsnNode.itf = false;
                    changed = true;
                    continue;
                }
                if (methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "org/bukkit/entity/ArmorStand".equals(methodInsnNode.owner)
                        && "setCanTick".equals(methodInsnNode.name)
                        && "(Z)V".equals(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "armorStandSetCanTickCompat";
                    methodInsnNode.desc = "(Lorg/bukkit/entity/ArmorStand;Z)V";
                    methodInsnNode.itf = false;
                    changed = true;
                    continue;
                }
                if (methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "org/bukkit/entity/ArmorStand".equals(methodInsnNode.owner)
                        && "setCanMove".equals(methodInsnNode.name)
                        && "(Z)V".equals(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "armorStandSetCanMoveCompat";
                    methodInsnNode.desc = "(Lorg/bukkit/entity/ArmorStand;Z)V";
                    methodInsnNode.itf = false;
                    changed = true;
                    continue;
                }
                if (methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "org/bukkit/entity/ArmorStand".equals(methodInsnNode.owner)
                        && "setDisabledSlots".equals(methodInsnNode.name)
                        && "([Lorg/bukkit/inventory/EquipmentSlot;)V".equals(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "armorStandSetDisabledSlotsCompat";
                    methodInsnNode.desc = "(Lorg/bukkit/entity/ArmorStand;[Lorg/bukkit/inventory/EquipmentSlot;)V";
                    methodInsnNode.itf = false;
                    changed = true;
                    continue;
                }
                if (methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "org/bukkit/inventory/meta/ItemMeta".equals(methodInsnNode.owner)
                        && "setPlaceableKeys".equals(methodInsnNode.name)
                        && "(Ljava/util/Collection;)V".equals(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "itemMetaSetPlaceableKeysCompat";
                    methodInsnNode.desc = "(Lorg/bukkit/inventory/meta/ItemMeta;Ljava/util/Collection;)V";
                    methodInsnNode.itf = false;
                    changed = true;
                    continue;
                }
                if (methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "org/bukkit/inventory/meta/ItemMeta".equals(methodInsnNode.owner)
                        && "setDestroyableKeys".equals(methodInsnNode.name)
                        && "(Ljava/util/Collection;)V".equals(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "itemMetaSetDestroyableKeysCompat";
                    methodInsnNode.desc = "(Lorg/bukkit/inventory/meta/ItemMeta;Ljava/util/Collection;)V";
                    methodInsnNode.itf = false;
                    changed = true;
                }
            }
            if (changed) {
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static boolean isMythicDataComponentAccessDescriptor(String descriptor) {
        if (descriptor == null) {
            return false;
        }
        return "(Lio/papermc/paper/datacomponent/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor)
                || "(Lio/papermc/paper/datacomponent/DataComponentType$Valued;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor)
                || "(Lcom/oneworldstudiomc/paper/datacomponent/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor)
                || "(Lcom/oneworldstudiomc/paper/datacomponent/DataComponentType$Valued;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
    }

    private static void fixMythicBukkitBootstrap(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"createBossBar".equals(methodNode.name)) {
                continue;
            }
            if (!"(Ljava/lang/String;Lio/lumine/mythic/api/adapters/AbstractBossBar$BarColor;Lio/lumine/mythic/api/adapters/AbstractBossBar$BarStyle;)Lio/lumine/mythic/api/adapters/AbstractBossBar;".equals(methodNode.desc)) {
                continue;
            }
            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.TypeInsnNode(
                    Opcodes.NEW,
                    "io/lumine/mythic/bukkit/adapters/bossbars/BukkitBossBar"
            ));
            toInject.add(new InsnNode(Opcodes.DUP));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 3));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "io/lumine/mythic/bukkit/adapters/bossbars/BukkitBossBar",
                    "<init>",
                    "(Ljava/lang/String;Lio/lumine/mythic/api/adapters/AbstractBossBar$BarColor;Lio/lumine/mythic/api/adapters/AbstractBossBar$BarStyle;)V",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.ARETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixMythicBukkitParticleCompat(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"get".equals(methodNode.name)
                    || !"(Ljava/lang/String;)Lio/lumine/mythic/bukkit/adapters/BukkitParticle;".equals(methodNode.desc)) {
                continue;
            }
            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "normalizeMythicParticleNameCompat",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ASTORE, 0));
            methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), toInject);
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixFancyHologramsLocationAccessors(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (!"org/bukkit/Location".equals(methodInsnNode.owner) || !"()D".equals(methodInsnNode.desc)) {
                    continue;
                }
                switch (methodInsnNode.name) {
                    case "x" -> methodInsnNode.name = "getX";
                    case "y" -> methodInsnNode.name = "getY";
                    case "z" -> methodInsnNode.name = "getZ";
                    default -> {
                    }
                }
            }
        }
    }

    private static void fixFawePaperweightFaweAdapter(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("ibdIDToOrdinal".equals(methodNode.name) && "(I)C".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 1));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "faweIbdIdToOrdinalCompat",
                        "(Ljava/lang/Object;I)C",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("adaptToChar".equals(methodNode.name)
                    && "(Lnet/minecraft/world/level/block/state/IBlockData;)C".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "faweAdaptToCharCompat",
                        "(Ljava/lang/Object;Ljava/lang/Object;)C",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("init".equals(methodNode.name) && "()Z".equals(methodNode.desc)) {
                for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof IntInsnNode intInsnNode)
                            || intInsnNode.getOpcode() != Opcodes.NEWARRAY
                            || intInsnNode.operand != Opcodes.T_CHAR) {
                        continue;
                    }

                    // FAWE assumes dense NMS block-state IDs and sizes by BlockTypesCache.states.length.
                    // On hybrid modded servers IDs can be sparse and larger than that length.
                    InsnList resize = new InsnList();
                    resize.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(PluginFixManager.class),
                            "computeFaweStateArrayLength",
                            "(I)I",
                            false
                    ));
                    methodNode.instructions.insertBefore(intInsnNode, resize);
                    break;
                }
                clearLocalDebugInfo(methodNode);
            }
        }
        clearClassLocalDebugInfo(node);
    }

    public static int computeFaweStateArrayLength(int worldEditStateCount) {
        int safeCount = Math.max(worldEditStateCount, 2);
        int detectedCapacity = detectFaweStateRegistryCapacityCompat();
        int base = Math.max(safeCount, detectedCapacity);

        // Keep headroom for sparse IDs from Forge/Mohist hybrid registries.
        long padded = Math.max((long) base + 4096L, (long) safeCount * 2L);
        if (padded > Integer.MAX_VALUE - 8L) {
            return Integer.MAX_VALUE - 8;
        }
        return (int) padded;
    }

    private static int detectFaweStateRegistryCapacityCompat() {
        try {
            Object registry = resolveNmsBlockStateRegistryCompat();
            if (registry == null) {
                return -1;
            }
            return extractRegistryCapacityCompat(registry);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static Object resolveNmsBlockStateRegistryCompat() {
        try {
            Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
            for (String fieldName : new String[]{"o", "BLOCK_STATE_REGISTRY", "BLOCK_STATE_IDS"}) {
                try {
                    Field field = blockClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value != null) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int extractRegistryCapacityCompat(Object registry) {
        int max = -1;
        Class<?> cursor = registry.getClass();

        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(registry);
                    if (value == null) {
                        continue;
                    }

                    Class<?> type = field.getType();
                    if (type.isArray()) {
                        max = Math.max(max, java.lang.reflect.Array.getLength(value));
                        continue;
                    }
                    if (value instanceof java.util.Collection<?> collection) {
                        max = Math.max(max, collection.size());
                        continue;
                    }
                    if (value instanceof java.util.Map<?, ?> map) {
                        max = Math.max(max, map.size());
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }

        for (String methodName : new String[]{"size", "b", "getSize"}) {
            try {
                Method method = registry.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0 || method.getReturnType() != int.class) {
                    continue;
                }
                method.setAccessible(true);
                Object value = method.invoke(registry);
                if (value instanceof Number number) {
                    max = Math.max(max, number.intValue());
                }
            } catch (Throwable ignored) {
            }
            try {
                Method method = registry.getClass().getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0 || method.getReturnType() != int.class) {
                    continue;
                }
                method.setAccessible(true);
                Object value = method.invoke(registry);
                if (value instanceof Number number) {
                    max = Math.max(max, number.intValue());
                }
            } catch (Throwable ignored) {
            }
        }
        return max;
    }

    private static int getNmsBlockStateIdCompat(Object blockStateObj) {
        if (blockStateObj == null) {
            return 0;
        }

        Object registry = resolveNmsBlockStateRegistryCompat();
        if (registry == null) {
            return 0;
        }

        for (String methodName : new String[]{"a", "getId", "id"}) {
            Method method = findMethodCompat(registry.getClass(), methodName, Object.class);
            if (method == null) {
                continue;
            }
            try {
                Object value = method.invoke(registry, blockStateObj);
                if (value instanceof Number number) {
                    return Math.max(0, number.intValue());
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static void ensureFaweAdapterInitCompat(Object adapterObj) {
        if (adapterObj == null) {
            return;
        }
        try {
            Field initialisedField = findFieldByNameCompat(adapterObj.getClass(), "initialised");
            if (initialisedField != null) {
                initialisedField.setAccessible(true);
                Object value = initialisedField.get(adapterObj);
                if (value instanceof Boolean ready && ready) {
                    return;
                }
            }
            Method initMethod = findMethodByNameAndArity(adapterObj.getClass(), "init", 0);
            if (initMethod != null) {
                initMethod.setAccessible(true);
                initMethod.invoke(adapterObj);
            }
        } catch (Throwable ignored) {
        }
    }

    public static char faweIbdIdToOrdinalCompat(Object adapterObj, int ibdId) {
        try {
            ensureFaweAdapterInitCompat(adapterObj);
            Field mappingField = findFieldByNameCompat(adapterObj.getClass(), "ibdToStateOrdinal");
            if (mappingField == null) {
                return (char) 1;
            }

            mappingField.setAccessible(true);
            Object mappingObj = mappingField.get(adapterObj);
            if (!(mappingObj instanceof char[] mapping) || mapping.length == 0) {
                return (char) 1;
            }
            if (ibdId < 0 || ibdId >= mapping.length) {
                return (char) 1;
            }

            char ordinal = mapping[ibdId];
            return ordinal == 0 ? (char) 1 : ordinal;
        } catch (Throwable ignored) {
            return (char) 1;
        }
    }

    public static char faweAdaptToCharCompat(Object adapterObj, Object blockStateObj) {
        int stateId = getNmsBlockStateIdCompat(blockStateObj);
        return faweIbdIdToOrdinalCompat(adapterObj, stateId);
    }

    private static void fixSparrowHeartSendPacketLookup(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"<init>".equals(methodNode.name) || !"()V".equals(methodNode.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                    continue;
                }
                if (!"java/lang/Class".equals(methodInsnNode.owner)
                        || !"getDeclaredMethod".equals(methodInsnNode.name)
                        || !"(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(methodInsnNode.desc)) {
                    continue;
                }

                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "resolveDeclaredMethodCompat";
                methodInsnNode.desc = "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;";
                methodInsnNode.itf = false;
            }
        }
    }

    private static void fixTopMinionMain(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("revision".equals(methodNode.name) && "()V".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "topMinionRevisionCompat",
                        "(Ljava/lang/Object;)V",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("onDisable".equals(methodNode.name) && "()V".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "topMinionOnDisableCompat",
                        "(Ljava/lang/Object;)V",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("autoSave".equals(methodNode.name) && "()V".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "topMinionAutoSaveCompat",
                        "(Ljava/lang/Object;)V",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
            }
        }
        clearClassLocalDebugInfo(node);
    }

    public static void topMinionRevisionCompat(Object pluginObj) {
        if (pluginObj == null) {
            return;
        }
        try {
            Class<?> type = pluginObj.getClass();
            Field versionField = findFieldByNameCompat(type, "version");
            Field latestVersionField = findFieldByNameCompat(type, "latestVersion");
            if (latestVersionField == null || versionField == null) {
                return;
            }

            versionField.setAccessible(true);
            latestVersionField.setAccessible(true);
            Object versionValue = versionField.get(pluginObj);
            if (versionValue instanceof String version && !version.isEmpty()) {
                latestVersionField.set(pluginObj, version);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fixRevxrsalMinecraftArgumentType(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("create".equals(methodNode.name)
                    && "([Ljava/lang/Object;)Lcom/mojang/brigadier/arguments/ArgumentType;".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "revxrsalMinecraftArgumentTypeCreateCompat",
                        "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                        false
                ));
                replacement.add(new org.objectweb.asm.tree.TypeInsnNode(
                        Opcodes.CHECKCAST,
                        "com/mojang/brigadier/arguments/ArgumentType"
                ));
                replacement.add(new InsnNode(ARETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("get".equals(methodNode.name)
                    && "()Lcom/mojang/brigadier/arguments/ArgumentType;".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new InsnNode(Opcodes.ICONST_0));
                replacement.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "revxrsalMinecraftArgumentTypeCreateCompat",
                        "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                        false
                ));
                replacement.add(new org.objectweb.asm.tree.TypeInsnNode(
                        Opcodes.CHECKCAST,
                        "com/mojang/brigadier/arguments/ArgumentType"
                ));
                replacement.add(new InsnNode(ARETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixRevxrsalBrigadierRegistryHook(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("createBridge".equals(methodNode.name)
                    && "()Lrevxrsal/commands/bukkit/brigadier/BukkitBrigadierBridge;".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                replacement.add(new InsnNode(ARETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("onRegistered".equals(methodNode.name)
                    && "(Lrevxrsal/commands/command/ExecutableCommand;Lrevxrsal/commands/hook/CancelHandle;)V".equals(methodNode.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = replacement;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
            }
        }
    }

    private static void fixRevxrsalBukkitBrigadierBridge(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"getAliases".equals(methodNode.name)
                    || !"(Lorg/bukkit/command/Command;)Ljava/util/List;".equals(methodNode.desc)) {
                continue;
            }

            InsnList replacement = new InsnList();
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "revxrsalGetAliasesCompat",
                    "(Lorg/bukkit/command/Command;)Ljava/util/List;",
                    false
            ));
            replacement.add(new InsnNode(ARETURN));
            methodNode.instructions = replacement;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixSmartInvsInvListener(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                    continue;
                }
                if (!"org/bukkit/inventory/InventoryView".equals(methodInsnNode.owner)) {
                    continue;
                }
                if (!"getTopInventory".equals(methodInsnNode.name)
                        || !"()Lorg/bukkit/inventory/Inventory;".equals(methodInsnNode.desc)) {
                    continue;
                }
                methodInsnNode.setOpcode(Opcodes.INVOKEINTERFACE);
                methodInsnNode.itf = true;
            }
        }
    }

    private static void fixModeledNpcsPotionInteractionListener(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (!(insn instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsnNode)) {
                    insn = next;
                    continue;
                }
                if (fieldInsnNode.getOpcode() != Opcodes.GETSTATIC
                        || !"org/bukkit/potion/PotionEffectType".equals(fieldInsnNode.owner)
                        || !"Lorg/bukkit/potion/PotionEffectType;".equals(fieldInsnNode.desc)) {
                    insn = next;
                    continue;
                }

                InsnList replacement = new InsnList();
                replacement.add(new LdcInsnNode(fieldInsnNode.name));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "resolvePotionEffectTypeCompat",
                        "(Ljava/lang/String;)Lorg/bukkit/potion/PotionEffectType;",
                        false
                ));
                methodNode.instructions.insertBefore(fieldInsnNode, replacement);
                methodNode.instructions.remove(fieldInsnNode);
                insn = next;
            }
        }
    }

    public static Object revxrsalMinecraftArgumentTypeCreateCompat(Object enumValueObj, Object[] args) {
        Object[] actualArgs = args == null ? new Object[0] : args;
        if (enumValueObj == null) {
            return createRevxrsalFallbackArgumentCompat(null);
        }
        try {
            Field constructorField = findFieldByNameCompat(enumValueObj.getClass(), "argumentConstructor");
            Field cachedArgumentField = findFieldByNameCompat(enumValueObj.getClass(), "argumentType");
            if (constructorField == null || cachedArgumentField == null) {
                return createRevxrsalFallbackArgumentCompat(enumValueObj);
            }
            constructorField.setAccessible(true);
            cachedArgumentField.setAccessible(true);

            Object cachedArgument = cachedArgumentField.get(enumValueObj);
            if (cachedArgument != null && actualArgs.length == 0) {
                return cachedArgument;
            }

            Object constructorObj = constructorField.get(enumValueObj);
            if (!(constructorObj instanceof Constructor<?> constructor)) {
                return createRevxrsalFallbackArgumentCompat(enumValueObj);
            }
            constructor.setAccessible(true);
            return constructor.newInstance(actualArgs);
        } catch (Throwable ignored) {
            return createRevxrsalFallbackArgumentCompat(enumValueObj);
        }
    }

    public static java.util.List<String> revxrsalGetAliasesCompat(org.bukkit.command.Command command) {
        if (command == null) {
            return java.util.Collections.emptyList();
        }

        java.util.LinkedHashSet<String> aliases = new java.util.LinkedHashSet<>();
        String label = command.getLabel();
        if (label != null && !label.isBlank()) {
            aliases.add(label);
        }

        try {
            java.util.List<String> commandAliases = command.getAliases();
            if (commandAliases != null) {
                for (String alias : commandAliases) {
                    if (alias != null && !alias.isBlank()) {
                        aliases.add(alias);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (command instanceof org.bukkit.command.PluginCommand pluginCommand) {
            try {
                org.bukkit.plugin.Plugin plugin = pluginCommand.getPlugin();
                if (plugin != null) {
                    String prefix = plugin.getName();
                    if (prefix != null) {
                        prefix = prefix.toLowerCase(Locale.ROOT).trim();
                        java.util.List<String> names = new java.util.ArrayList<>(aliases);
                        for (String alias : names) {
                            aliases.add(prefix + ":" + alias);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return new java.util.ArrayList<>(aliases);
    }

    private static Object createRevxrsalFallbackArgumentCompat(Object enumValueObj) {
        String enumName = enumValueObj instanceof Enum<?> enumValue
                ? enumValue.name().toLowerCase(Locale.ROOT)
                : "";
        if (enumName.contains("message")) {
            Object greedy = invokeStringArgumentFactoryCompat("greedyString");
            if (greedy != null) {
                return greedy;
            }
        }
        Object word = invokeStringArgumentFactoryCompat("word");
        if (word != null) {
            return word;
        }
        return invokeStringArgumentFactoryCompat("string");
    }

    private static Object invokeStringArgumentFactoryCompat(String factoryMethodName) {
        try {
            Class<?> stringArgumentType = Class.forName("com.mojang.brigadier.arguments.StringArgumentType");
            Method method = stringArgumentType.getMethod(factoryMethodName);
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void topMinionOnDisableCompat(Object pluginObj) {
        if (pluginObj == null) {
            return;
        }
        try {
            Field executeDisableField = findFieldByNameCompat(pluginObj.getClass(), "executeDisable");
            if (executeDisableField != null) {
                executeDisableField.setAccessible(true);
                Object value = executeDisableField.get(pluginObj);
                if (value instanceof Boolean flag && !flag) {
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Field upgradeManagerField = findFieldByNameCompat(pluginObj.getClass(), "upgradeManager");
            if (upgradeManagerField == null) {
                return;
            }
            upgradeManagerField.setAccessible(true);
            Object upgradeManager = upgradeManagerField.get(pluginObj);
            if (upgradeManager == null) {
                return;
            }

            for (String methodName : new String[]{"save", "saveData", "saveAll", "shutdown", "disable", "close"}) {
                Method method = findMethodByNameAndArity(upgradeManager.getClass(), methodName, 0);
                if (method == null || method.getReturnType() != Void.TYPE) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(upgradeManager);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void topMinionAutoSaveCompat(Object pluginObj) {
        if (!(pluginObj instanceof org.bukkit.plugin.Plugin plugin)) {
            return;
        }
        if (!REGISTERED_TOPMINION_AUTOSAVE.add(pluginObj)) {
            return;
        }

        Runnable saveTask = () -> topMinionSaveNowCompat(pluginObj);
        long periodTicks = 20L * 300L;

        try {
            org.bukkit.scheduler.BukkitScheduler scheduler = org.bukkit.Bukkit.getScheduler();
            if (scheduler != null) {
                scheduler.runTaskTimer(plugin, saveTask, periodTicks, periodTicks);
                return;
            }
        } catch (Throwable ignored) {
        }

        topMinionSaveNowCompat(pluginObj);
    }

    private static void topMinionSaveNowCompat(Object pluginObj) {
        if (pluginObj == null) {
            return;
        }
        try {
            Field upgradeManagerField = findFieldByNameCompat(pluginObj.getClass(), "upgradeManager");
            if (upgradeManagerField == null) {
                return;
            }
            upgradeManagerField.setAccessible(true);
            Object upgradeManager = upgradeManagerField.get(pluginObj);
            if (upgradeManager == null) {
                return;
            }

            for (String methodName : new String[]{"save", "saveData", "saveAll", "autosave", "flush"}) {
                Method method = findMethodByNameAndArity(upgradeManager.getClass(), methodName, 0);
                if (method == null || method.getReturnType() != Void.TYPE) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(upgradeManager);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static org.bukkit.potion.PotionEffectType resolvePotionEffectTypeCompat(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim().toUpperCase(Locale.ROOT);

        org.bukkit.potion.PotionEffectType effectType = resolvePotionEffectByStaticFieldCompat(normalized);
        if (effectType != null) {
            return effectType;
        }

        for (String alias : potionEffectAliasesCompat(normalized)) {
            effectType = org.bukkit.potion.PotionEffectType.getByName(alias);
            if (effectType != null) {
                return effectType;
            }
            try {
                effectType = org.bukkit.potion.PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(alias.toLowerCase(Locale.ROOT)));
            } catch (Throwable ignored) {
                effectType = null;
            }
            if (effectType != null) {
                return effectType;
            }
        }

        effectType = org.bukkit.potion.PotionEffectType.getByName("SLOW");
        if (effectType != null) {
            return effectType;
        }
        return org.bukkit.potion.PotionEffectType.getByName("SPEED");
    }

    private static org.bukkit.potion.PotionEffectType resolvePotionEffectByStaticFieldCompat(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        try {
            Field field = org.bukkit.potion.PotionEffectType.class.getField(fieldName);
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            Object value = field.get(null);
            if (value instanceof org.bukkit.potion.PotionEffectType effectType) {
                return effectType;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String[] potionEffectAliasesCompat(String fieldName) {
        return switch (fieldName) {
            case "SLOWNESS" -> new String[]{"SLOWNESS", "SLOW"};
            case "HASTE" -> new String[]{"HASTE", "FAST_DIGGING"};
            case "MINING_FATIGUE" -> new String[]{"MINING_FATIGUE", "SLOW_DIGGING"};
            case "STRENGTH" -> new String[]{"STRENGTH", "INCREASE_DAMAGE"};
            case "JUMP_BOOST" -> new String[]{"JUMP_BOOST", "JUMP"};
            case "RESISTANCE" -> new String[]{"RESISTANCE", "DAMAGE_RESISTANCE"};
            case "INSTANT_HEALTH" -> new String[]{"INSTANT_HEALTH", "HEAL"};
            case "INSTANT_DAMAGE" -> new String[]{"INSTANT_DAMAGE", "HARM"};
            default -> fieldName.isEmpty() ? new String[0] : new String[]{fieldName};
        };
    }

    private static Field findFieldByNameCompat(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Method resolveDeclaredMethodCompat(Class<?> owner, String name, Class<?>[] params) throws NoSuchMethodException {
        try {
            return owner.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException ignored) {
        }

        if ("sendPacket".equals(name) && params != null && params.length == 3) {
            Method method = findSendPacketLikeMethod(owner, params);
            if (method != null) {
                return method;
            }
        }

        Method fallback = findCompatibleDeclaredMethod(owner, name, params);
        if (fallback != null) {
            return fallback;
        }

        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    public static Object optionalGetOrZeroCompat(java.util.Optional<?> optional) {
        if (optional != null && optional.isPresent()) {
            return optional.get();
        }
        return Integer.valueOf(0);
    }

    public static Object applyThrowableFunctionCompat(Object function, Object value) throws Throwable {
        try {
            return invokeApplyCompat(function, value);
        } catch (Throwable throwable) {
            // CustomFishing rtag can fail on primitive MethodHandle signatures (e.g. NBTTagInt getter).
            Object fallback = extractNbtPrimitiveValueCompat(value);
            if (fallback != null) {
                return fallback;
            }
            throw throwable;
        }
    }

    public static void fireChannelReadCompat(Object context, Object message) {
        if (context == null) {
            return;
        }

        if (CUSTOMNAMEPLATES_FALLBACK_LOGGED.compareAndSet(false, true)) {
            org.bukkit.Bukkit.getLogger().info(
                    "[OneWorldCore] Enabled CustomNameplates network fallback (missing PreChannelInitializer); suppressing repeated NoClassDefFoundError spam."
            );
        }

        try {
            Method method = findMethodByNameAndArity(context.getClass(), "fireChannelRead", 1);
            if (method == null) {
                return;
            }
            method.setAccessible(true);
            method.invoke(context, message);
        } catch (Throwable throwable) {
            if (CUSTOMNAMEPLATES_FALLBACK_ERROR_LOGGED.compareAndSet(false, true)) {
                org.bukkit.Bukkit.getLogger().warning(
                        "[OneWorldCore] CustomNameplates fallback channel forwarding failed once: "
                                + throwable.getClass().getSimpleName()
                                + " - "
                                + throwable.getMessage()
                );
            }
        }
    }

    public static void unregisterHandlerListListenerCompat(Object listener) {
        if (!(listener instanceof org.bukkit.event.Listener bukkitListener)) {
            return;
        }

        try {
            org.bukkit.event.HandlerList.unregisterAll(bukkitListener);
        } catch (Throwable throwable) {
            if (ECO_UNREGISTER_FALLBACK_LOGGED.compareAndSet(false, true)) {
                org.bukkit.Bukkit.getLogger().warning(
                        "[OneWorldCore] Suppressing repeated eco unregisterListener exception: "
                                + throwable.getClass().getSimpleName()
                                + " - "
                                + throwable.getMessage()
                );
            }
        }
    }

    private static Object invokeApplyCompat(Object function, Object value) throws Throwable {
        if (function == null) {
            throw new NullPointerException("ThrowableFunction is null");
        }

        Method apply = null;
        try {
            apply = function.getClass().getMethod("apply", Object.class);
        } catch (NoSuchMethodException ignored) {
        }
        if (apply == null) {
            for (Method method : function.getClass().getMethods()) {
                if ("apply".equals(method.getName()) && method.getParameterCount() == 1) {
                    apply = method;
                    break;
                }
            }
        }
        if (apply == null) {
            for (Method method : function.getClass().getDeclaredMethods()) {
                if ("apply".equals(method.getName()) && method.getParameterCount() == 1) {
                    apply = method;
                    break;
                }
            }
        }
        if (apply == null) {
            throw new NoSuchMethodException(function.getClass().getName() + ".apply");
        }

        apply.setAccessible(true);
        try {
            return apply.invoke(function, value);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw cause;
            }
            throw e;
        }
    }

    private static Object extractNbtPrimitiveValueCompat(Object tag) {
        if (tag == null) {
            return null;
        }

        String className = tag.getClass().getName();
        try {
            if (className.endsWith("NBTTagInt") || className.endsWith("IntTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, int.class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagByte") || className.endsWith("ByteTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, byte.class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagShort") || className.endsWith("ShortTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, short.class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagLong") || className.endsWith("LongTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, long.class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagFloat") || className.endsWith("FloatTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, float.class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagDouble") || className.endsWith("DoubleTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, double.class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagString") || className.endsWith("StringTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, String.class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagByteArray") || className.endsWith("ByteArrayTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, byte[].class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagIntArray") || className.endsWith("IntArrayTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, int[].class);
                if (value != null) {
                    return value;
                }
            } else if (className.endsWith("NBTTagLongArray") || className.endsWith("LongArrayTag")) {
                Object value = extractPrimitiveByFieldOrMethod(tag, long[].class);
                if (value != null) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
        }

        // Generic fallback: first non-static primitive / array / string value from tag internals.
        for (Field field : tag.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Class<?> type = field.getType();
            if (type.isPrimitive() || type == String.class || type.isArray()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(tag);
                    if (value != null) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Object extractPrimitiveByFieldOrMethod(Object tag, Class<?> expectedType) {
        for (Field field : tag.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() != expectedType) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.get(tag);
            } catch (Throwable ignored) {
            }
        }
        for (Method method : tag.getClass().getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterCount() != 0 || method.getReturnType() != expectedType) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(tag);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findSendPacketLikeMethod(Class<?> owner, Class<?>[] params) {
        Class<?> packetType = params.length > 0 ? params[0] : null;
        Class<?> listenerType = params.length > 1 ? params[1] : null;

        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] methodParams = method.getParameterTypes();
            if (methodParams.length == 2
                    && packetType != null
                    && listenerType != null
                    && methodParams[0].isAssignableFrom(packetType)
                    && methodParams[1].isAssignableFrom(listenerType)) {
                return method;
            }
            if (methodParams.length == 1
                    && packetType != null
                    && methodParams[0].isAssignableFrom(packetType)) {
                return method;
            }
            if (methodParams.length == 3
                    && packetType != null
                    && listenerType != null
                    && methodParams[0].isAssignableFrom(packetType)
                    && methodParams[1].isAssignableFrom(listenerType)
                    && (methodParams[2] == boolean.class || methodParams[2] == Boolean.class)) {
                return method;
            }
        }
        return null;
    }

    private static Method findCompatibleDeclaredMethod(Class<?> owner, String name, Class<?>[] params) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            Class<?>[] methodParams = method.getParameterTypes();
            if (params == null && methodParams.length == 0) {
                return method;
            }
            if (params == null || methodParams.length != params.length) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < methodParams.length; i++) {
                Class<?> expected = params[i];
                if (expected == null) {
                    continue;
                }
                if (!methodParams[i].isAssignableFrom(expected)) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        return null;
    }

    private static void fixCustomFishingTagBaseGetValue(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"getValue".equals(methodNode.name) || !"(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodNode.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEINTERFACE) {
                    continue;
                }
                if (!"net/momirealms/customfishing/libraries/rtag/util/ThrowableFunction".equals(methodInsnNode.owner)
                        || !"apply".equals(methodInsnNode.name)
                        || !"(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodInsnNode.desc)) {
                    continue;
                }

                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "applyThrowableFunctionCompat";
                methodInsnNode.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                methodInsnNode.itf = false;
            }
        }
    }

    private static void fixCustomFishingDurabilityOptionalGet(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            boolean targetMethod = ("damage".equals(methodNode.name) && "(I)V".equals(methodNode.desc))
                    || ("damage".equals(methodNode.name) && "()I".equals(methodNode.desc))
                    || ("maxDamage".equals(methodNode.name) && "()I".equals(methodNode.desc));
            if (!targetMethod) {
                continue;
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                    continue;
                }
                if (!"java/util/Optional".equals(methodInsnNode.owner)
                        || !"get".equals(methodInsnNode.name)
                        || !"()Ljava/lang/Object;".equals(methodInsnNode.desc)) {
                    continue;
                }

                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "optionalGetOrZeroCompat";
                methodInsnNode.desc = "(Ljava/util/Optional;)Ljava/lang/Object;";
                methodInsnNode.itf = false;
            }
        }
    }

    private static void fixMythicCrucibleSkillEventListeners(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            boolean disableProtocolLibInit = "initProtocolLibListeners".equals(methodNode.name) && "()V".equals(methodNode.desc);
            boolean disableProtocolLibDropInit = "initProtocolLibDropListeners".equals(methodNode.name) && "()V".equals(methodNode.desc);
            boolean disableBrokenMythicTrigger = "onMythicTrigger".equals(methodNode.name) && "(Lio/lumine/mythic/bukkit/events/MythicTriggerEvent;)V".equals(methodNode.desc);

            if (!disableProtocolLibInit && !disableProtocolLibDropInit && !disableBrokenMythicTrigger) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new InsnNode(Opcodes.RETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
        }
    }

    private static void fixMythicCrucibleInventoryViewInvoke(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (methodInsnNode.getOpcode() != Opcodes.INVOKEINTERFACE) {
                    continue;
                }
                if (!"org/bukkit/inventory/InventoryView".equals(methodInsnNode.owner)) {
                    continue;
                }
                methodInsnNode.setOpcode(Opcodes.INVOKEVIRTUAL);
                methodInsnNode.itf = false;
            }
        }
    }

    private static void fixCustomNameplatesServerChannelHandler(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"channelRead".equals(methodNode.name) || !"(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "fireChannelReadCompat",
                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.RETURN));

            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixEcoEventManagerUnregisterListener(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"unregisterListener".equals(methodNode.name) || !"(Lorg/bukkit/event/Listener;)V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "unregisterHandlerListListenerCompat",
                    "(Ljava/lang/Object;)V",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.RETURN));

            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixEcoPacketSetSlotAsync(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"onSend".equals(methodNode.name)
                    || !"(Lcom/willfp/eco/core/packet/PacketEvent;)V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new InsnNode(Opcodes.RETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixEcoPrerequisite(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"()Ljava/lang/Boolean;".equals(methodNode.desc)) {
                continue;
            }

            boolean probesPaperClass = false;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode ldcInsnNode) || !(ldcInsnNode.cst instanceof String text)) {
                    continue;
                }
                if (text.contains("com.destroystokyo.paper.event.block.BeaconEffectEvent")) {
                    probesPaperClass = true;
                    break;
                }
            }
            if (!probesPaperClass && !"lambda$static$0".equals(methodNode.name)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new InsnNode(Opcodes.ICONST_1));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Boolean",
                    "valueOf",
                    "(Z)Ljava/lang/Boolean;",
                    false
            ));
            toInject.add(new InsnNode(ARETURN));

            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixLibreforgeElytraBoostSaveChance(ClassNode node) {
        for (java.util.Iterator<MethodNode> iterator = node.methods.iterator(); iterator.hasNext(); ) {
            MethodNode methodNode = iterator.next();
            if (methodNode.desc == null) {
                continue;
            }
            if (methodNode.desc.contains("com/oneworldstudiomc/paper/event/player/PlayerElytraBoostEvent")
                    || methodNode.desc.contains("com/destroystokyo/paper/event/player/PlayerElytraBoostEvent")) {
                iterator.remove();
            }
        }
    }

    private static void fixLibreforgePaperOnlyListeners(ClassNode node) {
        for (java.util.Iterator<MethodNode> iterator = node.methods.iterator(); iterator.hasNext(); ) {
            MethodNode methodNode = iterator.next();
            if (methodNode.desc == null) {
                continue;
            }
            if (methodNode.desc.contains("/paper/event/")) {
                iterator.remove();
            }
        }
    }

    private static void fixLampCommodoreProvider(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"checkSupported".equals(methodNode.name)
                    || !"()Ljava/util/function/Function;".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new InsnNode(Opcodes.ACONST_NULL));
            toInject.add(new InsnNode(ARETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixZapperDependencyManager(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("<init>".equals(methodNode.name)
                    && "(Lorg/bukkit/plugin/PluginDescriptionFile;Ljava/io/File;Lrevxrsal/zapper/classloader/URLClassLoaderWrapper;)V".equals(methodNode.desc)) {
                InsnList toInject = new InsnList();
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/lang/Object",
                        "<init>",
                        "()V",
                        false
                ));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
                toInject.add(new InsnNode(Opcodes.DUP));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/util/ArrayList",
                        "<init>",
                        "()V",
                        false
                ));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "dependencies",
                        "Ljava/util/List;"
                ));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/util/LinkedHashSet"));
                toInject.add(new InsnNode(Opcodes.DUP));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/util/LinkedHashSet",
                        "<init>",
                        "()V",
                        false
                ));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "repositories",
                        "Ljava/util/Set;"
                ));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
                toInject.add(new InsnNode(Opcodes.DUP));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/util/ArrayList",
                        "<init>",
                        "()V",
                        false
                ));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "relocations",
                        "Ljava/util/List;"
                ));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "pdf",
                        "Lorg/bukkit/plugin/PluginDescriptionFile;"
                ));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "directory",
                        "Ljava/io/File;"
                ));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 3));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "loaderWrapper",
                        "Lrevxrsal/zapper/classloader/URLClassLoaderWrapper;"
                ));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.GETFIELD,
                        node.name,
                        "repositories",
                        "Ljava/util/Set;"
                ));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "revxrsal/zapper/repository/Repository",
                        "mavenCentral",
                        "()Lrevxrsal/zapper/repository/Repository;",
                        true
                ));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKEINTERFACE,
                        "java/util/Set",
                        "add",
                        "(Ljava/lang/Object;)Z",
                        true
                ));
                toInject.add(new InsnNode(Opcodes.POP));

                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "helper",
                        "Lrevxrsal/zapper/TransitiveDependencyHelper;"
                ));

                toInject.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if (!"load".equals(methodNode.name) || !"()V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "loadZapperDependenciesCompat",
                    "(Ljava/lang/Object;)V",
                    false
            ));
            toInject.add(new InsnNode(Opcodes.RETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixAxiomPaper(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("hasPermission".equals(methodNode.name)
                    && "(Lorg/bukkit/entity/Player;Lcom/moulberry/axiom/restrictions/AxiomPermission;)Z".equals(methodNode.desc)) {
                InsnList toInject = new InsnList();
                toInject.add(new InsnNode(Opcodes.ICONST_1));
                toInject.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                continue;
            }

            if ("canModifyWorld".equals(methodNode.name) && "(Lorg/bukkit/entity/Player;Lorg/bukkit/World;)Z".equals(methodNode.desc)) {
                InsnList toInject = new InsnList();
                toInject.add(new InsnNode(Opcodes.ICONST_1));
                toInject.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                continue;
            }

            if ("canUseAxiom".equals(methodNode.name) && "(Lorg/bukkit/entity/Player;)Z".equals(methodNode.desc)) {
                InsnList toInject = new InsnList();
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.GETSTATIC,
                        "com/moulberry/axiom/restrictions/AxiomPermission",
                        "USE",
                        "Lcom/moulberry/axiom/restrictions/AxiomPermission;"
                ));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "com/moulberry/axiom/AxiomPaper",
                        "hasPermission",
                        "(Lorg/bukkit/entity/Player;Lcom/moulberry/axiom/restrictions/AxiomPermission;)Z",
                        false
                ));
                toInject.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                continue;
            }

            if ("canUseAxiom".equals(methodNode.name) && "(Lorg/bukkit/entity/Player;Lcom/moulberry/axiom/restrictions/AxiomPermission;)Z".equals(methodNode.desc)) {
                InsnList toInject = new InsnList();
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "com/moulberry/axiom/AxiomPaper",
                        "hasPermission",
                        "(Lorg/bukkit/entity/Player;Lcom/moulberry/axiom/restrictions/AxiomPermission;)Z",
                        false
                ));
                toInject.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
            }
        }
    }

    private static void fixMythicMobsAIHandler(ClassNode node) {
        // 1.20.1 Mob/GoalSelector internals differ on Forge mappings.
        helloWorld(node, "bS", "f_21344_");
        helloWorld(node, "bT", "f_21345_");
        helloWorld(node, "bU", "f_21346_");
        helloWorld(node, "b", "f_25344_");
        helloWorld(node, "c", "f_25345_");
    }

    private static void fixMythicCrucibleItemManager(ClassNode node) {
        for (java.util.Iterator<MethodNode> iterator = node.methods.iterator(); iterator.hasNext(); ) {
            MethodNode methodNode = iterator.next();
            if (methodNode.desc != null && methodNode.desc.contains("org/bukkit/event/block/CrafterCraftEvent")) {
                iterator.remove();
            }
        }
    }

    private static void fixMythicCrucibleVanillaInventoryMapping(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"registerAll".equals(methodNode.name) || !methodNode.desc.endsWith(")V")) {
                continue;
            }
            InsnList toInject = new InsnList();
            toInject.add(new InsnNode(Opcodes.RETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void fixFawePaperweightBlockMaterial(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"<init>".equals(methodNode.name)) {
                continue;
            }
            if (!"(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/block/state/IBlockData;)V".equals(methodNode.desc)) {
                continue;
            }

            InsnList toInject = new InsnList();
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));

            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "block",
                    "Lnet/minecraft/world/level/block/Block;"
            ));

            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "blockState",
                    "Lnet/minecraft/world/level/block/state/IBlockData;"
            ));

            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "org/bukkit/craftbukkit/v1_20_R1/block/data/CraftBlockData",
                    "fromData",
                    "(Lnet/minecraft/world/level/block/state/IBlockData;)Lorg/bukkit/craftbukkit/v1_20_R1/block/data/CraftBlockData;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "craftBlockData",
                    "Lorg/bukkit/craftbukkit/v1_20_R1/block/data/CraftBlockData;"
            ));

            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.GETFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "craftBlockData",
                    "Lorg/bukkit/craftbukkit/v1_20_R1/block/data/CraftBlockData;"
            ));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "org/bukkit/craftbukkit/v1_20_R1/block/data/CraftBlockData",
                    "getMaterial",
                    "()Lorg/bukkit/Material;",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "craftMaterial",
                    "Lorg/bukkit/Material;"
            ));

            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.GETFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "craftMaterial",
                    "Lorg/bukkit/Material;"
            ));
            toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "org/bukkit/Material", "isOccluding", "()Z", false));
            toInject.add(new InsnNode(Opcodes.ICONST_1));
            toInject.add(new InsnNode(Opcodes.IXOR));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "isTranslucent",
                    "Z"
            ));

            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.GETSTATIC,
                    "net/minecraft/world/level/BlockAccessAir",
                    "a",
                    "Lnet/minecraft/world/level/BlockAccessAir;"
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.GETSTATIC,
                    "net/minecraft/core/BlockPosition",
                    "b",
                    "Lnet/minecraft/core/BlockPosition;"
            ));
            toInject.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/level/block/state/IBlockData",
                    "b",
                    "(Lnet/minecraft/world/level/IBlockAccess;Lnet/minecraft/core/BlockPosition;)I",
                    false
            ));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "opacity",
                    "I"
            ));

            toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new InsnNode(Opcodes.ACONST_NULL));
            toInject.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_20_R1/PaperweightBlockMaterial",
                    "tile",
                    "Lcom/sk89q/jnbt/CompoundTag;"
            ));

            toInject.add(new InsnNode(Opcodes.RETURN));

            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
        }
    }

    private static void fixFawePaperweightPlatformAdapter(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if ("addTicket".equals(methodNode.name)) {
                // Paper-only ticket types (e.g. UNLOAD_COOLDOWN) are absent on Forge.
                // Keep chunk loading logic stable by skipping this optional ticket call.
                InsnList noop = new InsnList();
                noop.add(new InsnNode(Opcodes.RETURN));
                methodNode.instructions = noop;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            if ("nearbyPlayers".equals(methodNode.name)) {
                InsnList toInject = new InsnList();
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                toInject.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                toInject.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PluginFixManager.class),
                        "nearbyPlayersCompat",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
                        false
                ));
                toInject.add(new InsnNode(Opcodes.ARETURN));
                methodNode.instructions = toInject;
                methodNode.tryCatchBlocks.clear();
                clearLocalDebugInfo(methodNode);
                continue;
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsnNode
                        && fieldInsnNode.getOpcode() == Opcodes.GETSTATIC
                        && "MAIN_EXECUTOR".equals(fieldInsnNode.name)
                        && "Ljava/util/concurrent/Executor;".equals(fieldInsnNode.desc)
                        && ("io/papermc/paper/util/MCUtil".equals(fieldInsnNode.owner)
                        || "com/mohistmc/paper/util/MCUtil".equals(fieldInsnNode.owner)
                        || "com/oneworldstudiomc/paper/util/MCUtil".equals(fieldInsnNode.owner))) {
                    fieldInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    fieldInsnNode.name = "MAIN_EXECUTOR_COMPAT";
                }
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }
                if (!"getChunkAtIfCachedImmediately".equals(methodInsnNode.name)
                        && !"getChunkAtIfLoadedImmediately".equals(methodInsnNode.name)) {
                    continue;
                }
                if (!"(II)Lnet/minecraft/world/level/chunk/LevelChunk;".equals(methodInsnNode.desc)
                        && !"(II)Lnet/minecraft/world/level/chunk/Chunk;".equals(methodInsnNode.desc)) {
                    continue;
                }
                if (!"net/minecraft/server/level/ServerChunkCache".equals(methodInsnNode.owner)
                        && !"net/minecraft/server/level/ChunkProviderServer".equals(methodInsnNode.owner)) {
                    continue;
                }

                methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                methodInsnNode.name = "getChunkAtIfCachedImmediatelyCompat";
                methodInsnNode.desc = "(Ljava/lang/Object;II)Lnet/minecraft/world/level/chunk/LevelChunk;";
                methodInsnNode.itf = false;
            }
        }
        clearClassLocalDebugInfo(node);
    }

    private static void fixFawePaperweightGetBlocks(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsnNode
                        && fieldInsnNode.getOpcode() == Opcodes.GETFIELD
                        && "tickingList".equals(fieldInsnNode.name)
                        && ("net/minecraft/world/level/chunk/ChunkSection".equals(fieldInsnNode.owner)
                        || "net/minecraft/world/level/chunk/LevelChunkSection".equals(fieldInsnNode.owner))) {
                    // Replace field read with null to avoid linking to absent Paper field on Forge.
                    InsnList replacement = new InsnList();
                    replacement.add(new InsnNode(Opcodes.POP));
                    replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                    methodNode.instructions.insertBefore(fieldInsnNode, replacement);
                    methodNode.instructions.remove(fieldInsnNode);
                    insn = next;
                    continue;
                }

                if (insn instanceof MethodInsnNode methodInsnNode
                        && "clear".equals(methodInsnNode.name)
                        && "()V".equals(methodInsnNode.desc)
                        && ("com/destroystokyo/paper/util/maplist/IBlockDataList".equals(methodInsnNode.owner)
                        || "com/oneworldstudiomc/paper/util/maplist/IBlockDataList".equals(methodInsnNode.owner))) {
                    // Drop call receiver and skip clear() entirely.
                    methodNode.instructions.set(methodInsnNode, new InsnNode(Opcodes.POP));
                }
                insn = next;
            }
        }
    }

    private static void fixFawePaperweightPropertyCacheLoader(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"load".equals(methodNode.name)
                    || !"(Lnet/minecraft/world/level/block/state/properties/IBlockState;)Lcom/sk89q/worldedit/registry/state/Property;".equals(methodNode.desc)) {
                continue;
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn.getOpcode() == Opcodes.ATHROW) {
                    InsnList fallback = new InsnList();
                    fallback.add(new InsnNode(Opcodes.POP));
                    fallback.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    fallback.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    fallback.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(PluginFixManager.class),
                            "createWorldEditFallbackPropertyCompat",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            false
                    ));
                    fallback.add(new org.objectweb.asm.tree.TypeInsnNode(
                            Opcodes.CHECKCAST,
                            "com/sk89q/worldedit/registry/state/Property"
                    ));
                    fallback.add(new InsnNode(Opcodes.ARETURN));
                    methodNode.instructions.insertBefore(insn, fallback);
                    methodNode.instructions.remove(insn);
                }
                insn = next;
            }
        }
        clearClassLocalDebugInfo(node);
    }

    private static void fixWorldEditBlockTypesCache(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"generateStateOrdinals".equals(methodNode.name)
                    || !"(IIILjava/util/List;)[I".equals(methodNode.desc)) {
                continue;
            }

            InsnList replacement = new InsnList();
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 0));
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 1));
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 2));
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 3));
            replacement.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETSTATIC, node.name, "BIT_OFFSET", "I"));
            replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "generateWorldEditStateOrdinalsCompat",
                    "(IIILjava/lang/Object;I)[I",
                    false
            ));
            replacement.add(new InsnNode(Opcodes.ARETURN));
            methodNode.instructions = replacement;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }
        clearClassLocalDebugInfo(node);
    }

    public static int[] generateWorldEditStateOrdinalsCompat(
            int internalId,
            int startOrdinal,
            int stateCount,
            Object propertiesObj,
            int bitOffset
    ) {
        if (!(propertiesObj instanceof java.util.List<?> properties) || properties.isEmpty()) {
            return null;
        }

        int[] ordinals = new int[stateCount];
        java.util.Arrays.fill(ordinals, -1);
        int propertyCount = properties.size();
        int[] valueCounts = new int[propertyCount];
        int[] valueIndexes = new int[propertyCount];
        Object[] propertyArray = new Object[propertyCount];
        Method[] modifyIndexMethods = new Method[propertyCount];

        for (int i = 0; i < propertyCount; i++) {
            Object property = properties.get(i);
            if (property == null) {
                return ordinals;
            }

            propertyArray[i] = property;
            modifyIndexMethods[i] = findMethodCompat(property.getClass(), "modifyIndex", int.class, int.class);
            if (modifyIndexMethods[i] == null) {
                return ordinals;
            }

            int valueCount = 0;
            Object valuesObj = invokeNoArgs(property, "getValues");
            if (valuesObj instanceof java.util.Collection<?> collection) {
                valueCount = collection.size();
            } else if (valuesObj instanceof java.lang.Iterable<?> iterable) {
                for (Object ignored : iterable) {
                    valueCount++;
                }
            }
            if (valueCount <= 0) {
                return ordinals;
            }
            valueCounts[i] = valueCount;
        }

        int cursor = 0;
        int ordinal = startOrdinal;
        while (true) {
            int stateId = internalId;
            boolean validState = true;

            for (int i = 0; i < propertyCount; i++) {
                try {
                    Object modified = modifyIndexMethods[i].invoke(propertyArray[i], stateId, valueIndexes[i]);
                    if (!(modified instanceof Number number)) {
                        validState = false;
                        break;
                    }
                    stateId = number.intValue();
                } catch (Throwable ignored) {
                    validState = false;
                    break;
                }
            }

            int ordinalIndex = stateId >> bitOffset;
            if (validState && ordinalIndex >= 0 && ordinalIndex < ordinals.length) {
                if (ordinals[ordinalIndex] == -1) {
                    ordinals[ordinalIndex] = ordinal++;
                }
            }

            valueIndexes[cursor]++;
            if (valueIndexes[cursor] == valueCounts[cursor]) {
                valueIndexes[cursor] = 0;
                cursor++;
                if (cursor == valueIndexes.length) {
                    break;
                }
            } else {
                cursor = 0;
            }
        }

        return ordinals;
    }

    private static void fixWorldEditBlockTypesCacheSettings(ClassNode node) {
        String internalIdFieldName = "internalId";
        for (org.objectweb.asm.tree.FieldNode fieldNode : node.fields) {
            if ("I".equals(fieldNode.desc) && "internalId".equals(fieldNode.name)) {
                internalIdFieldName = fieldNode.name;
                break;
            }
        }

        for (MethodNode methodNode : node.methods) {
            if (!"parseProperties".equals(methodNode.name)
                    || !"(Ljava/lang/String;Ljava/util/Map;)I".equals(methodNode.desc)) {
                continue;
            }

            InsnList replacement = new InsnList();
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            replacement.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, node.name, internalIdFieldName, "I"));
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
            replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(PluginFixManager.class),
                    "parseWorldEditStatePropertiesCompat",
                    "(ILjava/lang/String;Ljava/lang/Object;)I",
                    false
            ));
            replacement.add(new InsnNode(Opcodes.IRETURN));
            methodNode.instructions = replacement;
            methodNode.tryCatchBlocks.clear();
            clearLocalDebugInfo(methodNode);
        }

        for (MethodNode methodNode : node.methods) {
            if (!"<init>".equals(methodNode.name)
                    || !"(Lcom/sk89q/worldedit/world/block/BlockType;Ljava/lang/String;ILjava/util/List;)V".equals(methodNode.desc)) {
                continue;
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof MethodInsnNode methodInsnNode
                        && methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "java/util/List".equals(methodInsnNode.owner)
                        && "get".equals(methodInsnNode.name)
                        && "(I)Ljava/lang/Object;".equals(methodInsnNode.desc)) {
                    methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsnNode.owner = Type.getInternalName(PluginFixManager.class);
                    methodInsnNode.name = "getListElementSafeCompat";
                    methodInsnNode.desc = "(Ljava/lang/Object;I)Ljava/lang/Object;";
                    methodInsnNode.itf = false;
                }
                insn = next;
            }
            clearLocalDebugInfo(methodNode);
        }
        clearClassLocalDebugInfo(node);
    }

    public static Object getListElementSafeCompat(Object listObj, int index) {
        if (!(listObj instanceof java.util.List<?> list) || list.isEmpty()) {
            return null;
        }
        if (index < 0 || index >= list.size()) {
            return list.get(0);
        }
        return list.get(index);
    }

    public static int parseWorldEditStatePropertiesCompat(int internalId, String serializedProperties, Object propertiesMapObj) {
        int stateId = internalId;
        if (!(propertiesMapObj instanceof java.util.Map<?, ?> propertiesMap) || serializedProperties == null || serializedProperties.isEmpty()) {
            return stateId;
        }

        String[] assignments = serializedProperties.split(",");
        for (String assignment : assignments) {
            if (assignment == null || assignment.isEmpty()) {
                continue;
            }

            int separatorIndex = assignment.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex >= assignment.length() - 1) {
                continue;
            }

            String propertyName = assignment.substring(0, separatorIndex).trim();
            String propertyValue = assignment.substring(separatorIndex + 1).trim();
            if (propertyName.isEmpty() || propertyValue.isEmpty()) {
                continue;
            }

            Object property = propertiesMap.get(propertyName);
            if (property == null) {
                continue;
            }

            try {
                Method getIndexFor = findMethodCompat(property.getClass(), "getIndexFor", CharSequence.class);
                if (getIndexFor == null) {
                    getIndexFor = findMethodCompat(property.getClass(), "getIndexFor", String.class);
                }
                Method modifyIndex = findMethodCompat(property.getClass(), "modifyIndex", int.class, int.class);
                if (getIndexFor == null || modifyIndex == null) {
                    continue;
                }

                Object indexObj = getIndexFor.invoke(property, propertyValue);
                if (!(indexObj instanceof Number indexNumber)) {
                    continue;
                }
                int propertyIndex = indexNumber.intValue();
                if (propertyIndex < 0) {
                    continue;
                }

                Object modified = modifyIndex.invoke(property, stateId, propertyIndex);
                if (modified instanceof Number number && number.intValue() >= 0) {
                    stateId = number.intValue();
                }
            } catch (Throwable ignored) {
            }
        }

        return stateId >= 0 ? stateId : internalId;
    }

    public static Object createWorldEditFallbackPropertyCompat(Object cacheLoaderObj, Object blockStateObj) {
        if (blockStateObj == null) {
            throw new IllegalArgumentException("Missing block state property");
        }

        try {
            String propertyName = String.valueOf(invokeNoArgs(blockStateObj, "f", "getName"));
            Object valuesObj = invokeNoArgs(blockStateObj, "a", "getPossibleValues");
            java.util.ArrayList<String> values = new java.util.ArrayList<>();

            if (valuesObj instanceof java.lang.Iterable<?> iterable) {
                for (Object value : iterable) {
                    if (value == null) {
                        continue;
                    }
                    Object named = invokeNoArgs(value, "c", "getSerializedName", "getName");
                    String serialized = named != null ? String.valueOf(named) : String.valueOf(value);
                    if (!serialized.isEmpty()) {
                        values.add(serialized);
                    }
                }
            }

            if (values.isEmpty()) {
                values.add(String.valueOf(blockStateObj));
            }

            ClassLoader propertyLoader = null;
            if (cacheLoaderObj != null) {
                propertyLoader = cacheLoaderObj.getClass().getClassLoader();
            }
            if (propertyLoader == null) {
                propertyLoader = Thread.currentThread().getContextClassLoader();
            }
            if (propertyLoader == null) {
                propertyLoader = PluginFixManager.class.getClassLoader();
            }

            Class<?> enumPropertyClass = Class.forName(
                    "com.sk89q.worldedit.registry.state.EnumProperty",
                    false,
                    propertyLoader
            );
            java.lang.reflect.Constructor<?> constructor = enumPropertyClass.getConstructor(String.class, java.util.List.class);
            return constructor.newInstance(propertyName, values);
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to create WorldEdit fallback property", throwable);
        }
    }

    public static void applyMythicDungeonsBetonQuestCompat(Object source) {
        Object mythicPlugin = resolveMythicDungeonsPlugin(source);
        if (!(mythicPlugin instanceof org.bukkit.plugin.Plugin)) {
            return;
        }

        synchronized (REGISTERED_MYTHICDUNGEONS_BQ3_BRIDGE) {
            if (REGISTERED_MYTHICDUNGEONS_BQ3_BRIDGE.contains(mythicPlugin)) {
                return;
            }
        }

        Object betonPlugin = resolvePluginByNameCompat("BetonQuest");
        if (!(betonPlugin instanceof org.bukkit.plugin.Plugin)) {
            return;
        }

        try {
            ClassLoader bqLoader = betonPlugin.getClass().getClassLoader();
            Class.forName("org.betonquest.betonquest.api.service.objective.Objectives", false, bqLoader);

            Object betonApi = invokeNoArgs(betonPlugin, "getBetonQuestApi");
            if (betonApi == null) {
                return;
            }

            Class<?> objectiveFactoryInterface = Class.forName(
                    "org.betonquest.betonquest.api.quest.objective.ObjectiveFactory",
                    true,
                    bqLoader
            );
            Class<?> objectiveInterface = Class.forName(
                    "org.betonquest.betonquest.api.quest.objective.Objective",
                    true,
                    bqLoader
            );
            Class<?> playerQuestFactoryInterface = Class.forName(
                    "org.betonquest.betonquest.api.quest.PlayerQuestFactory",
                    true,
                    bqLoader
            );
            Class<?> playerActionInterface = Class.forName(
                    "org.betonquest.betonquest.api.quest.action.PlayerAction",
                    true,
                    bqLoader
            );

            Object objectivesService = invokeNoArgs(betonApi, "objectives");
            Object objectiveRegistry = invokeNoArgs(objectivesService, "registry");
            Object objectiveFactory = createBq3ObjectiveFactoryProxy(
                    objectiveFactoryInterface,
                    objectiveInterface,
                    mythicPlugin
            );
            registerFactoryCompat(objectiveRegistry, "finishdungeon", objectiveFactoryInterface, objectiveFactory);

            Object actionsService = invokeNoArgs(betonApi, "actions");
            Object actionRegistry = invokeNoArgs(actionsService, "registry");
            Object actionFactory = createBq3ActionFactoryProxy(
                    playerQuestFactoryInterface,
                    playerActionInterface,
                    mythicPlugin
            );
            registerFactoryCompat(actionRegistry, "playdungeon", playerQuestFactoryInterface, actionFactory);

            setMythicDungeonsBetonEnabled(mythicPlugin, true);
            synchronized (REGISTERED_MYTHICDUNGEONS_BQ3_BRIDGE) {
                REGISTERED_MYTHICDUNGEONS_BQ3_BRIDGE.add(mythicPlugin);
            }
            org.bukkit.Bukkit.getLogger().info("[OneWorldCore] Enabled MythicDungeons <-> BetonQuest v3 compatibility bridge.");
        } catch (ClassNotFoundException ignored) {
            // Not BQ3 API, keep original behavior.
        } catch (Throwable throwable) {
            org.bukkit.Bukkit.getLogger().warning(
                    "[OneWorldCore] Failed to enable MythicDungeons BetonQuest v3 bridge: "
                            + throwable.getClass().getSimpleName()
                            + " - "
                            + throwable.getMessage()
            );
        }
    }

    private static Object resolveMythicDungeonsPlugin(Object source) {
        if (source instanceof org.bukkit.plugin.Plugin plugin) {
            return plugin;
        }
        Object plugin = resolvePluginByNameCompat("MythicDungeons");
        if (plugin != null) {
            return plugin;
        }
        return resolvePluginByNameCompat("One World Plugin - Mechanics - MythicDungeons");
    }

    public static org.bukkit.NamespacedKey attributeGetKeyCompat(org.bukkit.attribute.Attribute attribute) {
        return attribute.getKey();
    }

    public static org.bukkit.NamespacedKey materialGetKeyCompat(org.bukkit.Material material) {
        if (material == null) {
            return null;
        }
        if (!material.isLegacy()) {
            return material.getKey();
        }

        org.bukkit.Material modern = org.bukkit.craftbukkit.v1_20_R1.legacy.CraftLegacy.fromLegacy(material);
        if (modern != null && !modern.isLegacy()) {
            return modern.getKey();
        }

        String normalized = material.name();
        if (normalized.startsWith("LEGACY_")) {
            normalized = normalized.substring("LEGACY_".length());
        }

        org.bukkit.Material matched = org.bukkit.Material.matchMaterial(normalized, false);
        if (matched != null && !matched.isLegacy()) {
            return matched.getKey();
        }

        return org.bukkit.NamespacedKey.minecraft(normalized.toLowerCase(Locale.ROOT));
    }

    public static Object worldEditRegistryRegisterCompat(Object registryObj, String key, Object value) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(value, "value");

        if (!key.equals(key.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("key must be lowercase: " + key);
        }

        try {
            Field mapField = findFieldByNameCompat(registryObj.getClass(), "map");
            if (mapField == null) {
                throw new IllegalStateException("WorldEdit registry map field was not found");
            }
            mapField.setAccessible(true);

            Object mapObj = mapField.get(registryObj);
            if (!(mapObj instanceof java.util.Map<?, ?> rawMap)) {
                throw new IllegalStateException("WorldEdit registry map field has unexpected type");
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) rawMap;
            if (map.containsKey(key)) {
                return map.get(key);
            }

            map.put(key, value);
            return value;
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to register WorldEdit key " + key, throwable);
        }
    }

    public static Object essentialsGetServerSupportStatusCompat(Class<?> versionUtilClass) {
        if (versionUtilClass == null) {
            throw new IllegalStateException("Essentials VersionUtil class is unavailable");
        }

        try {
            ClassLoader classLoader = versionUtilClass.getClassLoader();
            Class<?> supportStatusClass = Class.forName(
                    "com.earth2me.essentials.utils.VersionUtil$SupportStatus",
                    false,
                    classLoader
            );

            @SuppressWarnings({"rawtypes", "unchecked"})
            Enum<?> supportStatus = Enum.valueOf((Class<? extends Enum>) supportStatusClass.asSubclass(Enum.class), "FULL");

            Field supportStatusField = findFieldByNameCompat(versionUtilClass, "supportStatus");
            if (supportStatusField != null) {
                supportStatusField.setAccessible(true);
                supportStatusField.set(null, supportStatus);
            }

            Field supportStatusClassField = findFieldByNameCompat(versionUtilClass, "supportStatusClass");
            if (supportStatusClassField != null) {
                supportStatusClassField.setAccessible(true);
                supportStatusClassField.set(null, null);
            }

            return supportStatus;
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to spoof Essentials server support status", throwable);
        }
    }

    public static org.bukkit.block.banner.PatternType patternTypeValueOfCompat(String name) {
        if (name == null) {
            throw new IllegalArgumentException("PatternType name cannot be null");
        }

        try {
            return org.bukkit.block.banner.PatternType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return org.bukkit.block.banner.PatternType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }

        org.bukkit.block.banner.PatternType patternType =
                org.bukkit.block.banner.PatternType.getByIdentifier(name.toLowerCase(Locale.ROOT));
        if (patternType != null) {
            return patternType;
        }

        throw new IllegalArgumentException("No enum constant org.bukkit.block.banner.PatternType." + name);
    }

    public static String normalizeMythicParticleNameCompat(String name) {
        if (name == null) {
            return null;
        }

        return switch (name.toUpperCase(Locale.ROOT)) {
            case "FIRE" -> "FLAME";
            case "SNOW_SHOVEL" -> "ITEM_SNOWBALL";
            case "LARGE_GUST" -> "GUST_EMITTER_LARGE";
            case "GUST_SMALL" -> "SMALL_GUST";
            default -> name;
        };
    }

    public static boolean mythicIsPaperCompat() {
        try {
            Class.forName("net.minecraftforge.common.MinecraftForge", false, PluginFixManager.class.getClassLoader());
            return false;
        } catch (Throwable ignored) {
        }

        try {
            String serverName = org.bukkit.Bukkit.getName();
            if (serverName != null) {
                return serverName.toLowerCase(Locale.ROOT).contains("paper");
            }
        } catch (Throwable ignored) {
        }

        try {
            Class.forName("io.papermc.paper.configuration.Configuration", false, PluginFixManager.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static org.bukkit.inventory.ItemStack playerGetActiveItemCompat(org.bukkit.entity.Player player) {
        if (player == null) {
            return null;
        }
        try {
            return player.getItemInUse();
        } catch (Throwable ignored) {
        }
        try {
            Method method = findMethodCompat(player.getClass(), "getActiveItem");
            if (method != null) {
                Object result = method.invoke(player);
                if (result instanceof org.bukkit.inventory.ItemStack itemStack) {
                    return itemStack;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return player.getInventory().getItemInMainHand();
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Object itemStackGetDataOrDefaultCompat(org.bukkit.inventory.ItemStack itemStack, Object componentType, Object defaultValue) {
        if (itemStack == null || componentType == null) {
            return defaultValue;
        }
        if (matchesDataComponentCompat(componentType, "DAMAGE")) {
            return Integer.valueOf(itemStackDamageCompat(itemStack));
        }
        if (matchesDataComponentCompat(componentType, "MAX_DAMAGE")) {
            return Integer.valueOf(itemStackMaxDamageCompat(itemStack));
        }
        return defaultValue;
    }

    public static void armorStandSetCanTickCompat(org.bukkit.entity.ArmorStand armorStand, boolean canTick) {
        if (armorStand == null) {
            return;
        }
        if (invokeBooleanArgumentMethodCompat(armorStand, canTick, "setCanTick")) {
            return;
        }
        Object handle = invokeNoArgs(armorStand, "getHandle");
        invokeBooleanArgumentMethodCompat(handle, canTick, "canUpdate", "setCanTick");
    }

    public static void armorStandSetCanMoveCompat(org.bukkit.entity.ArmorStand armorStand, boolean canMove) {
        invokeBooleanArgumentMethodCompat(armorStand, canMove, "setCanMove");
    }

    public static void armorStandSetDisabledSlotsCompat(org.bukkit.entity.ArmorStand armorStand, org.bukkit.inventory.EquipmentSlot[] disabledSlots) {
        invokeSingleArgumentMethodCompat(armorStand, org.bukkit.inventory.EquipmentSlot[].class, disabledSlots, "setDisabledSlots");
    }

    public static void itemMetaSetPlaceableKeysCompat(org.bukkit.inventory.meta.ItemMeta itemMeta, java.util.Collection<?> placeableKeys) {
        invokeSingleArgumentMethodCompat(itemMeta, java.util.Collection.class, placeableKeys, "setPlaceableKeys");
    }

    public static void itemMetaSetDestroyableKeysCompat(org.bukkit.inventory.meta.ItemMeta itemMeta, java.util.Collection<?> destroyableKeys) {
        invokeSingleArgumentMethodCompat(itemMeta, java.util.Collection.class, destroyableKeys, "setDestroyableKeys");
    }

    private static boolean matchesDataComponentCompat(Object componentType, String fieldName) {
        return sameDataComponentCompat(componentType, "com.oneworldstudiomc.paper.datacomponent.DataComponentTypes", fieldName)
                || sameDataComponentCompat(componentType, "io.papermc.paper.datacomponent.DataComponentTypes", fieldName);
    }

    private static boolean sameDataComponentCompat(Object componentType, String ownerName, String fieldName) {
        if (componentType == null || ownerName == null || fieldName == null) {
            return false;
        }
        try {
            Class<?> owner = Class.forName(ownerName, false, componentType.getClass().getClassLoader());
            Field field = owner.getField(fieldName);
            return field.get(null) == componentType;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> owner = Class.forName(ownerName, false, PluginFixManager.class.getClassLoader());
            Field field = owner.getField(fieldName);
            return field.get(null) == componentType;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int itemStackDamageCompat(org.bukkit.inventory.ItemStack itemStack) {
        try {
            org.bukkit.inventory.meta.ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                return Math.max(damageable.getDamage(), 0);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static int itemStackMaxDamageCompat(org.bukkit.inventory.ItemStack itemStack) {
        try {
            org.bukkit.inventory.meta.ItemMeta itemMeta = itemStack.getItemMeta();
            Method hasMaxDamage = findMethodCompat(itemMeta.getClass(), "hasMaxDamage");
            Method getMaxDamage = findMethodCompat(itemMeta.getClass(), "getMaxDamage");
            if (hasMaxDamage != null && getMaxDamage != null) {
                Object hasValue = hasMaxDamage.invoke(itemMeta);
                if (Boolean.TRUE.equals(hasValue)) {
                    Object maxValue = getMaxDamage.invoke(itemMeta);
                    if (maxValue instanceof Number number) {
                        return Math.max(number.intValue(), 0);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return Math.max(itemStack.getType().getMaxDurability(), 0);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static boolean invokeBooleanArgumentMethodCompat(Object owner, boolean value, String... names) {
        return invokeSingleArgumentMethodCompat(owner, boolean.class, value, names);
    }

    private static boolean invokeSingleArgumentMethodCompat(Object owner, Class<?> parameterType, Object value, String... names) {
        if (owner == null || parameterType == null || names == null) {
            return false;
        }
        for (String name : names) {
            try {
                Method method = findMethodCompat(owner.getClass(), name, parameterType);
                if (method == null) {
                    continue;
                }
                method.invoke(owner, value);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Object resolvePluginByNameCompat(String name) {
        try {
            return org.bukkit.Bukkit.getPluginManager().getPlugin(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object createBq3ObjectiveFactoryProxy(Class<?> objectiveFactoryInterface, Class<?> objectiveInterface, Object mythicPlugin) {
        return java.lang.reflect.Proxy.newProxyInstance(
                objectiveFactoryInterface.getClassLoader(),
                new Class[]{objectiveFactoryInterface},
                (proxy, method, args) -> {
                    if ("parseInstruction".equals(method.getName()) && args != null && args.length >= 2) {
                        return createBq3ObjectiveProxy(objectiveInterface, mythicPlugin, args[0], args[1]);
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );
    }

    private static Object createBq3ObjectiveProxy(Class<?> objectiveInterface, Object mythicPlugin, Object instruction, Object objectiveService) throws Throwable {
        final String dungeonName = parseInstructionValueCompat(instruction);
        final org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
        };
        registerFinishDungeonListenerCompat(listener, mythicPlugin, dungeonName, objectiveService);

        return java.lang.reflect.Proxy.newProxyInstance(
                objectiveInterface.getClassLoader(),
                new Class[]{objectiveInterface},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getService".equals(name)) {
                        return objectiveService;
                    }
                    if ("close".equals(name)) {
                        org.bukkit.event.HandlerList.unregisterAll(listener);
                        return null;
                    }
                    if ("toString".equals(name)) {
                        return "OneWorldCoreFinishDungeonObjective[" + dungeonName + "]";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name) && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static void registerFinishDungeonListenerCompat(org.bukkit.event.Listener listener, Object mythicPlugin, String dungeonName, Object objectiveService) {
        try {
            if (!(mythicPlugin instanceof org.bukkit.plugin.Plugin bukkitPlugin)) {
                return;
            }
            Class<?> rawEventClass = Class.forName(
                    "net.playavalon.mythicdungeons.api.events.dungeon.PlayerFinishDungeonEvent",
                    true,
                    mythicPlugin.getClass().getClassLoader()
            );
            if (!org.bukkit.event.Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            Class<? extends org.bukkit.event.Event> eventClass = (Class<? extends org.bukkit.event.Event>) rawEventClass;
            org.bukkit.Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    org.bukkit.event.EventPriority.MONITOR,
                    (ignoredListener, event) -> {
                        if (!isMatchingDungeonFinishEvent(event, dungeonName)) {
                            return;
                        }
                        Object playerObj = invokeNoArgs(event, "getPlayer");
                        if (!(playerObj instanceof org.bukkit.entity.Player player)) {
                            return;
                        }
                        Object profile = resolveProfileForPlayerCompat(objectiveService, player);
                        if (profile == null) {
                            return;
                        }
                        completeObjectiveCompat(objectiveService, profile);
                    },
                    bukkitPlugin,
                    true
            );
        } catch (Throwable ignored) {
        }
    }

    private static boolean isMatchingDungeonFinishEvent(Object event, String dungeonName) {
        try {
            Object dungeon = invokeNoArgs(event, "getDungeon");
            Object worldName = invokeNoArgs(dungeon, "getWorldName", "getName");
            if (!(worldName instanceof String actual)) {
                return false;
            }
            return actual.equalsIgnoreCase(dungeonName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object resolveProfileForPlayerCompat(Object objectiveService, org.bukkit.entity.Player player) {
        try {
            Object provider = invokeNoArgs(objectiveService, "getProfileProvider");
            if (provider == null) {
                return null;
            }

            Method getByPlayer = findCompatibleMethod(provider.getClass(), "getProfile", org.bukkit.entity.Player.class);
            if (getByPlayer != null) {
                getByPlayer.setAccessible(true);
                return getByPlayer.invoke(provider, player);
            }

            Method getByOffline = findCompatibleMethod(provider.getClass(), "getProfile", org.bukkit.OfflinePlayer.class);
            if (getByOffline != null) {
                getByOffline.setAccessible(true);
                return getByOffline.invoke(provider, player);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void completeObjectiveCompat(Object objectiveService, Object profile) {
        if (objectiveService == null || profile == null) {
            return;
        }
        try {
            Method complete = findCompatibleMethod(objectiveService.getClass(), "complete", profile.getClass());
            if (complete != null) {
                complete.setAccessible(true);
                complete.invoke(objectiveService, profile);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object createBq3ActionFactoryProxy(Class<?> playerQuestFactoryInterface, Class<?> playerActionInterface, Object mythicPlugin) {
        return java.lang.reflect.Proxy.newProxyInstance(
                playerQuestFactoryInterface.getClassLoader(),
                new Class[]{playerQuestFactoryInterface},
                (proxy, method, args) -> {
                    if ("parsePlayer".equals(method.getName()) && args != null && args.length == 1) {
                        return createBq3PlayerActionProxy(playerActionInterface, mythicPlugin, args[0]);
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );
    }

    private static Object createBq3PlayerActionProxy(Class<?> playerActionInterface, Object mythicPlugin, Object instruction) throws Throwable {
        final String dungeonName = parseInstructionValueCompat(instruction);
        return java.lang.reflect.Proxy.newProxyInstance(
                playerActionInterface.getClassLoader(),
                new Class[]{playerActionInterface},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("execute".equals(name) && args != null && args.length == 1) {
                        Object profile = args[0];
                        Object offline = invokeNoArgs(profile, "getPlayer");
                        org.bukkit.entity.Player player = null;
                        if (offline instanceof org.bukkit.entity.Player directPlayer) {
                            player = directPlayer;
                        } else if (offline instanceof org.bukkit.OfflinePlayer offlinePlayer) {
                            player = offlinePlayer.getPlayer();
                        }
                        if (player != null) {
                            invokeMythicSendToDungeonCompat(mythicPlugin, player, dungeonName);
                        }
                        return null;
                    }
                    if ("toString".equals(name)) {
                        return "OneWorldCorePlayDungeonAction[" + dungeonName + "]";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name) && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );
    }

    private static void invokeMythicSendToDungeonCompat(Object mythicPlugin, org.bukkit.entity.Player player, String dungeonName) {
        if (mythicPlugin == null || player == null || dungeonName == null || dungeonName.isEmpty()) {
            return;
        }
        try {
            for (Method method : mythicPlugin.getClass().getMethods()) {
                if (!"sendToDungeon".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?> first = method.getParameterTypes()[0];
                Class<?> second = method.getParameterTypes()[1];
                if (!first.isAssignableFrom(player.getClass()) || second != String.class) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(mythicPlugin, player, dungeonName);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void registerFactoryCompat(Object registry, String id, Class<?> factoryInterface, Object factoryInstance) throws ReflectiveOperationException {
        if (registry == null || id == null || factoryInterface == null || factoryInstance == null) {
            return;
        }
        for (Method method : registry.getClass().getMethods()) {
            if (!"register".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0] != String.class) {
                continue;
            }
            if (!params[1].isAssignableFrom(factoryInterface)) {
                continue;
            }
            method.setAccessible(true);
            method.invoke(registry, id, factoryInstance);
            return;
        }
        throw new NoSuchMethodException("register(String, " + factoryInterface.getName() + ")");
    }

    private static String parseInstructionValueCompat(Object instruction) {
        if (instruction == null) {
            return "";
        }
        try {
            Method nextElement = findMethodByNameAndArity(instruction.getClass(), "nextElement", 0);
            if (nextElement != null) {
                nextElement.setAccessible(true);
                Object value = nextElement.invoke(instruction);
                return value == null ? "" : String.valueOf(value);
            }
        } catch (Throwable ignored) {
        }
        try {
            Method next = findMethodByNameAndArity(instruction.getClass(), "next", 0);
            if (next != null) {
                next.setAccessible(true);
                Object value = next.invoke(instruction);
                return value == null ? "" : String.valueOf(value);
            }
        } catch (Throwable ignored) {
        }
        try {
            Method getParts = findMethodByNameAndArity(instruction.getClass(), "getParts", 0);
            if (getParts != null) {
                getParts.setAccessible(true);
                Object parts = getParts.invoke(instruction);
                if (parts instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
                    return String.valueOf(list.get(0));
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static Method findMethodByNameAndArity(Class<?> type, String methodName, int arity) {
        for (Method method : type.getMethods()) {
            if (methodName.equals(method.getName()) && method.getParameterCount() == arity) {
                return method;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (methodName.equals(method.getName()) && method.getParameterCount() == arity) {
                return method;
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?> argType) {
        for (Method method : type.getMethods()) {
            if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(argType)) {
                return method;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(argType)) {
                return method;
            }
        }
        return null;
    }

    private static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0f;
        }
        if (returnType == Double.TYPE) {
            return 0d;
        }
        return null;
    }

    private static void setMythicDungeonsBetonEnabled(Object mythicPlugin, boolean enabled) {
        if (mythicPlugin == null) {
            return;
        }
        try {
            Method method = mythicPlugin.getClass().getMethod("setBetonEnabled", boolean.class);
            method.setAccessible(true);
            method.invoke(mythicPlugin, enabled);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Field field = mythicPlugin.getClass().getDeclaredField("betonEnabled");
            field.setAccessible(true);
            field.setBoolean(mythicPlugin, enabled);
        } catch (Throwable ignored) {
        }
    }

    public static EntityType resolveEntityTypeCompat(String name) {
        if (name == null || name.isEmpty()) {
            return EntityType.UNKNOWN;
        }

        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
        }

        EntityType byName = EntityType.fromName(name);
        if (byName != null) {
            return byName;
        }

        return switch (name) {
            case "SNOW_GOLEM" -> EntityType.SNOWMAN;
            case "BREEZE" -> EntityType.BLAZE;
            case "BOGGED" -> EntityType.SKELETON;
            case "ARMADILLO" -> EntityType.RABBIT;
            case "WIND_CHARGE", "BREEZE_WIND_CHARGE" -> EntityType.SMALL_FIREBALL;
            default -> EntityType.UNKNOWN;
        };
    }

    public static void itemsAdderResolveTransitiveLibrariesCompat(Object libraryManager, Object library) {
        if (libraryManager == null || library == null) {
            return;
        }

        try {
            String groupId = stringValueCompat(invokeNoArgs(library, "getGroupId"));
            String artifactId = stringValueCompat(invokeNoArgs(library, "getArtifactId"));
            String version = stringValueCompat(invokeNoArgs(library, "getVersion"));
            String classifier = blankToNullCompat(stringValueCompat(invokeNoArgs(library, "getClassifier")));
            if (groupId == null || artifactId == null || version == null) {
                return;
            }

            java.util.Set<String> excludedDependencies = new java.util.HashSet<>();
            Object excludedObj = invokeNoArgs(library, "getExcludedTransitiveDependencies");
            if (excludedObj instanceof java.util.Collection<?> excludedCollection) {
                for (Object excluded : excludedCollection) {
                    String excludedGroup = stringValueCompat(invokeNoArgs(excluded, "getGroupId"));
                    String excludedArtifact = stringValueCompat(invokeNoArgs(excluded, "getArtifactId"));
                    if (excludedGroup != null && excludedArtifact != null) {
                        excludedDependencies.add(excludedGroup + ":" + excludedArtifact);
                    }
                }
            }

            com.mohistmc.org.eclipse.aether.RepositorySystem repositorySystem = createZapperRepositorySystemCompat();
            com.mohistmc.org.eclipse.aether.DefaultRepositorySystemSession session =
                    createZapperRepositorySessionCompat(repositorySystem);
            java.util.List<com.mohistmc.org.eclipse.aether.repository.RemoteRepository> repositories =
                    createItemsAdderRemoteRepositoriesCompat(libraryManager, library);

            com.mohistmc.org.eclipse.aether.artifact.Artifact requestedArtifact =
                    new com.mohistmc.org.eclipse.aether.artifact.DefaultArtifact(
                            groupId,
                            artifactId,
                            classifier == null ? "" : classifier,
                            "jar",
                            version
                    );
            com.mohistmc.org.eclipse.aether.graph.Dependency requestedDependency =
                    new com.mohistmc.org.eclipse.aether.graph.Dependency(requestedArtifact, null);
            com.mohistmc.org.eclipse.aether.collection.CollectRequest collectRequest =
                    new com.mohistmc.org.eclipse.aether.collection.CollectRequest(
                            (com.mohistmc.org.eclipse.aether.graph.Dependency) null,
                            java.util.List.of(requestedDependency),
                            repositories
                    );
            com.mohistmc.org.eclipse.aether.resolution.DependencyResult dependencyResult =
                    repositorySystem.resolveDependencies(
                            session,
                            new com.mohistmc.org.eclipse.aether.resolution.DependencyRequest(collectRequest, null)
                    );

            Class<?> managerClass = libraryManager.getClass();
            boolean isolated = booleanValueCompat(invokeNoArgs(library, "isIsolatedLoad"));
            Method addToClasspath = findMethodCompat(managerClass, "addToClasspath", java.nio.file.Path.class);
            Method addToIsolatedClasspath = findMethodCompat(managerClass, "addToIsolatedClasspath", library.getClass(), java.nio.file.Path.class);
            java.util.Set<String> loadedPaths = new java.util.HashSet<>();

            for (com.mohistmc.org.eclipse.aether.resolution.ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
                com.mohistmc.org.eclipse.aether.artifact.Artifact artifact = artifactResult.getArtifact();
                if (artifact == null || artifact.getFile() == null) {
                    continue;
                }

                String resolvedGroupId = artifact.getGroupId();
                String resolvedArtifactId = artifact.getArtifactId();
                String resolvedVersion = artifact.getBaseVersion() == null || artifact.getBaseVersion().isEmpty()
                        ? artifact.getVersion()
                        : artifact.getBaseVersion();
                String resolvedClassifier = blankToNullCompat(artifact.getClassifier());

                if (groupId.equals(resolvedGroupId)
                        && artifactId.equals(resolvedArtifactId)
                        && version.equals(resolvedVersion)
                        && java.util.Objects.equals(classifier, resolvedClassifier)) {
                    continue;
                }

                if (excludedDependencies.contains(resolvedGroupId + ":" + resolvedArtifactId)) {
                    continue;
                }

                java.nio.file.Path path = artifact.getFile().toPath().toAbsolutePath().normalize();
                String normalizedPath = path.toString();
                if (!loadedPaths.add(normalizedPath)) {
                    continue;
                }

                if (isolated && addToIsolatedClasspath != null) {
                    addToIsolatedClasspath.invoke(libraryManager, library, path);
                } else if (addToClasspath != null) {
                    addToClasspath.invoke(libraryManager, path);
                }
            }
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to resolve ItemsAdder transitive libraries", throwable);
        }
    }

    public static void loadZapperDependenciesCompat(Object dependencyManager) {
        if (dependencyManager == null) {
            return;
        }

        try {
            Class<?> managerClass = dependencyManager.getClass();
            java.io.File directory = (java.io.File) readDeclaredFieldCompat(managerClass, dependencyManager, "directory");
            Object loaderWrapper = readDeclaredFieldCompat(managerClass, dependencyManager, "loaderWrapper");
            Object pdf = readDeclaredFieldCompat(managerClass, dependencyManager, "pdf");
            Object dependenciesObj = readDeclaredFieldCompat(managerClass, dependencyManager, "dependencies");
            Object repositoriesObj = readDeclaredFieldCompat(managerClass, dependencyManager, "repositories");
            Object relocationsObj = readDeclaredFieldCompat(managerClass, dependencyManager, "relocations");

            if (!(dependenciesObj instanceof java.util.Collection<?> dependencies) || loaderWrapper == null || directory == null) {
                return;
            }

            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Unable to create Zapper directory " + directory.getAbsolutePath());
            }

            java.util.Collection<?> repositories =
                    repositoriesObj instanceof java.util.Collection<?> collection ? collection : java.util.List.of();
            java.util.List<?> relocations =
                    relocationsObj instanceof java.util.List<?> list ? list : java.util.List.of();

            ClassLoader pluginClassLoader = managerClass.getClassLoader();
            java.util.LinkedHashMap<String, ZapperResolvedArtifact> resolvedArtifacts = new java.util.LinkedHashMap<>();
            for (Object dependency : dependencies) {
                resolveZapperArtifactsCompat(dependency, repositories, resolvedArtifacts);
            }

            Method addUrlMethod = findMethodCompat(loaderWrapper.getClass(), "addURL", java.net.URL.class, boolean.class);
            Method flushMethod = findMethodCompat(loaderWrapper.getClass(), "flush");
            if (addUrlMethod == null || flushMethod == null) {
                throw new IllegalStateException("Unable to access Zapper class loader wrapper");
            }

            for (ZapperResolvedArtifact artifact : resolvedArtifacts.values()) {
                if (artifact.file == null || !artifact.file.isFile()) {
                    continue;
                }

                java.io.File libraryFile = zapperLibraryFileCompat(
                        directory,
                        artifact.groupId,
                        artifact.artifactId,
                        artifact.version,
                        artifact.classifier,
                        false
                );
                copyResolvedArtifactCompat(artifact.file, libraryFile);

                java.io.File loadFile = libraryFile;
                if (!relocations.isEmpty()) {
                    java.io.File relocatedFile = zapperLibraryFileCompat(
                            directory,
                            artifact.groupId,
                            artifact.artifactId,
                            artifact.version,
                            artifact.classifier,
                            true
                    );
                    relocateZapperArtifactCompat(pluginClassLoader, libraryFile, relocatedFile, relocations);
                    loadFile = relocatedFile;
                }

                loadFile = remapZapperArtifactCompat(pluginClassLoader, pdf, loadFile, artifact.remap);
                addUrlMethod.invoke(loaderWrapper, loadFile.toURI().toURL(), artifact.remap);
            }

            flushMethod.invoke(loaderWrapper);
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to load Zapper libraries", throwable);
        }
    }

    private static void resolveZapperArtifactsCompat(
            Object dependency,
            java.util.Collection<?> repositoryObjects,
            java.util.Map<String, ZapperResolvedArtifact> resolvedArtifacts
    ) throws Exception {
        String groupId = stringValueCompat(invokeNoArgs(dependency, "getGroupId"));
        String artifactId = stringValueCompat(invokeNoArgs(dependency, "getArtifactId"));
        String version = stringValueCompat(invokeNoArgs(dependency, "getVersion"));
        String classifier = blankToNullCompat(stringValueCompat(invokeNoArgs(dependency, "getClassifier")));
        boolean remap = booleanValueCompat(invokeNoArgs(dependency, "isRemap"));

        if (groupId == null || artifactId == null || version == null) {
            return;
        }

        com.mohistmc.org.eclipse.aether.RepositorySystem repositorySystem = createZapperRepositorySystemCompat();
        com.mohistmc.org.eclipse.aether.DefaultRepositorySystemSession session =
                createZapperRepositorySessionCompat(repositorySystem);
        java.util.List<com.mohistmc.org.eclipse.aether.repository.RemoteRepository> repositories =
                createZapperRemoteRepositoriesCompat(repositoryObjects);

        com.mohistmc.org.eclipse.aether.artifact.Artifact requestedArtifact =
                new com.mohistmc.org.eclipse.aether.artifact.DefaultArtifact(
                        groupId,
                        artifactId,
                        classifier == null ? "" : classifier,
                        "jar",
                        version
                );
        com.mohistmc.org.eclipse.aether.graph.Dependency requestedDependency =
                new com.mohistmc.org.eclipse.aether.graph.Dependency(requestedArtifact, null);
        com.mohistmc.org.eclipse.aether.collection.CollectRequest collectRequest =
                new com.mohistmc.org.eclipse.aether.collection.CollectRequest(
                        (com.mohistmc.org.eclipse.aether.graph.Dependency) null,
                        java.util.List.of(requestedDependency),
                        repositories
                );
        com.mohistmc.org.eclipse.aether.resolution.DependencyResult dependencyResult =
                repositorySystem.resolveDependencies(
                        session,
                        new com.mohistmc.org.eclipse.aether.resolution.DependencyRequest(collectRequest, null)
                );

        for (com.mohistmc.org.eclipse.aether.resolution.ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
            com.mohistmc.org.eclipse.aether.artifact.Artifact artifact = artifactResult.getArtifact();
            if (artifact == null || artifact.getFile() == null) {
                continue;
            }

            String resolvedGroupId = artifact.getGroupId();
            String resolvedArtifactId = artifact.getArtifactId();
            String resolvedVersion = artifact.getBaseVersion() == null || artifact.getBaseVersion().isEmpty()
                    ? artifact.getVersion()
                    : artifact.getBaseVersion();
            String resolvedClassifier = blankToNullCompat(artifact.getClassifier());
            boolean artifactRemap = remap
                    && groupId.equals(resolvedGroupId)
                    && artifactId.equals(resolvedArtifactId)
                    && version.equals(resolvedVersion)
                    && java.util.Objects.equals(classifier, resolvedClassifier);

            String key = resolvedGroupId + ":" + resolvedArtifactId + ":" + resolvedVersion + ":" + resolvedClassifier;
            ZapperResolvedArtifact existing = resolvedArtifacts.get(key);
            if (existing == null || (!existing.remap && artifactRemap)) {
                resolvedArtifacts.put(
                        key,
                        new ZapperResolvedArtifact(
                                resolvedGroupId,
                                resolvedArtifactId,
                                resolvedVersion,
                                resolvedClassifier,
                                artifact.getFile(),
                                artifactRemap
                        )
                );
            }
        }
    }

    private static com.mohistmc.org.eclipse.aether.RepositorySystem createZapperRepositorySystemCompat() {
        com.mohistmc.org.eclipse.aether.impl.DefaultServiceLocator locator =
                com.mohistmc.org.apache.maven.repository.internal.MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(
                com.mohistmc.org.eclipse.aether.spi.connector.RepositoryConnectorFactory.class,
                com.mohistmc.org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory.class
        );
        locator.addService(
                com.mohistmc.org.eclipse.aether.spi.connector.transport.TransporterFactory.class,
                com.mohistmc.org.eclipse.aether.transport.http.HttpTransporterFactory.class
        );
        return locator.getService(com.mohistmc.org.eclipse.aether.RepositorySystem.class);
    }

    private static com.mohistmc.org.eclipse.aether.DefaultRepositorySystemSession createZapperRepositorySessionCompat(
            com.mohistmc.org.eclipse.aether.RepositorySystem repositorySystem
    ) {
        com.mohistmc.org.eclipse.aether.DefaultRepositorySystemSession session =
                com.mohistmc.org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession();
        session.setChecksumPolicy(com.mohistmc.org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_WARN);
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(
                        session,
                        new com.mohistmc.org.eclipse.aether.repository.LocalRepository("libraries")
                )
        );
        return session;
    }

    private static java.util.List<com.mohistmc.org.eclipse.aether.repository.RemoteRepository> createZapperRemoteRepositoriesCompat(
            java.util.Collection<?> repositoryObjects
    ) {
        java.util.LinkedHashMap<String, com.mohistmc.org.eclipse.aether.repository.RemoteRepository> repositories =
                new java.util.LinkedHashMap<>();

        if (repositoryObjects != null) {
            for (Object repositoryObject : repositoryObjects) {
                String url = zapperRepositoryUrlCompat(repositoryObject);
                if (url == null || url.isEmpty()) {
                    continue;
                }

                repositories.computeIfAbsent(
                        url,
                        ignored -> new com.mohistmc.org.eclipse.aether.repository.RemoteRepository.Builder(
                                "oneworld-zapper-" + repositories.size(),
                                "default",
                                url
                        ).build()
                );
            }
        }

        if (repositories.isEmpty()) {
            String central = com.oneworldstudiomc.bukkit.PluginsLibrarySource.DEFAULT;
            repositories.put(
                    central,
                    new com.mohistmc.org.eclipse.aether.repository.RemoteRepository.Builder(
                            "oneworld-zapper-central",
                            "default",
                            central
                    ).build()
            );
        }

        return new java.util.ArrayList<>(repositories.values());
    }

    private static java.util.List<com.mohistmc.org.eclipse.aether.repository.RemoteRepository> createItemsAdderRemoteRepositoriesCompat(
            Object libraryManager,
            Object library
    ) throws IllegalAccessException {
        java.util.LinkedHashMap<String, com.mohistmc.org.eclipse.aether.repository.RemoteRepository> repositories =
                new java.util.LinkedHashMap<>();

        addItemsAdderRepositoryUrlCompat(repositories, com.oneworldstudiomc.bukkit.PluginsLibrarySource.DEFAULT);
        addItemsAdderRepositoriesCompat(repositories, invokeNoArgs(library, "getRepositories"));
        addItemsAdderRepositoriesCompat(repositories, invokeNoArgs(library, "getFallbackRepositories"));
        addItemsAdderRepositoriesCompat(
                repositories,
                readDeclaredFieldCompat(libraryManager.getClass(), libraryManager, "repositories")
        );

        return new java.util.ArrayList<>(repositories.values());
    }

    private static void addItemsAdderRepositoriesCompat(
            java.util.Map<String, com.mohistmc.org.eclipse.aether.repository.RemoteRepository> repositories,
            Object repositoryValues
    ) {
        if (!(repositoryValues instanceof java.util.Collection<?> collection)) {
            return;
        }

        for (Object repositoryValue : collection) {
            String url = stringValueCompat(repositoryValue);
            if ((url == null || url.isBlank()) && repositoryValue != null) {
                url = repositoryValue.toString();
            }
            addItemsAdderRepositoryUrlCompat(repositories, url);
        }
    }

    private static void addItemsAdderRepositoryUrlCompat(
            java.util.Map<String, com.mohistmc.org.eclipse.aether.repository.RemoteRepository> repositories,
            String url
    ) {
        if (url == null || url.isBlank()) {
            return;
        }

        String normalizedUrl = ensureTrailingSlashCompat(url);
        repositories.computeIfAbsent(
                normalizedUrl,
                ignored -> new com.mohistmc.org.eclipse.aether.repository.RemoteRepository.Builder(
                        "oneworld-itemsadder-" + repositories.size(),
                        "default",
                        normalizedUrl
                ).build()
        );
    }

    private static String zapperRepositoryUrlCompat(Object repositoryObject) {
        if (repositoryObject == null) {
            return null;
        }

        try {
            Method method = findMethodCompat(repositoryObject.getClass(), "getRepositoryURL");
            Object value = method == null ? null : method.invoke(repositoryObject);
            if (value instanceof String url && !url.isEmpty()) {
                return ensureTrailingSlashCompat(url);
            }
        } catch (Throwable ignored) {
        }

        String fallback = repositoryObject.toString();
        return fallback == null || fallback.isEmpty() ? null : ensureTrailingSlashCompat(fallback);
    }

    private static java.io.File zapperLibraryFileCompat(
            java.io.File directory,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            boolean relocated
    ) {
        StringBuilder fileName = new StringBuilder();
        fileName.append(groupId).append('.').append(artifactId).append('-').append(version);
        if (classifier != null && !classifier.isBlank()) {
            fileName.append('-').append(classifier);
        }
        if (relocated) {
            fileName.append("-relocated");
        }
        fileName.append(".jar");
        return new java.io.File(directory, fileName.toString());
    }

    private static void copyResolvedArtifactCompat(java.io.File sourceFile, java.io.File targetFile) throws java.io.IOException {
        java.io.File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (targetFile.isFile()
                && targetFile.length() == sourceFile.length()
                && isValidJarFileCompat(targetFile)) {
            return;
        }

        java.nio.file.Files.copy(
                sourceFile.toPath(),
                targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static void relocateZapperArtifactCompat(
            ClassLoader pluginClassLoader,
            java.io.File sourceFile,
            java.io.File relocatedFile,
            java.util.List<?> relocations
    ) throws Exception {
        if (relocatedFile.isFile()
                && relocatedFile.length() > 0
                && relocatedFile.lastModified() >= sourceFile.lastModified()
                && isValidJarFileCompat(relocatedFile)) {
            return;
        }

        Class<?> relocatorClass = Class.forName("revxrsal.zapper.relocation.Relocator", true, pluginClassLoader);
        Method relocateMethod = relocatorClass.getMethod("relocate", java.io.File.class, java.io.File.class, java.util.List.class);
        relocateMethod.invoke(null, sourceFile, relocatedFile, relocations);
    }

    private static java.io.File remapZapperArtifactCompat(
            ClassLoader pluginClassLoader,
            Object pluginDescription,
            java.io.File file,
            boolean remap
    ) {
        if (!remap || file == null) {
            return file;
        }

        try {
            Class<?> remapperClass = Class.forName("revxrsal.zapper.remapper.PaperLibraryRemapper", true, pluginClassLoader);
            Method remapMethod = remapperClass.getMethod(
                    "tryRemap",
                    org.bukkit.plugin.PluginDescriptionFile.class,
                    java.io.File.class,
                    boolean.class
            );
            Object remapped = remapMethod.invoke(null, pluginDescription, file, true);
            return remapped instanceof java.io.File remappedFile ? remappedFile : file;
        } catch (Throwable ignored) {
            return file;
        }
    }

    private static boolean isValidJarFileCompat(java.io.File file) {
        try (java.util.jar.JarFile ignored = new java.util.jar.JarFile(file)) {
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object readDeclaredFieldCompat(Class<?> owner, Object instance, String name) throws IllegalAccessException {
        Field field = findFieldByNameCompat(owner, name);
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        return field.get(instance);
    }

    private static String stringValueCompat(Object value) {
        return value instanceof String string ? string : null;
    }

    private static boolean booleanValueCompat(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static String blankToNullCompat(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String ensureTrailingSlashCompat(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static final class ZapperResolvedArtifact {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String classifier;
        private final java.io.File file;
        private final boolean remap;

        private ZapperResolvedArtifact(
                String groupId,
                String artifactId,
                String version,
                String classifier,
                java.io.File file,
                boolean remap
        ) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.classifier = classifier;
            this.file = file;
            this.remap = remap;
        }
    }

    private static void helloWorld(ClassNode node, String a, String b) {
        node.methods.forEach(method -> {
            for (AbstractInsnNode next : method.instructions) {
                if (next instanceof LdcInsnNode ldcInsnNode) {
                    if (ldcInsnNode.cst instanceof String str) {
                        if (a.equals(str)) {
                            ldcInsnNode.cst = b;
                        }
                    }
                }
            }
        });
    }

    private static void clearLocalDebugInfo(MethodNode methodNode) {
        if (methodNode.localVariables != null) {
            methodNode.localVariables.clear();
        }
        methodNode.visibleLocalVariableAnnotations = null;
        methodNode.invisibleLocalVariableAnnotations = null;
    }

    private static void clearClassLocalDebugInfo(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            clearLocalDebugInfo(methodNode);
        }
    }

    private static void helloWorld(ClassNode node, int a, int b) {
        node.methods.forEach(method -> {
            for (AbstractInsnNode next : method.instructions) {
                if (next instanceof IntInsnNode ldcInsnNode) {
                    if (ldcInsnNode.operand == a) {
                        ldcInsnNode.operand = b;
                    }
                }
            }
        });
    }
}
