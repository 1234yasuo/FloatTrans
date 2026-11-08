package com.example.floattrans.translate
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ConcurrentHashMap

class MlKitTranslator {
    private val idClient = LanguageIdentification.getClient()
    // 【修复】：使用 ConcurrentHashMap 保证多线程下获取翻译器实例不崩溃
    private val cache = ConcurrentHashMap<String, com.google.mlkit.nl.translate.Translator>()
    
    suspend fun translate(text: String, sourceLang: String?, targetLang: String): String {
        if (text.isBlank()) return ""
        val src = TranslateLanguage.fromLanguageTag(sourceLang ?: detect(text)) ?: TranslateLanguage.ENGLISH
        if (src == targetLang) return text
        
        val translator = cache.getOrPut("$src->$targetLang") { 
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(targetLang).build()) 
        }
        
        suspendCancellableCoroutine<Unit> { cont -> 
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) } 
        }
        
        return suspendCancellableCoroutine { cont -> 
            translator.translate(text)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }
    
    private suspend fun detect(text: String): String = suspendCancellableCoroutine { cont ->
        idClient.identifyPossibleLanguages(text)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.firstOrNull()?.languageTag ?: TranslateLanguage.ENGLISH) }
            .addOnFailureListener { if (cont.isActive) cont.resume(TranslateLanguage.ENGLISH) }
    }
}
