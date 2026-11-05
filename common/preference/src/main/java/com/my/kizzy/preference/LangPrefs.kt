/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LangPrefs.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.preference

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.my.kizzy.resources.R

// Languages Index number
const val SYSTEM_DEFAULT = 0
private const val ENGLISH = 1
private const val TURKISH = 2
private const val DUTCH = 3
private const val RUSSIAN = 4
private const val POLISH = 5
private const val PORTUGUESE = 6
private const val INDONESIAN = 7
private const val SIMPLIFIED_CHINESE = 8
private const val TRADITIONAL_CHINESE = 9
private const val BURMESE = 10
private const val VIETNAMESE = 11
private const val ITALIAN = 12
private const val FILIPINO = 13
private const val FRENCH = 14
private const val CROATIAN = 15
private const val FARSI = 16
private const val GERMAN = 17
private const val THAI = 18
private const val JAPANESE = 19
private const val KOREAN = 20
private const val ARABIC = 21
private const val SPANISH = 22
private const val HEBREW = 23
private const val BULGARIAN = 24
private const val CZECH = 25
private const val DANISH = 26
private const val GREEK = 27
private const val ESTONIAN = 28
private const val FINNISH = 29
private const val GALICIAN = 30
private const val HUNGARIAN = 31
private const val ICELANDIC = 32
private const val LITHUANIAN = 33
private const val LATVIAN = 34
private const val MACEDONIAN = 35
private const val MALAY = 36
private const val NORWEGIAN_BOKMAL = 37
private const val PUNJABI = 38
private const val ROMANIAN = 39
private const val SLOVAK = 40
private const val SLOVENIAN = 41
private const val SERBIAN = 42
private const val SWEDISH = 43
private const val UKRAINIAN = 44
private const val BENGALI = 45
private const val GUJARATI = 46
private const val KANNADA = 47
private const val MALAYALAM = 48
private const val MARATHI = 49
private const val NEPALI = 50
private const val ORIYA = 51
private const val SINHALA = 52
private const val TAMIL = 53
private const val TELUGU = 54
private const val URDU = 55
private const val AFRIKAANS = 56
private const val ALBANIAN = 57
private const val ARMENIAN = 58
private const val AZERBAIJANI = 59
private const val BASQUE = 60
private const val BELARUSIAN = 61
private const val GEORGIAN = 62
private const val KAZAKH = 63
private const val KHMER = 64
private const val LAO = 65
private const val MONGOLIAN = 66
private const val PASHTO = 67
private const val SANSKRIT = 68
private const val TIBETAN = 69
private const val UZBEK = 70
private const val WELSH = 71
private const val ZULU = 72

val languages: Map<Int, String> =
    mapOf(
        Pair(ENGLISH, "en"),
        Pair(TURKISH, "tr"),
        Pair(DUTCH, "nl"),
        Pair(RUSSIAN, "ru"),
        Pair(POLISH, "pl"),
        Pair(PORTUGUESE, "pt"),
        Pair(INDONESIAN, "in"),
        Pair(SIMPLIFIED_CHINESE, "zh"),
        Pair(TRADITIONAL_CHINESE, "zh-TW"),
        Pair(BURMESE, "mm"),
        Pair(VIETNAMESE, "vi"),
        Pair(ITALIAN, "it"),
        Pair(FILIPINO, "fil"),
        Pair(FRENCH, "fr"),
        Pair(CROATIAN, "hr"),
        Pair(FARSI, "fa"),
        Pair(GERMAN, "de"),
        Pair(THAI, "th"),
        Pair(JAPANESE, "ja"),
        Pair(KOREAN, "kr"),
        Pair(ARABIC, "ar"),
        Pair(SPANISH, "es"),
        Pair(HEBREW, "he"),
Pair(BULGARIAN, "bg"),
Pair(CZECH, "cs"),
Pair(DANISH, "da"),
Pair(GREEK, "el"),
Pair(ESTONIAN, "et"),
Pair(FINNISH, "fi"),
Pair(GALICIAN, "gl"),
Pair(HUNGARIAN, "hu"),
Pair(ICELANDIC, "is"),
Pair(LITHUANIAN, "lt"),
Pair(LATVIAN, "lv"),
Pair(MACEDONIAN, "mk"),
Pair(MALAY, "ms"),
Pair(NORWEGIAN_BOKMAL, "nb"),
Pair(PUNJABI, "pa"),
Pair(ROMANIAN, "ro"),
Pair(SLOVAK, "sk"),
Pair(SLOVENIAN, "sl"),
Pair(SERBIAN, "sr"),
Pair(SWEDISH, "sv"),
Pair(UKRAINIAN, "uk"),
Pair(BENGALI, "bn"),
Pair(GUJARATI, "gu"),
Pair(KANNADA, "kn"),
Pair(MALAYALAM, "ml"),
Pair(MARATHI, "mr"),
Pair(NEPALI, "ne"),
Pair(ORIYA, "or"),
Pair(SINHALA, "si"),
Pair(TAMIL, "ta"),
Pair(TELUGU, "te"),
Pair(URDU, "ur"),
Pair(AFRIKAANS, "af"),
Pair(ALBANIAN, "sq"),
Pair(ARMENIAN, "hy"),
Pair(AZERBAIJANI, "az"),
Pair(BASQUE, "eu"),
Pair(BELARUSIAN, "be"),
Pair(GEORGIAN, "ka"),
Pair(KAZAKH, "kk"),
Pair(KHMER, "km"),
Pair(LAO, "lo"),
Pair(MONGOLIAN, "mn"),
Pair(PASHTO, "ps"),
Pair(SANSKRIT, "sa"),
Pair(TIBETAN, "bo"),
Pair(UZBEK, "uz"),
Pair(WELSH, "cy"),
Pair(ZULU, "zu")
    ).toList().sortedBy { (_, value) -> value }.toMap()

