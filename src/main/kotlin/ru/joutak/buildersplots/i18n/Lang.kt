package ru.joutak.buildersplots.i18n

enum class Lang(val code: String, val fileName: String) {
    ENGLISH("en", "messages_en.yml"),
    RUSSIAN("ru", "messages_ru.yml");

    companion object {
        fun fromCode(code: String): Lang {
            return values().find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}