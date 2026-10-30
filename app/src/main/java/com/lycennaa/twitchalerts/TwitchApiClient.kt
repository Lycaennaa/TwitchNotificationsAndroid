package com.lycaennaa.twitchalerts

import android.net.Uri
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal data class TwitchSession(
    val accessToken: String,
    val clientId: String,
    val userId: String,
)

internal data class TwitchStream(
    val id: String,
    val userId: String,
    val userLogin: String,
    val userName: String,
    val title: String,
    val gameName: String,
    val profileImageUrl: String? = null,
) {
    val displayName: String
        get() = userName.ifBlank { userLogin }
}


internal fun attachProfileImages(
    streams: List<TwitchStream>,
    profileImagesByUserId: Map<String, String>,
): List<TwitchStream> = streams.map { stream ->
    profileImagesByUserId[stream.userId]?.let { imageUrl ->
        stream.copy(profileImageUrl = imageUrl)
    } ?: stream
}

internal class TwitchApiException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        INVALID_TOKEN,
        MISSING_SCOPE,
        NETWORK,
        RATE_LIMITED,
        SERVER,
        INVALID_RESPONSE,
        TOO_MANY_STREAMS,
    }
}

internal class TwitchApiClient {
    fun authenticate(rawToken: String): TwitchSession {
        val token = normalizeToken(rawToken)
        if (token.isBlank()) {
            throw TwitchApiException(TwitchApiException.Kind.INVALID_TOKEN, "Enter a Twitch OAuth token.")
        }

        val validation = getJson(
            "https://id.twitch.tv/oauth2/validate",
            mapOf("Authorization" to "OAuth $token"),
        )
        val scopes = validation.optJSONArray("scopes")
        val hasFollowScope = scopes != null && (0 until scopes.length()).any {
            scopes.optString(it) == REQUIRED_SCOPE
        }
        if (!hasFollowScope) {
            throw TwitchApiException(
                TwitchApiException.Kind.MISSING_SCOPE,
                "The OAuth token needs the user:read:follows scope.",
            )
        }

        val clientId = validation.optString("client_id")
        val userId = validation.optString("user_id")
        if (clientId.isBlank() || userId.isBlank()) {
            throw TwitchApiException(
                TwitchApiException.Kind.INVALID_RESPONSE,
                "Twitch returned incomplete token information.",
            )
        }
        return TwitchSession(token, clientId, userId)
    }

    fun followedStreams(session: TwitchSession): List<TwitchStream> {
        val streams = LinkedHashMap<String, TwitchStream>()
        val seenCursors = HashSet<String>()
        var cursor: String? = null

        repeat(MAXIMUM_PAGES) {
            val uri = Uri.parse("https://api.twitch.tv/helix/streams/followed")
                .buildUpon()
                .appendQueryParameter("user_id", session.userId)
                .appendQueryParameter("first", PAGE_SIZE.toString())
                .apply { cursor?.let { appendQueryParameter("after", it) } }
                .build()

            val page = getJson(
                uri.toString(),
                mapOf(
                    "Authorization" to "Bearer ${session.accessToken}",
                    "Client-ID" to session.clientId,
                ),
            )
            val data = page.optJSONArray("data")
                ?: throw invalidResponse("Twitch returned an invalid followed-stream list.")
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index)
                    ?: throw invalidResponse("Twitch returned an invalid followed-stream entry.")
                val stream = TwitchStream(
                    id = item.optString("id"),
                    userId = item.optString("user_id"),
                    userLogin = item.optString("user_login"),
                    userName = item.optString("user_name"),
                    title = item.optString("title"),
                    gameName = item.optString("game_name"),
                )
                if (stream.id.isBlank() || stream.userId.isBlank()) {
                    throw invalidResponse("Twitch returned an incomplete followed-stream entry.")
                }
                streams.putIfAbsent(stream.id, stream)
            }

