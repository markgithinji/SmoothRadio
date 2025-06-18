package com.smoothradio.radio.core.util

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

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
            Timber.e(e, "Failed to clear app cache")
        }
    }

    /**
     * Deletes a directory and its contents recursively.
     *
     * This function attempts to delete the specified directory and all its subdirectories and files.
     * It uses the newer NIO APIs on Android O (API level 26) and above for potentially better
     * performance and error handling. For older Android versions, it falls back to a legacy
     * recursive deletion method.
     *
     * @param directory The [File] object representing the directory to delete.
     *                  If the directory is null or does not exist, the function will return `false`.
     * @return `true` if the directory and its contents were successfully deleted, `false` otherwise.
     *         Returns `false` if the input `directory` is null, does not exist, or if any part of the
     *         deletion process fails (e.g., due to file permissions or files being in use).
     */
    private fun deleteDirectory(directory: File?): Boolean {
        if (directory == null || !directory.exists()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deleteDirectoryUsingNio(directory)
        } else {
            deleteDirectoryLegacy(directory)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deleteDirectoryUsingNio(directory: File): Boolean {
        return try {
            val path = directory.toPath()
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach { deletePath(it) }
            true
        } catch (e: IOException) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deletePath(path: Path) {
        try {
            Files.delete(path)
        } catch (e: IOException) {
            Timber.e(e, "Failed to delete path: $path")
        }
    }

    private fun deleteDirectoryLegacy(directory: File): Boolean {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectoryLegacy(file)
            } else {
                file.delete()
            }
        }
        return directory.delete()
    }
}
