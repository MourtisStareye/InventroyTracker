package com.example.inventorytracker.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenFoodFactsResponse(
    @field:Json(name = "code") val code: String? = null,
    @field:Json(name = "status") val status: String? = null,
    @field:Json(name = "status_verbose") val statusVerbose: String? = null,
    @field:Json(name = "product") val product: ProductDto? = null
)
