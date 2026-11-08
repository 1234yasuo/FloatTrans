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
    private val cache = ConcurrentHashMap<String, com.google.mlkit.nl.translate.Translator>()
    
    suspend fun translate(text: String, sourceLang: String?, targetLang: String): String {
        if (text.isBlank()) return ""
        if (text.matches(Regex("^[0-9\\p{Punct}\\s]+$"))) return text
        
        var detectedSrc = sourceLang ?: detect(text)
        // 【保底】：如果没识别出来，或者文本极短，强制默认为英文
        if (detectedSrc == "und" || detectedSrc.isBlank()) detectedSrc = "en"
        
        var src = TranslateLanguage.fromLanguageTag(detectedSrc) ?: TranslateLanguage.ENGLISH
        
        // 如果检测出的语言就是目标语言，直接返回原文
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
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.firstOrNull()?.languageTag ?: "und") }
            .addOnFailureListener { if (cont.isActive) cont.resume("und") }
    }
}
