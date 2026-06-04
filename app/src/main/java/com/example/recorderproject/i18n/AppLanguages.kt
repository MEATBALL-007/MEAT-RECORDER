package com.example.recorderproject.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language support. 20 major languages, switched at runtime via
 * AppCompatDelegate (system per-app locales on Android 13+, AppCompat backport below that).
 *
 * The picked locale persists automatically because the manifest declares
 * AppLocalesMetadataHolderService with autoStoreLocales=true.
 */
object AppLanguages {

    data class Lang(val code: String, val english: String, val native: String)

    /** Ordered roughly by global speaker count / Play Store reach. "" = follow system. */
    val all: List<Lang> = listOf(
        Lang("", "System default", "System default"),
        Lang("en", "English", "English"),
        Lang("zh", "Chinese", "中文"),
        Lang("hi", "Hindi", "हिन्दी"),
        Lang("es", "Spanish", "Español"),
        Lang("fr", "French", "Français"),
        Lang("ar", "Arabic", "العربية"),
        Lang("bn", "Bengali", "বাংলা"),
        Lang("pt", "Portuguese", "Português"),
        Lang("ru", "Russian", "Русский"),
        Lang("id", "Indonesian", "Bahasa Indonesia"),
        Lang("ja", "Japanese", "日本語"),
        Lang("de", "German", "Deutsch"),
        Lang("ko", "Korean", "한국어"),
        Lang("tr", "Turkish", "Türkçe"),
        Lang("vi", "Vietnamese", "Tiếng Việt"),
        Lang("th", "Thai", "ไทย"),
        Lang("it", "Italian", "Italiano"),
        Lang("pl", "Polish", "Polski"),
        Lang("uk", "Ukrainian", "Українська"),
        Lang("fa", "Persian", "فارسی"),
    )

    /** Apply a language by code ("" = system default). Recreates affected activities automatically. */
    fun setLanguage(code: String) {
        val locales = if (code.isBlank()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(code)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** The currently-applied language code, or "" if following the system. */
    fun currentCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "" else locales[0]?.language ?: ""
    }

    /** Native display name of the current language, for the settings row subtitle. */
    fun currentDisplayName(): String {
        val code = currentCode()
        return all.firstOrNull { it.code == code }?.native
            ?: all.first { it.code == "" }.native
    }
}
