package com.mrkirby153.foodandfriends.command

import ch.qos.logback.classic.Logger
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.enum
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.stereotype.Service

@Service
class LoggingCommands : ProvidesSlashCommands {
    private val log = KotlinLogging.logger { }

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("log-level") {
                disableByDefault()
                description = "Gets or sets the log level for the bot"
                val logger by string {
                    description = "The logger to affect"
                }.required()
                val level by enum<Level> {
                    description = "The log level to set"
                }.optional()
                run {
                    val logger = LoggerFactory.getLogger(logger()) as Logger
                    if (level() != null) {
                        val prev = logger.effectiveLevel
                        logger.level = ch.qos.logback.classic.Level.valueOf(level()!!.name)
                        log.info { "Log level for ${logger()} changed from $prev -> ${level()!!.name}" }
                        reply {
                            content =
                                "Log level for `${logger()}` changed from `$prev` -> `${level()!!.name}`"
                        }.await()
                    } else {
                        reply {
                            content =
                                "Logger `${logger.name}` is logging at level `${logger.effectiveLevel}`"
                        }.setEphemeral(true).await()
                    }
                }
            }
        }
    }
}