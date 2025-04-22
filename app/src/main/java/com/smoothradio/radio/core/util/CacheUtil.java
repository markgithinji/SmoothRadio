package com.smoothradio.radio.core.util;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CacheUtil {

    /**
     * Deletes the application cache directory (and recreates it)
     * @param context Application context
     */
    public static void clearAppCache(Context context) {
        try {
            File cacheDir = context.getCacheDir();
            File externalCacheDir = context.getExternalCacheDir();

            // Delete internal cache
            deleteDirectory(cacheDir);

            // Delete external cache if available
            if (externalCacheDir != null) {
                deleteDirectory(externalCacheDir);
            }

            // Recreate empty cache directories
            cacheDir.mkdirs();
            if (externalCacheDir != null) {
                externalCacheDir.mkdirs();
            }
        } catch (Exception e) {
            // Consider using a proper logging system
            e.printStackTrace();
        }
    }

    /**
     * Recursively deletes a directory and its contents
     * @param directory Directory to delete
     * @return true if successfully deleted
     */
    private static boolean deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return false;
        }

        // Use NIO Files.walk for better performance on Android 8.0+ (API 26+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                Path path = Paths.get(directory.getAbsolutePath());
                Files.walk(path)
                        .sorted((a, b) -> b.compareTo(a)) // reverse order to delete files first
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                // Log or handle specific file deletion failure
                            }
                        });
                return true;
            } catch (IOException e) {
                return false;
            }
        } else {
            // Fallback to traditional method for older devices
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return directory.delete();
        }
    }
}