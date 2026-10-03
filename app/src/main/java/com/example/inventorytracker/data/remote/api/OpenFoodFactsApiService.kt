package com.example.inventorytracker.data.remote.api

import com.example.inventorytracker.data.remote.model.OpenFoodFactsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApiService {

    @GET("api/v3/product/{barcode}")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "code,product_name,product_name_en,generic_name,brands,categories,image_front_url,image_url,quantity"
    ): Response<OpenFoodFactsResponse>
}
