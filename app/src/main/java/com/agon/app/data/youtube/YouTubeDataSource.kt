package com.agon.app.data.youtube

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import java.io.IOException

/**
 * Resolves `ytstream://<videoId>` data specs to a real progressive audio URL just
 * before the player opens them, on ExoPlayer's own loading thread (never the UI thread).
 *
 * Keeping the opaque `ytstream://` URI in the media item means queue persistence,
 * shuffle, repeat and notification controls all keep working unchanged, while the
 * short-lived signed URL is fetched (and refreshed after expiry) transparently.
 */
class YouTubeStreamResolver(private val repository: YouTubeRepository) : ResolvingDataSource.Resolver {

    @Throws(IOException::class)
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != YT_SCHEME) return dataSpec
        val videoId = (uri.host ?: uri.schemeSpecificPart?.trimStart('/'))
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Invalid YouTube stream reference")

        return when (val result = repository.resolveStreamBlocking(videoId)) {
            is StreamResult.Success -> dataSpec.withUri(android.net.Uri.parse(result.url))
            is StreamResult.Unavailable -> throw YouTubeUnavailableException(result.reason)
        }
    }
}

/** Signals content that is unavailable, region-locked, age-restricted or removed. */
class YouTubeUnavailableException(message: String) : IOException(message)

/** Browser UA used when downloading resolved googlevideo stream URLs. */
const val STREAM_FETCH_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/18.0 Safari/605.1.15"

/**
 * Data source factory used by the shared ExoPlayer: local content keeps going through
 * [DefaultDataSource] exactly as before, while `ytstream://` items are resolved first.
 * The HTTP data source sends a browser UA plus the YouTube Music origin/referer, which
 * the googlevideo CDN expects for these signed stream URLs.
 */
fun youTubeAwareDataSourceFactory(
    context: Context,
    repository: YouTubeRepository,
): DataSource.Factory {
    val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(12_000)
        .setReadTimeoutMs(15_000)
        .setUserAgent(STREAM_FETCH_UA)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://music.youtube.com/",
                "Origin" to "https://music.youtube.com",
            )
        )
    val base = DefaultDataSource.Factory(context, http)
    return ResolvingDataSource.Factory(base, YouTubeStreamResolver(repository))
}
