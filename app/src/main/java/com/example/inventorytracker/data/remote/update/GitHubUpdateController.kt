package com.example.inventorytracker.data.remote.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class GitHubRelease(
    val tag: String,
    val notes: String,
    val apkAssetUrl: String,
    val apkSha256: String? = null
)

class GitHubUpdateController(private val context: Context) {
    private val client = OkHttpClient()
    private val repository = "MourtisStareye/InventroyTracker"

    suspend fun checkForUpdate(): GitHubRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "InventoryTracker-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "GitHub did not allow anonymous release access. Confirm the repository and its Releases are public."
                    403 -> "GitHub temporarily denied the update check. Check the internet connection and try again later."
                    404 -> "GitHub returned 404. Verify the public repository name and publish a GitHub Release first. The updater checks Releases, not ordinary commits."
                    else -> "GitHub release check failed with HTTP ${response.code}."
                }
                throw IllegalStateException(message)
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val tag = json.optString("tag_name")
            val currentName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            if (compareVersion(tag, currentName) <= 0) return@withContext null
            val assets = json.optJSONArray("assets") ?: throw IllegalStateException("Release has no downloadable assets.")
            var assetUrl: String? = null
            var assetSha256: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name").lowercase()
                if (name == "app-release.apk" || name == "app-debug.apk" || name == "inventorytracker.apk") {
                    assetUrl = asset.optString("url")
                    assetSha256 = asset.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                    break
                }
            }
            GitHubRelease(tag, json.optString("body", "No release notes were provided."), assetUrl
                ?: throw IllegalStateException("Release must include app-release.apk or app-debug.apk."))
                .copy(apkSha256 = assetSha256)
        }
    }

    private fun compareVersion(first: String, second: String): Int {
        fun parts(value: String) = value.trim().removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(first); val b = parts(second)
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    suspend fun downloadAndInstall(release: GitHubRelease) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(release.apkAssetUrl)
            .header("Accept", "application/octet-stream")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "InventoryTracker-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("APK download failed (HTTP ${response.code}).")
            val destination = File(context.cacheDir, "updates/inventorytracker-${release.tag}.apk")
            destination.parentFile?.mkdirs()
            destination.outputStream().use { output -> response.body?.byteStream()?.copyTo(output) }
            if (destination.length() < 4 || !destination.inputStream().use { it.readNBytesCompat(2).contentEquals(byteArrayOf(0x50, 0x4b)) }) {
                destination.delete()
                throw IllegalStateException("GitHub did not return an APK file.")
            }
            val expected = release.apkSha256
            if (expected != null) {
                val digest = MessageDigest.getInstance("SHA-256")
                destination.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    destination.delete()
                    throw IllegalStateException("The downloaded APK failed GitHub's SHA-256 release digest check.")
                }
            }
            destination
        }
    }

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            requestInstallPermission()
            throw IllegalStateException("Allow Inventory Tracker to install updates, then check for the update again.")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun java.io.InputStream.readNBytesCompat(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(buffer, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return buffer.copyOf(offset)
    }
}
