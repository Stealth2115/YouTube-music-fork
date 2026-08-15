package com.agon.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade

/**
 * Configures the shared Coil image loader with explicit, bounded caches.
 *
 * Album art is by far the largest heap consumer in the app, and YouTube artwork is
 * fetched over the network, so both a capped memory cache and a small disk cache are
 * set up here instead of relying on Coil's defaults.
 */
class AgonApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    // ~15% of the heap: plenty for on-screen artwork, small enough to
                    // stay well clear of an OOM on low-memory devices.
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                // Bounded on-disk cache so repeated YouTube searches don't re-download
                // artwork, without letting storage grow without limit.
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork"))
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .build()
}
