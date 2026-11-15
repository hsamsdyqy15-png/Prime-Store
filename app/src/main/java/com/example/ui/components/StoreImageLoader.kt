package com.example.ui.components

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

object StoreImageLoader {
    @Volatile
    private var INSTANCE: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildLoader(context.applicationContext).also { INSTANCE = it }
        }
    }

    private fun buildLoader(appContext: Context): ImageLoader {
        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "image_cache"))
                    .maxSizeBytes(50L * 1024L * 1024L)
                    .build()
            }
            .crossfade(150)
            .respectCacheHeaders(false)
            .build()
    }
}
