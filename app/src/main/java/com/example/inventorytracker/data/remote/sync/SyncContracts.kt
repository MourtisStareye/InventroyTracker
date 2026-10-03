package com.example.inventorytracker.data.remote.sync

import com.squareup.moshi.JsonClass

/** Wire contract expected from the companion desktop application's LAN sync server. */
@JsonClass(generateAdapter = true)
data class SyncRecord(
    val id: String,
    val name: String,
    val barcode: String?,
    val brand: String?,
    val quantity: Int,
    val category: String?,
    val imageUrl: String?,
    val location: String?,
    val notes: String?,
    val price: Double?,
    val expirationDate: Long?,
    val updatedAt: Long,
    val version: Long,
    val deleted: Boolean
)

@JsonClass(generateAdapter = true)
data class SyncRequest(
    val deviceId: String,
    val cursor: Long,
    val changes: List<SyncRecord>
)

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val records: List<SyncRecord> = emptyList(),
    val acknowledgedIds: List<String> = emptyList(),
    val cursor: Long = 0
)

@JsonClass(generateAdapter = true)
data class DesktopSyncTrigger(val syncRequested: Boolean = false)

@JsonClass(generateAdapter = true)
data class PhotoTransfer(
    val mimeType: String = "image/jpeg",
    val data: String
)

@JsonClass(generateAdapter = true)
data class PhotoTransferResult(
    val uploaded: Boolean = false,
    val mimeType: String = "image/jpeg",
    val data: String = ""
)
