package com.example.data.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.R
import com.example.data.local.PrimeStoreDatabase
import com.example.data.local.TranslationEntity
import com.example.data.repository.SettingsRepository
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume

sealed class TranslationOutcome {
    data class Success(
        val translatedText: String,
        val sourceLangTag: String,
        val targetLangTag: String
    ) : TranslationOutcome()

    data object AlreadyInTargetLanguage : TranslationOutcome()

    data class Error(
        val reason: String,
        val isWifiBlocked: Boolean = false
    ) : TranslationOutcome()
}

object MlKitTranslatorHelper {

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.4f)
                .build()
        )
    }

    suspend fun translateField(
        context: Context,
        appId: String,
        field: String,
        originalText: String,
        targetLanguageCode: String,
        wifiOnly: Boolean = false
    ): TranslationOutcome {
        val trimmed = originalText.trim()
        if (trimmed.isBlank()) return TranslationOutcome.AlreadyInTargetLanguage

        val normalizedTarget = normalizeMlKitTag(targetLanguageCode)
        val targetMlKitLang = TranslateLanguage.fromLanguageTag(normalizedTarget)
            ?: return TranslationOutcome.Error(
                reason = context.getString(R.string.translation_error_msg),
                isWifiBlocked = false
            )

        val db = PrimeStoreDatabase.getInstance(context)
        val textHash = md5("$normalizedTarget::$trimmed")

        // 1. Check local Room cache
        val cached = withContext(Dispatchers.IO) {
            db.translationDao().getTranslation(appId, field, normalizedTarget, textHash)
        }
        if (cached != null && cached.translatedText.isNotBlank()) {
            return TranslationOutcome.Success(
                translatedText = cached.translatedText,
                sourceLangTag = cached.sourceLang.ifBlank { "auto" },
                targetLangTag = normalizedTarget
            )
        }

        // 2. Split into paragraphs by newline & identify language per paragraph
        val lines = originalText.split("\n")
        val identifiedSources = ArrayList<String>(lines.size)
        for (line in lines) {
            val clean = line.trim()
            if (clean.isBlank()) {
                identifiedSources.add("und")
            } else {
                val detected = normalizeMlKitTag(identifyLanguage(clean))
                identifiedSources.add(detected)
            }
        }

        val translatableLangs = identifiedSources.filter {
            it != "und" && it != normalizedTarget && TranslateLanguage.fromLanguageTag(it) != null
        }
        if (translatableLangs.isEmpty()) {
            // Also check whole text if individual short lines returned "und"
            val wholeDetected = normalizeMlKitTag(identifyLanguage(trimmed))
            if (wholeDetected == "und" || wholeDetected == normalizedTarget || TranslateLanguage.fromLanguageTag(wholeDetected) == null) {
                return TranslationOutcome.AlreadyInTargetLanguage
            }
            for (i in identifiedSources.indices) {
                if (lines[i].isNotBlank() && identifiedSources[i] == "und") {
                    identifiedSources[i] = wholeDetected
                }
            }
        }

        val dominantSource = identifiedSources
            .filter { it != "und" && it != normalizedTarget && TranslateLanguage.fromLanguageTag(it) != null }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: return TranslationOutcome.AlreadyInTargetLanguage

        return withContext(Dispatchers.IO) {
            val translatorCache = HashMap<String, Translator>()
            try {
                // Check Wi-Fi requirement upfront for needed remote models
                val neededSources = identifiedSources
                    .filter { it != "und" && it != normalizedTarget && TranslateLanguage.fromLanguageTag(it) != null }
                    .toSet()

                for (srcTag in neededSources) {
                    val srcMlKit = TranslateLanguage.fromLanguageTag(srcTag) ?: continue
                    val missingSource = !isModelDownloaded(srcMlKit)
                    val missingTarget = !isModelDownloaded(targetMlKitLang)
                    if ((missingSource || missingTarget) && wifiOnly && !isOnWifi(context)) {
                        return@withContext TranslationOutcome.Error(
                            reason = context.getString(R.string.translation_wifi_blocked_msg),
                            isWifiBlocked = true
                        )
                    }
                }

                val conditions = DownloadConditions.Builder().build()
                val outputLines = ArrayList<String>(lines.size)
                var anyTranslated = false

                for (i in lines.indices) {
                    val rawLine = lines[i]
                    val srcTag = identifiedSources[i]
                    if (rawLine.isBlank() || srcTag == "und" || srcTag == normalizedTarget) {
                        outputLines.add(rawLine)
                        continue
                    }

                    val srcMlKit = TranslateLanguage.fromLanguageTag(srcTag)
                    if (srcMlKit == null || srcMlKit == targetMlKitLang) {
                        outputLines.add(rawLine)
                        continue
                    }

                    val translator = translatorCache.getOrPut(srcMlKit) {
                        val options = TranslatorOptions.Builder()
                            .setSourceLanguage(srcMlKit)
                            .setTargetLanguage(targetMlKitLang)
                            .build()
                        Translation.getClient(options)
                    }

                    val ready = downloadModel(translator, conditions)
                    if (!ready) {
                        return@withContext TranslationOutcome.Error(
                            reason = context.getString(R.string.translation_error_msg),
                            isWifiBlocked = false
                        )
                    }

                    val chunks = chunkParagraph(rawLine, maxChars = 400)
                    val translatedChunks = chunks.map { chunk ->
                        performTranslation(translator, chunk)
                    }
                    val joinedLine = translatedChunks.joinToString(" ").trim()
                    if (joinedLine.isNotBlank() && joinedLine != rawLine) {
                        anyTranslated = true
                        outputLines.add(joinedLine)
                    } else {
                        outputLines.add(rawLine)
                    }
                }

                val finalTranslated = outputLines.joinToString("\n").trim()
                if (!anyTranslated || finalTranslated.isBlank()) {
                    return@withContext TranslationOutcome.AlreadyInTargetLanguage
                }

                db.translationDao().saveTranslation(
                    TranslationEntity(
                        appId = appId,
                        field = field,
                        targetLang = normalizedTarget,
                        sourceLang = dominantSource,
                        textHash = textHash,
                        translatedText = finalTranslated
                    )
                )

                TranslationOutcome.Success(
                    translatedText = finalTranslated,
                    sourceLangTag = dominantSource,
                    targetLangTag = normalizedTarget
                )
            } catch (e: Exception) {
                TranslationOutcome.Error(
                    reason = e.message ?: context.getString(R.string.translation_error_msg),
                    isWifiBlocked = false
                )
            } finally {
                translatorCache.values.forEach {
                    try { it.close() } catch (_: Exception) {}
                }
            }
        }
    }

    fun getLanguageDisplayName(langTag: String, uiLocale: Locale = Locale.getDefault()): String {
        val clean = normalizeMlKitTag(langTag)
        if (clean == "auto" || clean == "und" || clean.isBlank()) return ""
        val locale = Locale.forLanguageTag(clean)
        val display = locale.getDisplayLanguage(uiLocale)
        return if (display.isNullOrBlank()) clean.uppercase() else display.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(uiLocale) else it.toString()
        }
    }

    private fun normalizeMlKitTag(tag: String): String {
        val clean = tag.trim().lowercase().substringBefore("-").substringBefore("_")
        return when (clean) {
            "", "und" -> "und"
            "in", "id" -> "id"
            "auto" -> SettingsRepository.resolveDeviceLanguage()
            else -> clean
        }
    }

    private fun chunkParagraph(text: String, maxChars: Int = 400): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + maxChars).coerceAtMost(text.length)
            if (end == text.length) {
                result.add(text.substring(start, end))
                break
            }
            val slice = text.substring(start, end)
            val sentenceBreak = maxOf(
                slice.lastIndexOf(". "),
                slice.lastIndexOf("؟ "),
                slice.lastIndexOf("! "),
                slice.lastIndexOf("، ")
            )
            val splitAt = when {
                sentenceBreak > maxChars / 3 -> start + sentenceBreak + 1
                slice.lastIndexOf(' ') > maxChars / 3 -> start + slice.lastIndexOf(' ')
                else -> end
            }
            result.add(text.substring(start, splitAt).trim())
            start = splitAt
            while (start < text.length && text[start] == ' ') start++
        }
        return result.filter { it.isNotBlank() }
    }

    private fun isOnWifi(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun isModelDownloaded(mlKitLang: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val model = TranslateRemoteModel.Builder(mlKitLang).build()
            RemoteModelManager.getInstance().isModelDownloaded(model)
                .addOnSuccessListener { downloaded ->
                    if (cont.isActive) cont.resume(downloaded)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(false)
                }
                .addOnCanceledListener {
                    if (cont.isActive) cont.resume(false)
                }
        }

    private suspend fun identifyLanguage(text: String): String =
        suspendCancellableCoroutine { cont ->
            languageIdentifier.identifyLanguage(text.take(300))
                .addOnSuccessListener { languageCode ->
                    if (cont.isActive) cont.resume(languageCode)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume("und")
                }
                .addOnCanceledListener {
                    if (cont.isActive) cont.resume("und")
                }
        }

    private suspend fun downloadModel(translator: Translator, conditions: DownloadConditions): Boolean =
        suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(true)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(false)
                }
                .addOnCanceledListener {
                    if (cont.isActive) cont.resume(false)
                }
        }

    private suspend fun performTranslation(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    if (cont.isActive) cont.resume(translatedText)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(text)
                }
                .addOnCanceledListener {
                    if (cont.isActive) cont.resume(text)
                }
        }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }
}
