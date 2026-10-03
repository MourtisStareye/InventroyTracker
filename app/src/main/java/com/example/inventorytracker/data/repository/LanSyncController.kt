package com.example.inventorytracker.data.repository

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.LinkAddress
import android.net.LinkProperties
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.example.inventorytracker.data.local.dao.InventoryDao
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.remote.sync.LanSyncApi
import com.example.inventorytracker.data.remote.sync.LegacyBooleanJsonAdapter
import com.example.inventorytracker.data.remote.sync.PhotoTransfer
import com.example.inventorytracker.data.remote.sync.SyncRecord
import com.example.inventorytracker.data.remote.sync.SyncRequest
import com.example.inventorytracker.data.remote.sync.SyncResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class LanSyncController(
    context: Context,
    private val inventoryDao: InventoryDao
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("lan_inventory_sync", Context.MODE_PRIVATE)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val syncMutex = Mutex()

    val desktopAddress: String get() = prefs.getString(KEY_ADDRESS, "") ?: ""
    val pairingToken: String get() = prefs.getString(KEY_TOKEN, "") ?: ""
    suspend fun pendingCount(): Int = inventoryDao.getPendingSyncItems().size

    fun savePairing(address: String, token: String): String? {
        val parsed = parseDesktopAddress(address)
            ?: return "Enter the desktop's private IPv4 address, or 127.0.0.1:8765 for USB sync."
        if (token.isBlank()) return "Enter the pairing token shown by the desktop app."
        prefs.edit()
            .putString(KEY_ADDRESS, parsed)
            .putString(KEY_TOKEN, token.trim())
            .apply()
        return null
    }

    fun disconnect() {
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_TOKEN).remove(KEY_CURSOR).apply()
    }

    suspend fun awaitDesktopSyncRequest(): Boolean = withContext(Dispatchers.IO) {
        val address = desktopAddress
        val token = pairingToken
        if (address.isBlank() || token.isBlank()) return@withContext false
        try {
            val usbTunnel = address.substringBefore(':') == USB_LOOPBACK
            if (!usbTunnel) {
                if (Build.VERSION.SDK_INT >= 37 &&
                    appContext.checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
                ) return@withContext false
                val desktopIp = InetAddress.getByName(address.substringBefore(':')) as? Inet4Address
                    ?: return@withContext false
                if (!isOnSameWifiOrEthernetSubnet(desktopIp)) return@withContext false
            }
            val response = api.awaitDesktopSync("http://$address/api/v1/trigger", "Bearer $token")
            response.isSuccessful && response.body()?.syncRequested == true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncNow(): SyncOutcome = syncMutex.withLock { withContext(Dispatchers.IO) {
        val address = desktopAddress
        val token = pairingToken
        if (address.isBlank() || token.isBlank()) return@withContext SyncOutcome.Failure("Pair with a desktop first.")
        try {
            val usbTunnel = address.substringBefore(':') == USB_LOOPBACK
            if (!usbTunnel) {
                val desktopIp = InetAddress.getByName(address.substringBefore(':')) as? Inet4Address
                    ?: return@withContext SyncOutcome.Failure("Desktop address must be a local IPv4 address.")
                if (!isOnSameWifiOrEthernetSubnet(desktopIp)) {
                    return@withContext SyncOutcome.Failure("Desktop is not reachable on this local network. No sync was attempted.")
                }
            }
            val pendingItems = inventoryDao.getPendingSyncItems()
            uploadPendingPhotos(pendingItems, address, token)
            val request = SyncRequest(
                deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString(KEY_DEVICE_ID, it).apply()
                },
                cursor = prefs.getLong(KEY_CURSOR, 0L),
                changes = pendingItems.map { it.toSyncRecord() }
            )
            val response = api.sync("http://$address/api/v1/sync", "Bearer $token", request)
            if (!response.isSuccessful) {
                return@withContext SyncOutcome.Failure("Desktop sync failed (${response.code()}).")
            }
            val body = response.body() ?: return@withContext SyncOutcome.Failure("Desktop returned an empty sync response.")
            applyResponse(body, address, token)
            prefs.edit().putLong(KEY_CURSOR, body.cursor).apply()
            SyncOutcome.Success(body.acknowledgedIds.size, body.records.size)
        } catch (error: Exception) {
            SyncOutcome.Failure(error.localizedMessage ?: "Could not reach the desktop on this local network.")
        }
    } }

    private suspend fun uploadPendingPhotos(items: List<InventoryItem>, address: String, token: String) {
        for (item in items) {
            if (item.isDeleted) continue
            val image = item.imageUrl ?: continue
            val file = localPhotoFile(image) ?: continue
            if (!file.isFile) throw IOException("A selected item photo is missing from this phone.")
            val transfer = PhotoTransfer(data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            val upload = api.uploadPhoto(
                "http://$address/api/v1/photos/${item.syncId}",
                "Bearer $token",
                transfer
            )
            if (!upload.isSuccessful) throw IOException("Desktop photo upload failed (${upload.code()}).")
        }
    }

    private suspend fun applyResponse(response: SyncResponse, address: String, token: String) {
        response.records.forEach { remote ->
            val local = inventoryDao.getItemBySyncId(remote.id)
            val imageUrl = if (!remote.deleted && remote.imageUrl == desktopPhotoReference(remote.id)) {
                downloadPhoto(remote.id, address, token)
            } else {
                remote.imageUrl
            }
            val merged = remote.toInventoryItem(local?.id ?: 0L, imageUrl)
            if (local == null) inventoryDao.insertSyncedItem(merged)
            else inventoryDao.updateItem(merged)
        }
        response.acknowledgedIds.forEach { syncId ->
            val version = response.records.firstOrNull { it.id == syncId }?.version
                ?: inventoryDao.getItemBySyncId(syncId)?.syncVersion
                ?: 0L
            inventoryDao.markSyncAcknowledged(syncId, version)
        }
    }

    private fun isOnSameWifiOrEthernetSubnet(desktop: Inet4Address): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) return false

        val links: List<LinkAddress> = connectivityManager.getLinkProperties(network)?.linkAddresses.orEmpty()
        return links.any { it.address is Inet4Address && sameSubnet(it, desktop) }
    }

    private fun sameSubnet(local: LinkAddress, remote: Inet4Address): Boolean {
        val localBytes = local.address.address
        val remoteBytes = remote.address
        val prefix = local.prefixLength
        for (bit in 0 until prefix) {
            val byteIndex = bit / 8
            val mask = 1 shl (7 - bit % 8)
            if ((localBytes[byteIndex].toInt() and mask) != (remoteBytes[byteIndex].toInt() and mask)) return false
        }
        return true
    }

    private fun parseDesktopAddress(input: String): String? {
        val normalized = input.trim().removePrefix("http://").removeSuffix("/")
        val host = normalized.substringBefore(':')
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { part -> part.toIntOrNull()?.let { it !in 0..255 } != false }) return null
        val first = parts[0].toInt()
        val second = parts[1].toInt()
        val privateAddress = host == USB_LOOPBACK || first == 10 ||
            first == 192 && second == 168 ||
            first == 172 && second in 16..31 ||
            first == 169 && second == 254
        if (!privateAddress) return null
        val port = normalized.substringAfter(':', "8765").toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return "$host:$port"
    }

    private fun InventoryItem.toSyncRecord() = SyncRecord(
        id = syncId,
        name = name,
        barcode = barcode,
        brand = brand,
        quantity = quantity,
        category = category,
        imageUrl = imageUrl?.let { value ->
            if (localPhotoFile(value) != null) desktopPhotoReference(syncId) else value
        },
        location = location,
        notes = notes,
        price = price,
        expirationDate = expirationDate,
        updatedAt = lastUpdated,
        version = syncVersion,
        deleted = isDeleted
    )

    private fun SyncRecord.toInventoryItem(localId: Long, resolvedImageUrl: String?) = InventoryItem(
        id = localId,
        name = name,
        barcode = barcode,
        brand = brand,
        quantity = quantity,
        category = category,
        imageUrl = resolvedImageUrl,
        location = location,
        notes = notes,
        price = price,
        expirationDate = expirationDate,
        lastUpdated = updatedAt,
        syncId = id,
        syncVersion = version,
        isDeleted = deleted,
        syncPending = false
    )

    private fun desktopPhotoReference(syncId: String) = "inventory-photo://$syncId"

    private fun localPhotoFile(value: String): File? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        if (uri.scheme != "file") return null
        return uri.path?.let(::File)
    }

    private suspend fun downloadPhoto(syncId: String, address: String, token: String): String {
        val response = api.downloadPhoto(
            "http://$address/api/v1/photos/$syncId",
            "Bearer $token"
        )
        if (!response.isSuccessful) throw IOException("Desktop photo download failed (${response.code()}).")
        val photo = response.body() ?: throw IOException("Desktop returned an empty photo response.")
        if (photo.mimeType != "image/jpeg") throw IOException("Desktop returned an unsupported photo format.")
        val encoded = photo.data.takeIf { it.isNotEmpty() }
            ?: throw IOException("Desktop returned an empty photo.")
        val bytes = try {
            Base64.decode(encoded, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            throw IOException("Desktop returned an invalid photo.")
        }
        if (bytes.size > MAX_PHOTO_BYTES) throw IOException("Desktop photo is larger than 10 MB.")
        val photoDirectory = File(appContext.filesDir, "item_photos").apply { mkdirs() }
        val destination = File(photoDirectory, "$syncId.jpg")
        destination.writeBytes(bytes)
        return Uri.fromFile(destination).toString()
    }

    private val api: LanSyncApi by lazy {
        val moshi = Moshi.Builder()
            .add(Boolean::class.javaObjectType, LegacyBooleanJsonAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LanSyncApi::class.java)
    }

    sealed interface SyncOutcome {
        data class Success(val uploaded: Int, val downloaded: Int) : SyncOutcome
        data class Failure(val message: String) : SyncOutcome
    }

    companion object {
        private const val KEY_ADDRESS = "desktop_address"
        private const val KEY_TOKEN = "pairing_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CURSOR = "sync_cursor"
        private const val MAX_PHOTO_BYTES = 10_000_000
        private const val USB_LOOPBACK = "127.0.0.1"
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
