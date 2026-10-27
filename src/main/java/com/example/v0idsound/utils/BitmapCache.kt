package com.example.v0idsound.utils

import android.graphics.Bitmap
import androidx.collection.LruCache

class BitmapCache(maxSize: Int) {
    private val cache = LruCache<String, Bitmap>(maxSize) { key ->
        // Load bitmap from resources or disk
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.image)
        bitmap
    }

    fun getBitmap(key: String): Bitmap? {
        return cache[key]
    }

    fun putBitmap(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun removeBitmap(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }
}