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

/**
 * Data source factory used by the shared ExoPlayer: local content keeps going through
 * [DefaultDataSource] exactly as before, while `ytstream://` items are resolved first.
 * The HTTP source uses media3's default user agent and no extra headers, matching the
 * profile the working InnerTune forks use to fetch googlevideo stream URLs.
 */
fun youTubeAwareDataSourceFactory(
    context: Context,
    repository: YouTubeRepository,
): DataSource.Factory {
    val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(12_000)
        .setReadTimeoutMs(15_000)
    val base = DefaultDataSource.Factory(context, http)
    return ResolvingDataSource.Factory(base, YouTubeStreamResolver(repository))
}
