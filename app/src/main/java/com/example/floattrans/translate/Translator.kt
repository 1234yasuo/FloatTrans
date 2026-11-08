package com.example.floattrans.translate
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
class MlKitTranslator {
    private val idClient = LanguageIdentification.getClient()
    private val cache = HashMap<String, com.google.mlkit.nl.translate.Translator>()
    suspend fun translate(text: String, sourceLang: String?, targetLang: String): String {
        if (text.isBlank()) return ""
        val src = TranslateLanguage.fromLanguageTag(sourceLang ?: detect(text)) ?: TranslateLanguage.ENGLISH
        if (src == targetLang) return text
        val translator = cache.getOrPut("$src->$targetLang") { Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(targetLang).build()) }
        suspendCancellableCoroutine<Unit> { it.resume(Unit) }
        return suspendCancellableCoroutine { cont -> translator.translate(text).addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resumeWithException(it) } }
    }
    private suspend fun detect(text: String): String = suspendCancellableCoroutine { cont ->
        idClient.identifyPossibleLanguages(text).addOnSuccessListener { cont.resume(it.firstOrNull()?.languageTag ?: TranslateLanguage.ENGLISH) }.addOnFailureListener { cont.resume(TranslateLanguage.ENGLISH) }
    }
}
