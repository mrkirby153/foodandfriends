package com.mrkirby153.foodandfriends.service

import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.util.Locale

interface LocalizationService {

    /**
     * Translates a message
     */
    fun translate(key: String, vararg params: Any, locale: Locale? = null): String
}


@Service
class LocalizationManager(
    private val messageSource: MessageSource
) : LocalizationService {
    override fun translate(key: String, vararg params: Any, locale: Locale?): String {
        return this.messageSource.getMessage(key, params, locale ?: Locale.ENGLISH)
    }
}