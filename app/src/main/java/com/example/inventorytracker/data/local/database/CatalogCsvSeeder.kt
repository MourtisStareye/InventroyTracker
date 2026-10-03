package com.example.inventorytracker.data.local.database

import android.content.Context
import android.util.Log
import com.example.inventorytracker.data.local.dao.InventoryDao
import com.example.inventorytracker.data.local.entity.InventoryItem
import java.util.UUID

class CatalogCsvSeeder(
    private val context: Context,
    private val inventoryDao: InventoryDao
) {
    suspend fun seedIfNeeded() {
        val preferences = context.getSharedPreferences("catalog_seed", Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_VERSION, 0) >= CATALOG_VERSION) return

        val items = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
            parseCsv(reader.readText()).drop(1).mapNotNull(::toInventoryItem)
        }
        inventoryDao.insertCatalogItemsIfMissing(items)
        check(preferences.edit().putInt(KEY_VERSION, CATALOG_VERSION).commit()) {
            "Failed to save miniature paint catalog seed version"
        }
        Log.i(TAG, "Seeded ${items.size} miniature paint catalog rows (existing barcodes preserved).")
    }

    private fun toInventoryItem(row: List<String>): InventoryItem? {
        val headers = headerFields ?: return null
        val fields = headers.zip(row).toMap()
        fun value(column: String) = fields[column]?.trim().orEmpty().takeIf { it.isNotEmpty() }

        val barcode = value("barcode") ?: value("ean") ?: value("upc") ?: return null
        if (!barcode.all { it.isDigit() }) return null
        val name = value("paint_name") ?: return null
        val productCode = value("product_code")
        val range = value("paint_range")
        val variant = value("variant")
        val size = value("size_ml")
        val barcodeType = value("barcode_type")
        val source = value("source_url")
        val notes = buildList {
            range?.let { add("Range: $it") }
            productCode?.let { add("Product code: $it") }
            variant?.let { add("Variant: $it") }
            size?.let { add("Size: $it ml") }
            barcodeType?.let { add("Barcode type: $it") }
            source?.let { add("Source: $it") }
        }.joinToString("\n").ifBlank { null }

        val stableId = UUID.nameUUIDFromBytes("miniature-paint:$barcode".toByteArray(Charsets.UTF_8)).toString()
        return InventoryItem(
            name = name,
            barcode = barcode,
            brand = value("brand"),
            category = value("product_type") ?: range,
            imageUrl = value("image_url"),
            notes = notes,
            quantity = 1,
            syncId = stableId,
            syncPending = false,
            isCatalogItem = true
        )
    }

    private var headerFields: List<String>? = null

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishField() {
            row += field.toString()
            field.setLength(0)
        }

        fun finishRow() {
            finishField()
            rows += row.toList()
            row.clear()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && inQuotes && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> finishField()
                (char == '\n' || char == '\r') && !inQuotes -> {
                    finishRow()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        if (rows.isEmpty()) return emptyList()
        val cleanHeader = rows.first().toMutableList()
        cleanHeader[0] = cleanHeader[0].removePrefix("\uFEFF")
        headerFields = cleanHeader
        return rows
    }

    companion object {
        private const val TAG = "CatalogCsvSeeder"
        private const val KEY_VERSION = "miniature_paint_catalog_version"
        private const val CATALOG_VERSION = 1
        private const val ASSET_PATH = "catalogs/miniature_paint_catalog.csv"
    }
}
