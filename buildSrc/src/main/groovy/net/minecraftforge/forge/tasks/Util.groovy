package net.minecraftforge.forge.tasks

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

public class Util {
	static final ASM_LEVEL = Opcodes.ASM9

	static void init() {
		File.metaClass.sha1 = { ->
			MessageDigest md = MessageDigest.getInstance('SHA-1')
			delegate.eachByte 4096, {bytes, size ->
				md.update(bytes, 0, size)
			}
			return md.digest().collect {String.format "%02x", it}.join()
		}
        File.metaClass.getSha1 = { !delegate.exists() ? null : delegate.sha1() }

		File.metaClass.json = { -> new JsonSlurper().parseText(delegate.text) }
        File.metaClass.getJson = { return delegate.exists() ? new JsonSlurper().parse(delegate) : [:] }
        File.metaClass.setJson = { json -> delegate.text = new JsonBuilder(json).toPrettyString() }

		Date.metaClass.iso8601 = { ->
			def format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
			def result = format.format(delegate)
			return result[0..21] + ':' + result[22..-1]
		}

        String.metaClass.rsplit = { String del, int limit = -1 ->
            def lst = new ArrayList()
            def x = 0, idx
            def tmp = delegate
            while ((idx = tmp.lastIndexOf(del)) != -1 && (limit == -1 || x++ < limit)) {
                lst.add(0, tmp.substring(idx + del.length(), tmp.length()))
                tmp = tmp.substring(0, idx)
            }
            lst.add(0, tmp)
            return lst
        }
	}

	public static String[] getClasspath(project, libs, artifact) {
		def ret = []
		artifactTree(project, artifact).each { key, lib ->
			libs[lib.name] = lib
			if (lib.name != artifact)
				ret.add(lib.name)
		}
		return ret
	}

	public static def getArtifacts(project, config, classifiers) {
		def ret = [:]
		config.resolvedConfiguration.resolvedArtifacts.each {
			def art = [
				group: it.moduleVersion.id.group,
				name: it.moduleVersion.id.name,
				version: it.moduleVersion.id.version,
				classifier: it.classifier,
				extension: it.extension,
				file: it.file
			]
			def key = art.group + ':' + art.name
			def folder = "${art.group.replace('.', '/')}/${art.name}/${art.version}/"
			def filename = "${art.name}-${art.version}"
			if (art.classifier != null)
				filename += "-${art.classifier}"
			filename += ".${art.extension}"
			def path = "${folder}${filename}"
			def url = inferDownloadUrl(art, path)
			ret[key] = [
				name: "${art.group}:${art.name}:${art.version}" + (art.classifier == null ? '' : ":${art.classifier}") + (art.extension == 'jar' ? '' : "@${art.extension}"),
				downloads: [
					artifact: [
						path: path,
						url: url,
						sha1: sha1(art.file),
						size: art.file.length()
					]
				]
			]
		}
		return ret
	}

	private static String inferDownloadUrl(Map art, String path) {
		if (art.group == 'net.minecraft' || art.group.startsWith('com.mojang')) {
			return "https://libraries.minecraft.net/${path}"
		}

		if (art.group.startsWith('net.minecraftforge') || art.group.startsWith('cpw.mods')) {
			// installertools 1.4.1 is not mirrored on neoforged, keep official forge URL.
			if (art.group == 'net.minecraftforge' && art.name == 'installertools') {
				return "https://maven.minecraftforge.net/${path}"
			}
			return "https://maven.neoforged.net/releases/${path}"
		}

		if (art.group.startsWith('com.mohistmc') ||
				art.group == 'io.izzel' ||
				(art.group == 'commons-lang' && art.version.endsWith('-mohist')) ||
				(art.group == 'net.minecrell' && art.name == 'terminalconsoleappender')) {
			return "https://maven.mohistmc.com/${path}"
		}

		return "https://repo.maven.apache.org/maven2/${path}"
	}

    public static def getMavenPath(task) {
        def classifier = task.archiveClassifier.get()
        def dep = "${task.project.group}:${task.project.name}:${task.project.version}" + (classifier == '' ? '' : ':' + classifier)
        return "${task.project.group.replace('.', '/')}/${task.project.name}/${task.project.version}/${task.project.name}-${task.project.version}".toString() + (classifier == '' ? '' : '-' + classifier) + '.jar'
    }

    public static def getMavenDep(task) {
        def classifier = task.archiveClassifier.get()
        return "${task.project.group}:${task.project.name}:${task.project.version}" + (classifier == '' ? '' : ':' + classifier)
    }

	public static def iso8601Now() { new Date().iso8601() }

	public static def sha1(file) {
		MessageDigest md = MessageDigest.getInstance('SHA-1')
		file.eachByte 4096, {bytes, size ->
			md.update(bytes, 0, size)
		}
		return md.digest().collect {String.format "%02x", it}.join()
	}

	private static def artifactTree(project, artifact) {
		if (!project.ext.has('tree_resolver'))
			project.ext.tree_resolver = 1
		def cfg = project.configurations.create('tree_resolver_' + project.ext.tree_resolver++)
		def dep = project.dependencies.create(artifact)
		cfg.dependencies.add(dep)
		def files = cfg.resolve()
		return getArtifacts(project, cfg, true)
	}

	private static boolean checkExists(url) {
		try {
			def conn = new URL(url).openConnection()
			conn.connectTimeout = 3000
			conn.readTimeout = 3000
			if (conn instanceof HttpURLConnection) {
				conn.requestMethod = 'HEAD'
				conn.instanceFollowRedirects = true
			}
			conn.connect()
			if (conn instanceof HttpURLConnection)
				return conn.responseCode >= 200 && conn.responseCode < 400
			return true
		} catch (Exception e) {
			if (e.toString().contains('unable to find valid certification path to requested target'))
				throw new RuntimeException('Failed to connect to ' + url + ': Missing certificate root authority, try updating java')
			return false
		}
	}

	static String getLatestForgeVersion(mcVersion) {
		final json = new JsonSlurper().parseText(new URL('https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json').getText('UTF-8'))
		final ver = json.promos["$mcVersion-latest"]
		ver === null ? null : (mcVersion + '-' + ver)
    }

	static void processClassNodes(File file, Closure process) {
		file.withInputStream { i ->
			new ZipInputStream(i).withCloseable { zin ->
				ZipEntry zein
				while ((zein = zin.nextEntry) != null) {
					if (zein.name.endsWith('.class')) {
						def node = new ClassNode(ASM_LEVEL)
						new ClassReader(zin).accept(node, 0)
						process(node)
					}
				}
			}
		}
	}
}
