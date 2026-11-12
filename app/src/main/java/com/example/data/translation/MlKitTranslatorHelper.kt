package com.example.data.translation

import android.content.Context
import com.example.data.local.PrimeStoreDatabase
import com.example.data.local.TranslationEntity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.resume

object MlKitTranslatorHelper {

    private val languageIdentifier = LanguageIdentification.getClient()

    suspend fun translateText(
        context: Context,
        appId: String,
        field: String,
        originalText: String,
        targetLanguageCode: String,
        wifiOnly: Boolean = false
    ): String {
        if (originalText.isBlank()) return originalText

        val db = PrimeStoreDatabase.getInstance(context)
        val textHash = md5(originalText)

        // 1. Check local Room cache
        val cached = db.translationDao().getTranslation(appId, field, targetLanguageCode, textHash)
        if (cached != null && cached.translatedText.isNotBlank()) {
            return cached.translatedText
        }

        // 2. Identify source language
        val sourceLanguageCode = identifyLanguage(originalText)
        if (sourceLanguageCode == "und" || sourceLanguageCode == targetLanguageCode) {
            return originalText
        }

        val sourceMlKitLang = TranslateLanguage.fromLanguageTag(sourceLanguageCode) ?: TranslateLanguage.ENGLISH
        val targetMlKitLang = TranslateLanguage.fromLanguageTag(targetLanguageCode) ?: TranslateLanguage.ARABIC

        if (sourceMlKitLang == targetMlKitLang) {
            return originalText
        }

        return withContext(Dispatchers.IO) {
            var translator: Translator? = null
            try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceMlKitLang)
                    .setTargetLanguage(targetMlKitLang)
                    .build()

                translator = Translation.getClient(options)

                val conditionsBuilder = DownloadConditions.Builder()
                if (wifiOnly) {
                    conditionsBuilder.requireWifi()
                }
                val conditions = conditionsBuilder.build()

                val modelDownloaded = downloadModel(translator, conditions)
                if (!modelDownloaded) {
                    return@withContext originalText
                }

                val translated = performTranslation(translator, originalText)

                // Save to Room cache
                if (translated.isNotBlank() && translated != originalText) {
                    db.translationDao().saveTranslation(
                        TranslationEntity(
                            appId = appId,
                            field = field,
                            targetLang = targetLanguageCode,
                            textHash = textHash,
                            translatedText = translated
                        )
                    )
                }

                translated
            } catch (_: Exception) {
                originalText
            } finally {
                translator?.close()
            }
        }
    }

    private suspend fun identifyLanguage(text: String): String = suspendCancellableCoroutine { cont ->
        languageIdentifier.identifyLanguage(text.take(200))
            .addOnSuccessListener { languageCode ->
                cont.resume(languageCode)
            }
            .addOnFailureListener {
                cont.resume("und")
            }
    }

    private suspend fun downloadModel(translator: Translator, conditions: DownloadConditions): Boolean =
        suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    cont.resume(true)
                }
                .addOnFailureListener {
                    cont.resume(false)
                }
        }

    private suspend fun performTranslation(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    cont.resume(translatedText)
                }
                .addOnFailureListener {
                    cont.resume(text)
                }
        }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }
}
