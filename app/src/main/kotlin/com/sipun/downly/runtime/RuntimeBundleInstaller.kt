package com.sipun.downly.runtime

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object RuntimeBundleInstaller {
    private const val OWNER = "Darkstar085"
    private const val REPOSITORY = "youtubedl-kotlin"
    private const val RELEASE_API =
        "https://api.github.com/repos/$OWNER/$REPOSITORY/releases/latest"
    private const val MAX_BUNDLE_BYTES = 100L * 1024L * 1024L
    @Volatile
    private var processInstalledDirectory: File? = null
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000

    @Throws(IOException::class)
    fun ensureInstalled(context: Context, onProgress: (Float?) -> Unit = {}): File {
        processInstalledDirectory?.let { cached ->
            if (isInstalledValid(cached)) return cached
            processInstalledDirectory = null
        }
        val root = File(context.noBackupFilesDir, ".libraries")
        val legacyRoot = File(context.noBackupFilesDir, "downly-runtime")
        if (findValidInstalledVersion(root) == null &&
            findValidInstalledVersion(legacyRoot) != null
        ) {
            if (root.exists()) deleteRecursively(root)
            if (!legacyRoot.renameTo(root)) {
                throw IOException("Cannot migrate existing Libraries directory")
            }
        } else if (findValidInstalledVersion(root) != null && legacyRoot.exists()) {
            deleteRecursively(legacyRoot)
        }
        if (!root.exists() && !root.mkdirs()) throw IOException("Cannot create Libraries directory")

        val release = try {
            latestReleaseAsset()
        } catch (error: IOException) {
            findValidInstalledVersion(root)?.let {
                processInstalledDirectory = it
                return it
            }
            throw error
        }
        val (assetName, downloadUrl, expectedDigest) = release
        val versionDirectoryName = assetName.removeSuffix(".zip")
        val installed = File(root, versionDirectoryName)
        if (isInstalledValid(installed)) {
            removeObsoleteVersions(root, versionDirectoryName)
            processInstalledDirectory = installed
            return installed
        }

        val downloaded = File(root, "$assetName.part")
        val staging = File(root, ".$versionDirectoryName-staging")
        try {
            onProgress(null)
            download(downloadUrl, downloaded, onProgress)
            if (sha256(downloaded) != expectedDigest) {
                throw IOException("Libraries archive SHA-256 verification failed")
            }
            onProgress(0.90f)
            deleteRecursively(staging)
            if (!staging.mkdirs()) throw IOException("Cannot create Libraries staging directory")
            try {
                extractBundle(downloaded, staging)
                onProgress(0.96f)
                verifyChecksums(staging, File(staging, "SHA256SUMS"))
                listOf("libpython.zip.so", "libffmpeg.zip.so", "libaria2c.zip.so").forEach {
                    validateArchive(File(staging, it))
                }
                onProgress(0.99f)
                val previous = File(root, ".$versionDirectoryName-previous")
                deleteRecursively(previous)
                if (installed.exists() && !installed.renameTo(previous)) {
                    throw IOException("Cannot stage previous Libraries archive")
                }
                if (!staging.renameTo(installed)) {
                    if (previous.exists()) previous.renameTo(installed)
                    throw IOException("Cannot activate downloaded Libraries archive")
                }
                deleteRecursively(previous)
                removeObsoleteVersions(root, versionDirectoryName)
            } finally {
                deleteRecursively(staging)
            }
        } finally {
            downloaded.delete()
        }
        processInstalledDirectory = installed
        return installed
    }

    private fun isInstalledValid(directory: File): Boolean {
        val required =
            listOf("libpython.zip.so", "libffmpeg.zip.so", "libaria2c.zip.so", "SHA256SUMS")
        return required.all { File(directory, it).isFile } &&
                runCatching {
                    verifyChecksums(
                        directory,
                        File(directory, "SHA256SUMS")
                    )
                }.getOrDefault(false)
    }

    private fun findValidInstalledVersion(root: File): File? {
        val required =
            listOf("libpython.zip.so", "libffmpeg.zip.so", "libaria2c.zip.so", "SHA256SUMS")
        val versionDirectory = Regex("^libraries_v[0-9][A-Za-z0-9._+-]*$")
        return root.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && versionDirectory.matches(it.name) }
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull { directory ->
                required.all { File(directory, it).isFile } &&
                        runCatching {
                            verifyChecksums(directory, File(directory, "SHA256SUMS"))
                        }.getOrDefault(false)
            }
    }

    private fun removeObsoleteVersions(root: File, keepDirectoryName: String) {
        val versionDirectory = Regex("^libraries_v[0-9][A-Za-z0-9._+-]*$")
        root.listFiles()?.forEach { candidate ->
            if (candidate.isDirectory &&
                candidate.name != keepDirectoryName &&
                versionDirectory.matches(candidate.name)
            ) {
                deleteRecursively(candidate)
            }
        }
    }

    private fun latestReleaseAsset(): Triple<String, String, String> {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Downly-Android")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Libraries archive release lookup failed (HTTP $status)")
            val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                throw IOException("Latest Libraries archive release is not stable")
            }
            val assets = release.optJSONArray("assets")
                ?: throw IOException("Libraries archive release has no assets")
            val expectedAssetName = Regex("^libraries_v[0-9][A-Za-z0-9._+-]*\\.zip$")
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (expectedAssetName.matches(name) && url.startsWith("https://github.com/")) {
                    val digest = asset.optString("digest")
                        .removePrefix("sha256:")
                        .takeIf { it.matches(Regex("^[0-9a-fA-F]{64}$")) }
                        ?.lowercase()
                        ?: throw IOException("Libraries archive release has no valid SHA-256 digest")
                    return Triple(name, url, digest)
                }
            }
            throw IOException("Versioned Libraries archive was not found in the latest release")
        } finally {
            connection?.disconnect()
        }
    }

    private fun download(address: String, destination: File, onProgress: (Float?) -> Unit) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Downly-Android")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Libraries archive download failed (HTTP $status)")
            if (connection.contentLengthLong > MAX_BUNDLE_BYTES) {
                throw IOException("Libraries archive is unexpectedly large")
            }
            val expectedBytes = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BUNDLE_BYTES) throw IOException("Libraries archive exceeds size limit")
                        output.write(buffer, 0, count)
                        onProgress(
                            if (expectedBytes > 0L) (total.toFloat() / expectedBytes.toFloat()).coerceIn(
                                0f,
                                1f
                            ) * 0.85f else null
                        )
                    }
                    output.fd.sync()
                }
            }
            if (!destination.isFile || destination.length() == 0L) {
                throw IOException("Downloaded Libraries archive is empty")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractBundle(bundle: File, destination: File) {
        val allowed =
            setOf("libpython.zip.so", "libffmpeg.zip.so", "libaria2c.zip.so", "SHA256SUMS")
        var count = 0
        var extractedBytes = 0L
        val seen = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(bundle))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name !in allowed || entry.name.contains('/') ||
                    entry.name.contains('\\') || !seen.add(entry.name)
                ) throw IOException("Unexpected or duplicate Libraries archive ZIP entry")
                val output = File(destination, entry.name)
                FileOutputStream(output).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        extractedBytes += read
                        if (total > MAX_BUNDLE_BYTES || extractedBytes > MAX_BUNDLE_BYTES) {
                            throw IOException("Libraries archive extraction exceeds size limit")
                        }
                        stream.write(buffer, 0, read)
                    }
                    stream.fd.sync()
                }
                count++
                zip.closeEntry()
            }
        }
        if (count != allowed.size || seen != allowed) {
            throw IOException("Libraries archive is missing required files")
        }
    }

    private fun verifyChecksums(directory: File, manifest: File): Boolean {
        if (!manifest.isFile || manifest.length() > 16 * 1024) {
            throw IOException("Libraries archive checksum manifest missing")
        }
        val expected = manifest.readLines().map { line ->
            val match = Regex("^([0-9a-fA-F]{64}) [ *](lib(?:python|ffmpeg|aria2c)\\.zip\\.so)$")
                .matchEntire(line.trim())
                ?: throw IOException("Invalid Libraries archive checksum manifest")
            match.groupValues[2] to match.groupValues[1].lowercase()
        }.toMap()
        val names = setOf("libpython.zip.so", "libffmpeg.zip.so", "libaria2c.zip.so")
        if (expected.keys != names) throw IOException("Libraries archive checksum manifest is incomplete")
        for (name in names) {
            val asset = File(directory, name)
            if (!asset.isFile || asset.length() == 0L || sha256(asset) != expected[name]) {
                throw IOException("Libraries archive checksum verification failed for $name")
            }
        }
        return true
    }

    private fun validateArchive(file: File) {
        if (!file.isFile || file.length() < 22) throw IOException("Library archive is missing or too small")
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            if (input.read(header) != header.size ||
                header[0] != 0x50.toByte() || header[1] != 0x4b.toByte()
            ) throw IOException("Library archive is not a ZIP file: ${file.name}")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }
}