fun getLanguageConfig(languageNumber: Int = Prefs[Prefs.LANGUAGE]): String {
    return if (languages.containsKey(languageNumber)) languages[languageNumber].toString() else ""
}

private fun getLanguageNumberByCode(languageCode: String): Int {
    languages.entries.forEach {
        if (it.value == languageCode) return it.key
    }
    return SYSTEM_DEFAULT
}

fun getLanguageNumber(): Int {
    return if (Build.VERSION.SDK_INT >= 33)
        getLanguageNumberByCode(
            LocaleListCompat.getAdjustedDefault()[0]?.toLanguageTag().toString()
        )
    else Prefs[Prefs.LANGUAGE, SYSTEM_DEFAULT]
}

@Composable
fun getLanguageDesc(language: Int = getLanguageNumber()): String {
    return stringResource(
        when (language) {
            SIMPLIFIED_CHINESE -> R.string.locale_zh_rcn
            TRADITIONAL_CHINESE -> R.string.locale_zh_rtw
            ENGLISH -> R.string.locale_en
            TURKISH -> R.string.locale_tr
            RUSSIAN -> R.string.locale_ru
            INDONESIAN -> R.string.locale_in
            DUTCH -> R.string.locale_nl
            POLISH -> R.string.locale_pl
            PORTUGUESE -> R.string.locale_pt
            BURMESE -> R.string.locale_mm
            VIETNAMESE -> R.string.locale_vi
            ITALIAN -> R.string.locale_it
            FILIPINO -> R.string.locale_fil
            FRENCH -> R.string.locale_fr
            CROATIAN -> R.string.locale_hr
            FARSI -> R.string.locale_fa
            GERMAN -> R.string.locale_de
            THAI -> R.string.locale_th
            JAPANESE -> R.string.locale_ja
            KOREAN -> R.string.locale_kr
            ARABIC -> R.string.locale_ar
            SPANISH -> R.string.locale_es
            HEBREW -> R.string.locale_he
            BULGARIAN -> R.string.locale_bg
            CZECH -> R.string.locale_cs
            DANISH -> R.string.locale_da
            GREEK -> R.string.locale_el
            ESTONIAN -> R.string.locale_et
            FINNISH -> R.string.locale_fi
            GALICIAN -> R.string.locale_gl
            HUNGARIAN -> R.string.locale_hu
            ICELANDIC -> R.string.locale_is
            LITHUANIAN -> R.string.locale_lt
            LATVIAN -> R.string.locale_lv
            MACEDONIAN -> R.string.locale_mk
            MALAY -> R.string.locale_ms
            NORWEGIAN_BOKMAL -> R.string.locale_nb
            PUNJABI -> R.string.locale_pa
            ROMANIAN -> R.string.locale_ro
            SLOVAK -> R.string.locale_sk
            SLOVENIAN -> R.string.locale_sl
            SERBIAN -> R.string.locale_sr
            SWEDISH -> R.string.locale_sv
            UKRAINIAN -> R.string.locale_uk
            BENGALI -> R.string.locale_bn
            GUJARATI -> R.string.locale_gu
            KANNADA -> R.string.locale_kn
            MALAYALAM -> R.string.locale_ml
            MARATHI -> R.string.locale_mr
            NEPALI -> R.string.locale_ne
            ORIYA -> R.string.locale_or
            SINHALA -> R.string.locale_si
            TAMIL -> R.string.locale_ta
            TELUGU -> R.string.locale_te
            URDU -> R.string.locale_ur
            AFRIKAANS -> R.string.locale_af
            ALBANIAN -> R.string.locale_sq
            ARMENIAN -> R.string.locale_hy
            AZERBAIJANI -> R.string.locale_az
            BASQUE -> R.string.locale_eu
            BELARUSIAN -> R.string.locale_be
            GEORGIAN -> R.string.locale_ka
            KAZAKH -> R.string.locale_kk
            KHMER -> R.string.locale_km
            LAO -> R.string.locale_lo
            MONGOLIAN -> R.string.locale_mn
            PASHTO -> R.string.locale_ps
            SANSKRIT -> R.string.locale_sa
            TIBETAN -> R.string.locale_bo
            WELSH -> R.string.locale_cy
            ZULU -> R.string.locale_zu
            else -> R.string.follow_system
        }
    )
}