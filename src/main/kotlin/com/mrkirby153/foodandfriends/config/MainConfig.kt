package com.mrkirby153.foodandfriends.config

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.spring.config.EnableBot
import com.mrkirby153.botcore.spring.config.EnableJpaAutocomplete
import com.mrkirby153.botcore.spring.config.EnableModalManager
import com.mrkirby153.botcore.spring.config.ModalManagerConfiguration
import com.mrkirby153.botcore.spring.config.RegisterSlashCommands
import me.mrkirby153.kcutils.spring.coroutine.CoroutineTransactionHandler
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executor

@Configuration
@EnableScheduling
@EnableAsync(proxyTargetClass = true)
@EnableJpaAuditing
@EnableBot
@EnableModalManager
@RegisterSlashCommands
@EnableJpaAutocomplete
class MainConfig(
    dslCommandExecutor: DslCommandExecutor,
    shardManager: ShardManager,
    transactionTemplate: TransactionTemplate
) : AsyncConfigurer {

    private val threadPoolTaskExecutor = ThreadPoolTaskExecutor()

    init {
        shardManager.addEventListener(dslCommandExecutor.getListener())
        CoroutineTransactionHandler.setDefault(transactionTemplate)
    }

    @Bean
    fun messageSource(): ResourceBundleMessageSource {
        val source = ResourceBundleMessageSource()
        source.setBasename("messages/messages")
        source.setUseCodeAsDefaultMessage(true)
        return source
    }

    override fun getAsyncExecutor(): Executor? {
        threadPoolTaskExecutor.initialize()
        return threadPoolTaskExecutor
    }

    @Bean
    @Primary
    fun threadPoolTaskScheduler(): ThreadPoolTaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 5
            setThreadNamePrefix("TaskScheduler")
        }
    }
}