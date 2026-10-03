package com.example.inventorytracker.data.remote

import com.example.inventorytracker.data.remote.api.OpenFoodFactsApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val FOOD_BASE_URL = "https://world.openfoodfacts.org/"
    private const val PRODUCTS_BASE_URL = "https://world.openproductsfacts.org/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "InventoryTracker - Android - Version 1.0")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun createApiService(baseUrl: String): OpenFoodFactsApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenFoodFactsApiService::class.java)
    }

    val apiService: OpenFoodFactsApiService by lazy { createApiService(FOOD_BASE_URL) }
    val productsApiService: OpenFoodFactsApiService by lazy { createApiService(PRODUCTS_BASE_URL) }
}
