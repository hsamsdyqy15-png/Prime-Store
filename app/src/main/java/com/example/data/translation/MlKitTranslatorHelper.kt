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

sealed class TranslationStage {
    data object Identifying : TranslationStage()
    data class DownloadingModel(val sourceLangTag: String, val targetLangTag: String) : TranslationStage()
    data object Translating : TranslationStage()
}

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
        wifiOnly: Boolean = false,
        onStageChanged: ((TranslationStage) -> Unit)? = null
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

        // 2. Split into lines, separating leading emoji/bullet prefixes from actual text
        onStageChanged?.invoke(TranslationStage.Identifying)
        val rawLines = originalText.split("\n")
        val splitLines = rawLines.map { splitLeadingPrefix(it) }
        val identifiedSources = ArrayList<String>(splitLines.size)
        for ((_, body) in splitLines) {
            if (body.isBlank()) {
                identifiedSources.add("und")
            } else {
                val detected = normalizeMlKitTag(identifyLanguage(body))
                identifiedSources.add(detected)
            }
        }

        val translatableLangs = identifiedSources.filter {
            it != "und" && it != normalizedTarget && TranslateLanguage.fromLanguageTag(it) != null
        }
        if (translatableLangs.isEmpty()) {
            val wholeBody = splitLines.map { it.second }.filter { it.isNotBlank() }.joinToString(" ")
            val wholeDetected = if (wholeBody.isNotBlank()) normalizeMlKitTag(identifyLanguage(wholeBody)) else "und"
            if (wholeDetected == "und" || wholeDetected == normalizedTarget || TranslateLanguage.fromLanguageTag(wholeDetected) == null) {
                return TranslationOutcome.AlreadyInTargetLanguage
            }
            for (i in identifiedSources.indices) {
                if (splitLines[i].second.isNotBlank() && identifiedSources[i] == "und") {
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
                val outputLines = ArrayList<String>(rawLines.size)
                var anyTranslated = false

                for (i in rawLines.indices) {
                    val rawLine = rawLines[i]
                    val (prefix, body) = splitLines[i]
                    val srcTag = identifiedSources[i]
                    if (body.isBlank() || srcTag == "und" || srcTag == normalizedTarget) {
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

                    if (!isModelDownloaded(srcMlKit) || !isModelDownloaded(targetMlKitLang)) {
                        onStageChanged?.invoke(TranslationStage.DownloadingModel(srcTag, normalizedTarget))
                    }
                    val ready = downloadModel(translator, conditions)
                    if (!ready) {
                        return@withContext TranslationOutcome.Error(
                            reason = context.getString(R.string.translation_error_msg),
                            isWifiBlocked = false
                        )
                    }

                    onStageChanged?.invoke(TranslationStage.Translating)
                    val chunks = chunkParagraph(body, maxChars = 400)
                    val translatedChunks = chunks.map { chunk ->
                        performTranslation(translator, chunk)
                    }
                    val joinedBody = translatedChunks.joinToString(" ").trim()
                    if (joinedBody.isNotBlank() && joinedBody != body) {
                        anyTranslated = true
                        outputLines.add(prefix + joinedBody)
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

    private fun splitLeadingPrefix(line: String): Pair<String, String> {
        var idx = 0
        while (idx < line.length) {
            val cp = line.codePointAt(idx)
            if (Character.isLetterOrDigit(cp)) {
                break
            }
            idx += Character.charCount(cp)
        }
        return line.substring(0, idx) to line.substring(idx).trim()
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
