package com.example.data.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.LruCache
import com.example.R
import com.example.data.local.PrimeStoreDatabase
import com.example.data.local.TranslationEntity
import com.example.data.repository.SettingsRepository
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
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

    private val requestMutexes = Array(16) { Mutex() }
    private val langIdInitLock = Any()

    @Volatile
    private var cachedLanguageIdentifier: LanguageIdentifier? = null

    @Volatile
    private var langIdUnavailable = false

    private class ManagedTranslator(val translator: Translator) {
        private val activeUses = AtomicInteger(0)
        @Volatile
        private var closed = false
        @Volatile
        private var evictRequested = false

        fun acquire(): Boolean {
            synchronized(this) {
                if (closed || evictRequested) return false
                activeUses.incrementAndGet()
                return true
            }
        }

        fun release() {
            val shouldClose = synchronized(this) {
                val remaining = activeUses.decrementAndGet()
                if (remaining <= 0 && evictRequested && !closed) {
                    closed = true
                    true
                } else {
                    false
                }
            }
            if (shouldClose) {
                runCatching { translator.close() }
            }
        }

        fun requestClose() {
            val shouldClose = synchronized(this) {
                evictRequested = true
                if (activeUses.get() <= 0 && !closed) {
                    closed = true
                    true
                } else {
                    false
                }
            }
            if (shouldClose) {
                runCatching { translator.close() }
            }
        }
    }

    private val translatorCache = object : LruCache<String, ManagedTranslator>(6) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: ManagedTranslator?,
            newValue: ManagedTranslator?
        ) {
            if (oldValue != null && oldValue !== newValue) {
                oldValue.requestClose()
            }
        }
    }

    fun clearCache() {
        synchronized(translatorCache) {
            runCatching { translatorCache.evictAll() }
        }
    }

    suspend fun translateField(
        context: Context,
        appId: String,
        field: String,
        originalText: String,
        targetLanguageCode: String,
        wifiOnly: Boolean = false,
        knownSourceHint: String? = null,
        onStageChanged: ((TranslationStage) -> Unit)? = null
    ): TranslationOutcome {
        val trimmed = originalText.trim()
        if (trimmed.isBlank()) return TranslationOutcome.AlreadyInTargetLanguage

        val normalizedTarget = normalizeMlKitTag(targetLanguageCode)
        val normalizedHint = knownSourceHint
            ?.let { normalizeMlKitTag(it) }
            ?.takeIf { it != "und" }

        // Fast pure-Kotlin pre-check before touching Room, ML Kit, or IO threads
        val fastWholeScript = detectScriptLanguage(trimmed)
        if (fastWholeScript == normalizedTarget) {
            return TranslationOutcome.AlreadyInTargetLanguage
        }
        if (fastWholeScript == null && normalizedHint == normalizedTarget && isPredominantlyLatin(trimmed)) {
            return TranslationOutcome.AlreadyInTargetLanguage
        }

        val lockIndex = ((appId.hashCode() * 31 + field.hashCode() * 17 + normalizedTarget.hashCode()) and 0x7FFFFFFF) % requestMutexes.size

        return withContext(Dispatchers.IO) {
            requestMutexes[lockIndex].withLock {
                try {
                    val targetMlKitLang = runCatching {
                        TranslateLanguage.fromLanguageTag(normalizedTarget)
                    }.getOrNull()
                        ?: return@withLock TranslationOutcome.Error(
                            reason = context.getString(R.string.translation_error_msg),
                            isWifiBlocked = false
                        )

                    val db = PrimeStoreDatabase.getInstance(context)
                    val textHash = md5("$normalizedTarget::$trimmed")

                    // 1. Check local Room cache
                    val cached = db.translationDao().getTranslation(appId, field, normalizedTarget, textHash)
                    if (cached != null && cached.translatedText.isNotBlank()) {
                        return@withLock TranslationOutcome.Success(
                            translatedText = cached.translatedText,
                            sourceLangTag = cached.sourceLang.ifBlank { "auto" },
                            targetLangTag = normalizedTarget
                        )
                    }

                    // 2. Split into lines and detect script in Kotlin first to avoid unnecessary native LanguageIdentification init
                    notifyStage(onStageChanged, TranslationStage.Identifying)
                    val rawLines = originalText.split("\n")
                    val splitLines = rawLines.map { splitLeadingPrefix(it) }
                    val identifiedSources = ArrayList<String>(splitLines.size)
                    val unresolvedLatinIndices = ArrayList<Int>()

                    for (i in splitLines.indices) {
                        val body = splitLines[i].second
                        if (body.isBlank()) {
                            identifiedSources.add("und")
                        } else {
                            val scriptLang = detectScriptLanguage(body)
                            if (scriptLang != null) {
                                identifiedSources.add(scriptLang)
                            } else if (normalizedHint != null && isPredominantlyLatin(body)) {
                                identifiedSources.add(normalizedHint)
                            } else {
                                identifiedSources.add("und")
                                unresolvedLatinIndices.add(i)
                            }
                        }
                    }

                    // Only initialize ML Kit LanguageIdentification if there are ambiguous Latin lines
                    if (unresolvedLatinIndices.isNotEmpty()) {
                        val sampleText = unresolvedLatinIndices
                            .asSequence()
                            .map { splitLines[it].second }
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .take(300)
                        val detectedSample = if (sampleText.isNotBlank()) {
                            normalizeMlKitTag(identifyLanguageSafely(sampleText))
                        } else {
                            "und"
                        }
                        if (detectedSample != "und") {
                            for (idx in unresolvedLatinIndices) {
                                identifiedSources[idx] = detectedSample
                            }
                        }
                    }

                    val supportedTranslatable = identifiedSources.filter { tag ->
                        tag != "und" &&
                            tag != normalizedTarget &&
                            runCatching { TranslateLanguage.fromLanguageTag(tag) }.getOrNull() != null
                    }

                    val dominantSource = supportedTranslatable
                        .groupingBy { it }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key
                        ?: return@withLock TranslationOutcome.AlreadyInTargetLanguage

                    val neededSources = supportedTranslatable.toSet()
                    for (srcTag in neededSources) {
                        val srcMlKit = runCatching { TranslateLanguage.fromLanguageTag(srcTag) }.getOrNull() ?: continue
                        val missingSource = !isModelDownloaded(srcMlKit)
                        val missingTarget = !isModelDownloaded(targetMlKitLang)
                        if ((missingSource || missingTarget) && wifiOnly && !isOnWifi(context)) {
                            return@withLock TranslationOutcome.Error(
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

                        val srcMlKit = runCatching { TranslateLanguage.fromLanguageTag(srcTag) }.getOrNull()
                        if (srcMlKit == null || srcMlKit == targetMlKitLang) {
                            outputLines.add(rawLine)
                            continue
                        }

                        val cacheKey = "${srcMlKit}->$targetMlKitLang"
                        val managed = acquireTranslator(cacheKey, srcMlKit, targetMlKitLang)
                            ?: return@withLock TranslationOutcome.Error(
                                reason = context.getString(R.string.translation_error_msg),
                                isWifiBlocked = false
                            )

                        try {
                            if (!isModelDownloaded(srcMlKit) || !isModelDownloaded(targetMlKitLang)) {
                                notifyStage(onStageChanged, TranslationStage.DownloadingModel(srcTag, normalizedTarget))
                            }
                            val ready = downloadModel(managed.translator, conditions)
                            if (!ready) {
                                return@withLock TranslationOutcome.Error(
                                    reason = context.getString(R.string.translation_error_msg),
                                    isWifiBlocked = false
                                )
                            }

                            notifyStage(onStageChanged, TranslationStage.Translating)
                            val chunks = chunkParagraph(body, maxChars = 400)
                            val translatedChunks = chunks.map { chunk ->
                                performTranslation(managed.translator, chunk)
                            }
                            val joinedBody = translatedChunks.joinToString(" ").trim()
                            if (joinedBody.isNotBlank() && joinedBody != body) {
                                anyTranslated = true
                                outputLines.add(prefix + joinedBody)
                            } else {
                                outputLines.add(rawLine)
                            }
                        } finally {
                            managed.release()
                        }
                    }

                    val finalTranslated = outputLines.joinToString("\n").trim()
                    if (!anyTranslated || finalTranslated.isBlank()) {
                        return@withLock TranslationOutcome.AlreadyInTargetLanguage
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
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    TranslationOutcome.Error(
                        reason = t.message?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.translation_error_msg),
                        isWifiBlocked = false
                    )
                }
            }
        }
    }

    private suspend fun notifyStage(
        onStageChanged: ((TranslationStage) -> Unit)?,
        stage: TranslationStage
    ) {
        if (onStageChanged != null) {
            withContext(Dispatchers.Main.immediate) {
                onStageChanged(stage)
            }
        }
    }

    private fun getOrCreateLanguageIdentifier(): LanguageIdentifier? {
        if (langIdUnavailable) return null
        cachedLanguageIdentifier?.let { return it }
        return synchronized(langIdInitLock) {
            if (langIdUnavailable) return@synchronized null
            cachedLanguageIdentifier?.let { return@synchronized it }
            try {
                LanguageIdentification.getClient(
                    LanguageIdentificationOptions.Builder()
                        .setConfidenceThreshold(0.4f)
                        .build()
                ).also { cachedLanguageIdentifier = it }
            } catch (_: Throwable) {
                langIdUnavailable = true
                null
            }
        }
    }

    private fun acquireTranslator(
        cacheKey: String,
        srcMlKit: String,
        targetMlKitLang: String
    ): ManagedTranslator? {
        synchronized(translatorCache) {
            val existing = translatorCache.get(cacheKey)
            if (existing != null && existing.acquire()) {
                return existing
            }
            val created = try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(srcMlKit)
                    .setTargetLanguage(targetMlKitLang)
                    .build()
                ManagedTranslator(Translation.getClient(options))
            } catch (_: Throwable) {
                return null
            }
            if (!created.acquire()) {
                created.requestClose()
                return null
            }
            translatorCache.put(cacheKey, created)
            return created
        }
    }

    internal fun isPredominantlyLatin(text: String): Boolean {
        var latin = 0
        var nonLatin = 0
        for (ch in text) {
            when {
                ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00C0'..'\u024F' -> latin++
                Character.isLetter(ch) -> nonLatin++
            }
        }
        return latin > 0 && latin >= nonLatin
    }

    /**
     * Pure-Kotlin script detector for prime store's supported scripts to avoid loading
     * native ML Kit LanguageIdentification when the script already identifies the language.
     * Returns null when the text is Latin-script without distinctive diacritics.
     */
    internal fun detectScriptLanguage(text: String): String? {
        var arabicCount = 0
        var persianUniqueCount = 0
        var persianSoftCount = 0
        var arabicSpecificCount = 0
        var urduUniqueCount = 0
        var cyrillicCount = 0
        var devanagariCount = 0
        var latinCount = 0
        var turkishHintCount = 0
        var germanHintCount = 0
        var spanishHintCount = 0
        var portugueseHintCount = 0

        for (ch in text) {
            when {
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\u08A0'..'\u08FF' -> {
                    arabicCount++
                    when (ch) {
                        'پ', 'چ', 'ژ', 'گ', 'ۀ' -> persianUniqueCount++
                        'ک', 'ی' -> persianSoftCount++
                        'ك', 'ي', 'ة', 'أ', 'إ', 'آ', 'ؤ', 'ئ', 'ء' -> arabicSpecificCount++
                        'ٹ', 'ڈ', 'ڑ', 'ے', 'ں', 'ہ', 'ھ', 'ۂ' -> urduUniqueCount++
                    }
                }
                ch in '\u0400'..'\u04FF' -> cyrillicCount++
                ch in '\u0900'..'\u097F' -> devanagariCount++
                ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00C0'..'\u024F' -> {
                    latinCount++
                    when (ch) {
                        'ğ', 'Ğ', 'ş', 'Ş', 'ı', 'İ' -> turkishHintCount++
                        'ß' -> germanHintCount++
                        'ñ', 'Ñ', '¿', '¡' -> spanishHintCount++
                        'ã', 'Ã', 'õ', 'Õ' -> portugueseHintCount++
                    }
                }
            }
        }

        val total = arabicCount + cyrillicCount + devanagariCount + latinCount
        if (total == 0) return null

        return when {
            arabicCount >= total * 0.5 -> when {
                urduUniqueCount > 0 -> "ur"
                persianUniqueCount > 0 || (persianSoftCount >= 2 && arabicSpecificCount == 0) -> "fa"
                else -> "ar"
            }
            cyrillicCount >= total * 0.5 -> "ru"
            devanagariCount >= total * 0.5 -> "hi"
            latinCount >= total * 0.5 -> when {
                turkishHintCount > 0 -> "tr"
                germanHintCount > 0 -> "de"
                spanishHintCount > 0 -> "es"
                portugueseHintCount > 0 -> "pt"
                else -> null
            }
            else -> null
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

    internal fun normalizeMlKitTag(tag: String): String {
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
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun isModelDownloaded(mlKitLang: String): Boolean =
        suspendCancellableCoroutine { cont ->
            try {
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
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(false)
            }
        }

    private suspend fun identifyLanguageSafely(text: String): String {
        val identifier = getOrCreateLanguageIdentifier() ?: return "und"
        return suspendCancellableCoroutine { cont ->
            try {
                identifier.identifyLanguage(text.take(300))
                    .addOnSuccessListener { languageCode ->
                        if (cont.isActive) cont.resume(languageCode)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume("und")
                    }
                    .addOnCanceledListener {
                        if (cont.isActive) cont.resume("und")
                    }
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume("und")
            }
        }
    }

    private suspend fun downloadModel(translator: Translator, conditions: DownloadConditions): Boolean =
        suspendCancellableCoroutine { cont ->
            try {
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
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(false)
            }
        }

    private suspend fun performTranslation(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            try {
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
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(text)
            }
        }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }
}
