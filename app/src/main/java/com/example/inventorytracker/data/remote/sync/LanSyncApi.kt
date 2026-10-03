package com.example.inventorytracker.data.remote.sync

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface LanSyncApi {
    @GET
    suspend fun awaitDesktopSync(
        @Url endpoint: String,
        @Header("Authorization") authorization: String
    ): Response<DesktopSyncTrigger>

    @POST
    suspend fun sync(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: SyncRequest
    ): Response<SyncResponse>

    @POST
    suspend fun uploadPhoto(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body photo: PhotoTransfer
    ): Response<PhotoTransferResult>

    @GET
    suspend fun downloadPhoto(
        @Url endpoint: String,
        @Header("Authorization") authorization: String
    ): Response<PhotoTransferResult>
}