            val nextCursor = page.optJSONObject("pagination")
                ?.optString("cursor")
                ?.takeIf(String::isNotBlank)
            if (nextCursor == null) return streams.values.toList()
            if (!seenCursors.add(nextCursor)) {
                throw invalidResponse("Twitch returned a repeated pagination cursor.")
            }
            cursor = nextCursor
        }

        throw TwitchApiException(
            TwitchApiException.Kind.TOO_MANY_STREAMS,
            "The followed-stream list is larger than the supported limit.",
        )
    }

    fun profileImageUrls(
        session: TwitchSession,
        userIds: Collection<String>,
    ): Map<String, String> {
        val uniqueUserIds = userIds.filter(String::isNotBlank).distinct()
        if (uniqueUserIds.isEmpty()) return emptyMap()

        val profileImagesByUserId = HashMap<String, String>()
        for (userIdBatch in uniqueUserIds.chunked(PAGE_SIZE)) {
            val uri = Uri.parse("https://api.twitch.tv/helix/users")
                .buildUpon()
                .apply { userIdBatch.forEach { appendQueryParameter("id", it) } }
                .build()
            val response = getJson(
                uri.toString(),
                mapOf(
                    "Authorization" to "Bearer ${session.accessToken}",
                    "Client-ID" to session.clientId,
                ),
            )
            val users = response.optJSONArray("data") ?: continue
            for (index in 0 until users.length()) {
                val user = users.optJSONObject(index) ?: continue
                val userId = user.optString("id")
                val profileImageUrl = user.optString("profile_image_url")
                if (userId.isNotBlank() && profileImageUrl.isNotBlank()) {
                    profileImagesByUserId[userId] = profileImageUrl
                }
            }
        }
        return profileImagesByUserId
    }

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (exception: IOException) {
            throw TwitchApiException(
                TwitchApiException.Kind.NETWORK,
                networkFailureMessage(exception),
                exception,
            )
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
            connection.readTimeout = REQUEST_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBytes = responseStream?.use(::readBounded) ?: ByteArray(0)
            if (statusCode !in 200..299) throw httpError(statusCode)
            return JSONObject(String(responseBytes, StandardCharsets.UTF_8))
        } catch (exception: TwitchApiException) {
            throw exception
        } catch (exception: JSONException) {
            throw invalidResponse("Twitch returned an invalid response.", exception)
        } catch (exception: IOException) {
            throw TwitchApiException(
                TwitchApiException.Kind.NETWORK,
                networkFailureMessage(exception),
                exception,
            )
        } finally {
            connection.disconnect()
        }
    }
    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var totalBytes = 0
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            if (totalBytes > MAXIMUM_RESPONSE_BYTES - bytesRead) {
                throw invalidResponse("Twitch response exceeded the supported size.")
            }
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }
        return output.toByteArray()
    }

    private fun httpError(statusCode: Int): TwitchApiException = when (statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> TwitchApiException(
            TwitchApiException.Kind.INVALID_TOKEN,
            "The OAuth token is invalid or expired.",
        )
        HttpURLConnection.HTTP_FORBIDDEN -> TwitchApiException(
            TwitchApiException.Kind.MISSING_SCOPE,
            "The OAuth token needs the user:read:follows scope.",
        )
        HTTP_TOO_MANY_REQUESTS -> TwitchApiException(
            TwitchApiException.Kind.RATE_LIMITED,
            "Twitch API rate limit reached.",
        )
        in 500..599 -> TwitchApiException(
            TwitchApiException.Kind.SERVER,
            "Twitch API returned HTTP $statusCode.",
        )
        else -> invalidResponse("Twitch API returned HTTP $statusCode.")
    }

    private fun invalidResponse(message: String, cause: Throwable? = null) =
        TwitchApiException(TwitchApiException.Kind.INVALID_RESPONSE, message, cause)

    companion object {
        const val REQUIRED_SCOPE = "user:read:follows"
        private const val PAGE_SIZE = 100
        private const val MAXIMUM_PAGES = 100
        private const val REQUEST_TIMEOUT_MILLIS = 15_000
        private const val MAXIMUM_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val BUFFER_BYTES = 8 * 1024
        private const val HTTP_TOO_MANY_REQUESTS = 429

        fun normalizeToken(rawToken: String): String {
            val token = rawToken.trim()
            return if (token.startsWith("oauth:", ignoreCase = true)) {
                token.substring("oauth:".length).trim()
            } else {
                token
            }
        }

        fun networkFailureMessage(exception: IOException): String {
            val detail = exception.message
                ?.takeIf(String::isNotBlank)
                ?.take(160)
                ?: exception.javaClass.simpleName
            return "Could not reach Twitch: $detail."
        }
    }
}
