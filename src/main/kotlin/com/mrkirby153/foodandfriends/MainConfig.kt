package com.mrkirby153.foodandfriends

import com.mrkirby153.botcore.spring.CommandAutoConfiguration
import com.mrkirby153.botcore.spring.JDAAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(JDAAutoConfiguration::class, CommandAutoConfiguration::class)
class MainConfig {
}