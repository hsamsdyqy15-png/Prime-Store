package com.example.data.installer

import android.content.Context
import android.net.Uri
import com.example.R
import java.io.File
import java.util.zip.ZipFile

object ApkAbiDetector {
    fun detectAbis(context: Context, apkUri: Uri): String {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("inspect_apk", ".apk", context.cacheDir)
            context.contentResolver.openInputStream(apkUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return detectAbisFromFile(tempFile, context)
        } catch (e: Exception) {
            return context.getString(R.string.abi_universal_all)
        } finally {
            try {
                tempFile?.delete()
            } catch (_: Exception) {}
        }
    }

    fun detectAbisFromFile(apkFile: File, context: Context? = null): String {
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                val foundAbis = mutableSetOf<String>()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.startsWith("lib/")) {
                        val parts = name.split("/")
                        if (parts.size >= 2) {
                            val abi = parts[1]
                            if (abi.isNotBlank()) {
                                foundAbis.add(abi)
                            }
                        }
                    }
                }
                if (foundAbis.isEmpty()) {
                    return context?.getString(R.string.abi_universal_java) ?: "Universal (Pure Java/Kotlin)"
                }
                val hasArm64 = foundAbis.contains("arm64-v8a")
                val hasArmeabi = foundAbis.contains("armeabi-v7a")

                return when {
                    foundAbis.size >= 4 -> context?.getString(R.string.abi_universal_all) ?: "Universal (All Architectures)"
                    hasArm64 && hasArmeabi -> context?.getString(R.string.abi_arm64_armv7) ?: "arm64-v8a + armeabi-v7a (Universal Mobile)"
                    hasArm64 -> context?.getString(R.string.abi_arm64_only) ?: "arm64-v8a only (64-bit)"
                    hasArmeabi -> context?.getString(R.string.abi_armeabi_only) ?: "armeabi-v7a only (32-bit ARM)"
                    else -> foundAbis.joinToString(", ")
                }
            }
        } catch (e: Exception) {
            return context?.getString(R.string.abi_universal_all) ?: "Universal (All Architectures)"
        }
    }
}
