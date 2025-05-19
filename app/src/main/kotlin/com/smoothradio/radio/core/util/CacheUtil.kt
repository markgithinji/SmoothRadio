package com.smoothradio.radio.core.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object CacheUtil {

    /**
     * Deletes the application cache directory (and recreates it)
     * @param context Application context
     */
    fun clearAppCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val externalCacheDir = context.externalCacheDir

            // Delete internal cache
            deleteDirectory(cacheDir)

            // Delete external cache if available
            if (externalCacheDir != null) {
                deleteDirectory(externalCacheDir)
            }

            // Recreate empty cache directories
            cacheDir.mkdirs()
            externalCacheDir?.mkdirs()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Recursively deletes a directory and its contents
     * @param directory Directory to delete
     * @return true if successfully deleted
     */
    private fun deleteDirectory(directory: File?): Boolean {
        if (directory == null || !directory.exists()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val path: Path = Paths.get(directory.absolutePath)
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach { p ->
                        try {
                            Files.delete(p)
                        } catch (e: IOException) {
                            // Log or handle specific file deletion failure
                        }
                    }
                true
            } catch (e: IOException) {
                false
            }
        } else {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
            directory.delete()
        }
    }
}
