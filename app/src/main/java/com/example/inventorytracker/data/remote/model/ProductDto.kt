package com.example.inventorytracker.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProductDto(
    @field:Json(name = "product_name") val productName: String? = null,
    @field:Json(name = "product_name_en") val productNameEn: String? = null,
    @field:Json(name = "generic_name") val genericName: String? = null,
    @field:Json(name = "brands") val brands: String? = null,
    @field:Json(name = "image_front_url") val imageFrontUrl: String? = null,
    @field:Json(name = "image_url") val imageUrl: String? = null,
    @field:Json(name = "categories") val categories: String? = null,
    @field:Json(name = "quantity") val quantity: String? = null
) {
    fun getDisplayName(): String? {
        return productName?.takeIf { it.isNotBlank() }
            ?: productNameEn?.takeIf { it.isNotBlank() }
            ?: genericName?.takeIf { it.isNotBlank() }
    }

    fun getBestImageUrl(): String? {
        return imageFrontUrl?.takeIf { it.isNotBlank() }
            ?: imageUrl?.takeIf { it.isNotBlank() }
    }
}
