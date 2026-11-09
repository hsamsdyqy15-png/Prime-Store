package com.example.data.installer

import android.content.Context
import android.net.Uri
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
            return detectAbisFromFile(tempFile)
        } catch (e: Exception) {
            return "Universal (All Architectures)"
        } finally {
            try {
                tempFile?.delete()
            } catch (ignored: Exception) {}
        }
    }

    fun detectAbisFromFile(apkFile: File): String {
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
                    return "Universal (Pure Java/Kotlin)"
                }
                val hasArm64 = foundAbis.contains("arm64-v8a")
                val hasArmeabi = foundAbis.contains("armeabi-v7a")
                val hasX86 = foundAbis.contains("x86")
                val hasX86_64 = foundAbis.contains("x86_64")

                return when {
                    foundAbis.size >= 4 -> "Universal / All Architectures (arm64, armv7, x86, x64)"
                    hasArm64 && hasArmeabi -> "arm64-v8a + armeabi-v7a (Universal Mobile)"
                    hasArm64 -> "arm64-v8a only (Modern 64-bit)"
                    hasArmeabi -> "armeabi-v7a only (32-bit ARM)"
                    else -> foundAbis.joinToString(", ")
                }
            }
        } catch (e: Exception) {
            return "Universal (All Architectures)"
        }
    }
}
