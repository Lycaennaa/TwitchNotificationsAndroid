package com.lycaennaa.twitchalerts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal object TwitchAvatarLoader {
    private const val CDN_HOST = "static-cdn.jtvnw.net"
    private const val CONNECT_TIMEOUT_MILLIS = 3_000
    private const val READ_TIMEOUT_MILLIS = 3_000
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_IMAGE_DIMENSION = 512
    private const val BUFFER_BYTES = 8 * 1024

    fun load(profileImageUrl: String?): Bitmap? {
        if (profileImageUrl.isNullOrBlank()) return null
        val url = try {
            URL(profileImageUrl)
        } catch (_: IOException) {
            return null
        }
        if (url.protocol != "https" || !url.host.equals(CDN_HOST, ignoreCase = true)) return null

        val connection = try {
            url.openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return null
        }
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > MAX_IMAGE_BYTES) return null
            val imageBytes = connection.inputStream.use(::readBounded) ?: return null
            decodeAvatar(imageBytes)
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var totalBytes = 0
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            if (bytesRead == 0) continue
            if (totalBytes > MAX_IMAGE_BYTES - bytesRead) return null
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }
        return output.toByteArray()
    }

    private fun decodeAvatar(imageBytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    }
}
