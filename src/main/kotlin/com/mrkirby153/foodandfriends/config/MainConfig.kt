package com.mrkirby153.foodandfriends.config

import com.mrkirby153.botcore.spring.CommandAutoConfiguration
import com.mrkirby153.botcore.spring.JDAAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.support.ResourceBundleMessageSource

@Configuration
@Import(JDAAutoConfiguration::class, CommandAutoConfiguration::class)
class MainConfig {

    @Bean
    fun messageSource(): ResourceBundleMessageSource {
        val source = ResourceBundleMessageSource()
        source.setBasename("messages/messages")
        source.setUseCodeAsDefaultMessage(true)
        return source
    }
}