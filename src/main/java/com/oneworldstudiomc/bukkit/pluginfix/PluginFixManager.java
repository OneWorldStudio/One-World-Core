package com.oneworldstudiomc.bukkit.pluginfix;

import java.util.function.Consumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.ARETURN;

public class PluginFixManager {

    public static byte[] injectPluginFix(String className, byte[] clazz) {
        if (className.endsWith("PaperLib")) {
            return patch(clazz, PluginFixManager::removePaper);
        }
        if (className.equals("com.onarandombox.MultiverseCore.utils.WorldManager")) {
            return patch(clazz, MultiverseCore::fix);
        }
        Consumer<ClassNode> patcher = switch (className) {
            case "com.sk89q.worldedit.bukkit.BukkitAdapter" -> WorldEdit::handleBukkitAdapter;
            case "com.sk89q.worldedit.bukkit.adapter.Refraction" -> WorldEdit::handlePickName;
            case "com.sk89q.worldedit.bukkit.adapter.impl.v1_20_R1.PaperweightAdapter$SpigotWatchdog" -> WorldEdit::handleWatchdog;
            case "com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_20_R1.PaperweightBlockMaterial" -> PluginFixManager::fixFawePaperweightBlockMaterial;
            case "com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_20_R1.PaperweightPlatformAdapter" -> PluginFixManager::fixFawePaperweightPlatformAdapter;
            case "com.earth2me.essentials.utils.VersionUtil" -> node -> {
                helloWorld(node, 110, 109);
                helloWorld(node, "brand:", "peace");
            };
            case "com.sk89q.worldedit.bukkit.BukkitConfiguration" -> node -> {
                helloWorld(node, "I accept that I will receive no support with this flag enabled.", "mohist");
                helloWorld(node, "allow-editing-on-unsupported-versions", "mohist");
                helloWorld(node, "false", "mohist");
            };
            case "net.Zrips.CMILib.Reflections" -> node -> helloWorld(node, "bR", "f_36096_");
            case "net.coreprotect.listener.ListenerHandler" -> node -> helloWorld(node, "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer", "mohist");
            case "io.lumine.mythic.bukkit.utils.version.ServerVersion" -> PluginFixManager::fixMythicMobsServerVersion;
            case "io.lumine.mythic.core.volatilecode.v1_20_R1.VolatileAIHandlerImpl" -> PluginFixManager::fixMythicMobsAIHandler;
            case "com.moulberry.axiom.AxiomPaper" -> PluginFixManager::fixAxiomPaper;
            case "com.moulberry.axiom.AxiomReflection" -> PluginFixManager::fixAxiomReflection;
            case "com.moulberry.axiom.WorldExtension" -> PluginFixManager::fixAxiomWorldExtension;
            case "net.playavalon.mythicdungeons.listeners.AvalonListener" -> PluginFixManager::fixMythicDungeonsAvalonListener;
            default -> null;
        };

        return patcher == null ? clazz : patch(clazz, patcher);
    }

    private static byte[] patch(byte[] basicClass, Consumer<ClassNode> handler) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        handler.accept(node);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
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
            toInject.add(new InsnNode(Opcodes.RETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
        }
    }

    private static void fixMythicMobsServerVersion(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (!"isPaper".equals(methodNode.name) || !"()Z".equals(methodNode.desc)) {
                continue;
            }
            InsnList toInject = new InsnList();
            toInject.add(new InsnNode(Opcodes.ICONST_1));
            toInject.add(new InsnNode(Opcodes.IRETURN));
            methodNode.instructions = toInject;
            methodNode.tryCatchBlocks.clear();
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
