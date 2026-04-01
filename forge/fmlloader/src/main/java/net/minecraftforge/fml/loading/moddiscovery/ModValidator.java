/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import net.minecraftforge.fml.loading.*;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ModValidator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<IModFile.Type, List<ModFile>> modFiles;
    private final List<ModFile> candidatePlugins;
    private final List<ModFile> candidateMods;
    private final List<ModFile> gameLibraries;
    private final Set<String> pluginModuleNames;
    private LoadingModList loadingModList;
    private final List<IModFile> brokenFiles;
    private final List<EarlyLoadingException.ExceptionData> discoveryErrorData;

    public ModValidator(final Map<IModFile.Type, List<ModFile>> modFiles, final List<IModFileInfo> brokenFiles, final List<EarlyLoadingException.ExceptionData> discoveryErrorData) {
        this.modFiles = modFiles;
        this.gameLibraries = deduplicateByModuleName(lst(modFiles.get(IModFile.Type.GAMELIBRARY)));
        this.candidatePlugins = deduplicateByModuleName(merge(lst(modFiles.get(IModFile.Type.LANGPROVIDER)), lst(modFiles.get(IModFile.Type.LIBRARY))));
        this.pluginModuleNames = this.candidatePlugins.stream()
                .map(ModValidator::moduleName)
                .collect(Collectors.toSet());
        this.candidateMods = deduplicateByModuleName(merge(lst(modFiles.get(IModFile.Type.MOD)), this.gameLibraries));
        this.discoveryErrorData = discoveryErrorData;
        this.brokenFiles = brokenFiles.stream().map(IModFileInfo::getFile).collect(Collectors.toList()); // mutable list
    }

    private static List<ModFile> lst(List<ModFile> files) {
        return files == null ? new ArrayList<>() : new ArrayList<>(files);
    }

    private static List<ModFile> merge(List<ModFile> first, List<ModFile> second) {
        var merged = new ArrayList<ModFile>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    private static List<ModFile> deduplicateByModuleName(List<ModFile> files) {
        var deduplicated = new LinkedHashMap<String, ModFile>();
        for (var file : files) {
            var moduleName = moduleName(file);
            var existing = deduplicated.get(moduleName);
            if (existing == null || compareVersion(file, existing) > 0) {
                deduplicated.put(moduleName, file);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private static int compareVersion(ModFile left, ModFile right) {
        return versionOf(left).compareTo(versionOf(right));
    }

    private static ArtifactVersion versionOf(ModFile modFile) {
        if (modFile.getModFileInfo() == null || modFile.getModInfos() == null || modFile.getModInfos().isEmpty()) {
            return modFile.getJarVersion();
        }
        return modFile.getModInfos().get(0).getVersion();
    }

    private static String moduleName(ModFile modFile) {
        if (modFile.getModFileInfo() == null || modFile.getModFileInfo().getMods().isEmpty()) {
            return modFile.getSecureJar().name();
        }
        return modFile.getModFileInfo().moduleName();
    }

    public void stage1Validation() {
        brokenFiles.addAll(validateFiles(candidateMods));
        if (LOGGER.isDebugEnabled(LogMarkers.SCAN)) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} mod files with {} mods", candidateMods.size(), candidateMods.stream().mapToInt(mf -> mf.getModInfos().size()).sum());
        }
        ImmediateWindowHandler.updateProgress("Found "+candidateMods.size()+" mod candidates");
    }

    @NotNull
    private List<ModFile> validateFiles(final List<ModFile> mods) {
        final List<ModFile> brokenFiles = new ArrayList<>();
        for (Iterator<ModFile> iterator = mods.iterator(); iterator.hasNext();) {
            ModFile modFile = iterator.next();
            if (!modFile.getProvider().isValid(modFile) || !modFile.identifyMods()) {
                LOGGER.warn(LogMarkers.SCAN, "File {} has been ignored - it is invalid", modFile.getFilePath());
                iterator.remove();
                brokenFiles.add(modFile);
            }
        }
        return brokenFiles;
    }

    public ITransformationService.Resource getPluginResources() {
        var plugins = new ArrayList<SecureJar>();
        var seenModuleNames = new HashSet<String>();
        for (var plugin : this.candidatePlugins) {
            addUniqueJar(plugins, seenModuleNames, plugin, "plugin");
        }
        return new ITransformationService.Resource(IModuleLayerManager.Layer.PLUGIN, plugins);
    }

    public ITransformationService.Resource getModResources() {
        var mods = new ArrayList<SecureJar>();
        var seenModuleNames = new HashSet<>(this.pluginModuleNames);
        // Add only the valid mods that we will be attempting to load.
        // If any detectable error happens during the sorting process {missing deps, duplicates,
        // This helps prevent coremods/mixins from screwing up us displaying the error screens that the sorting/validation is trying to display.
        // This won't fix them all as they are still loaded and will still apply but it might help
        for (var info : this.loadingModList.getModFiles())
            addUniqueJar(mods, seenModuleNames, info.getFile(), "game");

        // Add any game libraries, this *may* be duplicates, depending on the state of the sorting. But until I get around to re-writing
        // that clusterfuck of a system, have this simple de-duplication. Order of resource is important, but i'm unsure if duplication is
        // so might as well waste a few cycles on checking. Still faster then converting from a set to a list.
        // Ideally we would explicitly list out which mods to load {forge and MC being the only ones} but some coremods could require extra things.
        // So try game libraries as well
        for (var lib : this.gameLibraries) {
            addUniqueJar(mods, seenModuleNames, lib, "game");
        }

        return new ITransformationService.Resource(IModuleLayerManager.Layer.GAME, mods);
    }

    private void addUniqueJar(List<SecureJar> jars, Set<String> seenModuleNames, ModFile modFile, String layerName) {
        var moduleName = moduleName(modFile);
        if (!seenModuleNames.add(moduleName)) {
            LOGGER.debug(LogMarkers.SCAN, "Skipping duplicate {} layer module {} from {}", layerName, moduleName, modFile.getFilePath());
            return;
        }
        jars.add(modFile.getSecureJar());
    }

    private List<EarlyLoadingException.ExceptionData> validateLanguages() {
        List<EarlyLoadingException.ExceptionData> errorData = new ArrayList<>();
        for (Iterator<ModFile> iterator = this.candidateMods.iterator(); iterator.hasNext(); ) {
            final ModFile modFile = iterator.next();
            try {
                modFile.identifyLanguage();
            } catch (EarlyLoadingException e) {
                errorData.addAll(e.getAllData());
                iterator.remove();
            }
        }
        return errorData;
    }

    public BackgroundScanHandler stage2Validation() {
        var errors = validateLanguages();

        var allErrors = new ArrayList<>(errors);
        allErrors.addAll(this.discoveryErrorData);

        loadingModList = ModSorter.sort(candidateMods, allErrors);
        loadingModList.addCoreMods();
        loadingModList.addAccessTransformers();
        loadingModList.setBrokenFiles(brokenFiles);
        BackgroundScanHandler backgroundScanHandler = new BackgroundScanHandler(candidateMods);
        loadingModList.addForScanning(backgroundScanHandler);
        return backgroundScanHandler;
    }
}
