package com.example.inventorytracker.data.remote.sync

import android.net.Uri
import org.json.JSONObject

/** Credentials encoded by the desktop companion in its LAN pairing QR code. */
data class LanSyncPairingPayload(
    val address: String,
    val token: String
) {
    companion object {
        /**
         * Accepts the documented JSON payload or an inventorytracker://pair URI.
         * UPC/EAN values and unrelated QR codes return null.
         */
        fun parse(raw: String): LanSyncPairingPayload? {
            val value = raw.trim()
            if (value.isEmpty()) return null

            val payload = runCatching {
                if (value.startsWith("{")) {
                    val json = JSONObject(value)
                    val type = json.optString("type")
                    if (type.isNotEmpty() && type != "inventorytracker-sync") return null
                    LanSyncPairingPayload(
                        address = json.optString("address", json.optString("desktopAddress")),
                        token = json.optString("token", json.optString("pairingToken"))
                    )
                } else {
                    val uri = Uri.parse(value)
                    if (uri.scheme != "inventorytracker" || uri.host !in setOf("pair", "sync")) return null
                    LanSyncPairingPayload(
                        address = uri.getQueryParameter("address")
                            ?: uri.getQueryParameter("desktopAddress").orEmpty(),
                        token = uri.getQueryParameter("token")
                            ?: uri.getQueryParameter("pairingToken").orEmpty()
                    )
                }
            }.getOrNull() ?: return null

            if (payload.address.isBlank() || payload.token.isBlank()) return null
            return payload
        }
    }
}
