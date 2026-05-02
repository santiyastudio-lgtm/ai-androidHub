package com.santiya.localaihub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalClientApiModelsTest {
    @Test
    fun parseApproximateSizeBytes_supportsGigabytes() {
        val bytes = parseApproximateSizeBytes("3.5 GB")

        assertEquals((3.5 * 1024 * 1024 * 1024).toLong(), bytes)
    }

    @Test
    fun mapDownloadStatusResponse_translatesDownloadingState() {
        val response = mapDownloadStatusResponse(
            state = ModelDownloadService.DownloadState.Downloading(
                modelId = "vendor/chat-model",
                progress = 0.25f,
                downloadedBytes = 256,
                totalBytes = 1024,
            ),
            installed = false,
            selected = true,
            installedBytes = 0,
        )

        assertEquals("downloading", response.status)
        assertEquals(25.0f, response.progressPercent)
        assertEquals(256L, response.bytesDownloaded)
        assertTrue(response.selected)
    }

    @Test
    fun mapDownloadStatusResponse_reportsInstalledModelAsReadyWhenNoTransientStateExists() {
        val response = mapDownloadStatusResponse(
            state = null,
            installed = true,
            selected = false,
            installedBytes = 2048,
        )

        assertEquals("ready", response.status)
        assertEquals(100.0f, response.progressPercent)
        assertEquals(2048L, response.totalBytes)
        assertEquals("Installed in AI Hub", response.message)
    }
}
