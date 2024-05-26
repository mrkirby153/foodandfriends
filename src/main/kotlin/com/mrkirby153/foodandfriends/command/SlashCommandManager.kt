package com.mrkirby153.foodandfriends.command

import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class SlashCommandManager(
    private val shardManager: ShardManager,
//    private val slashCommandExecutor: SlashCommandExecutor,
    private val context: ApplicationContext,
    @Value("\${bot.slash-command-guilds:}") private val slashCommandGuilds: String
) {
//    companion object {
//        private val log = LogManager.getLogger()
//    }
//
//    private final val slashCommands = mutableListOf<Class<*>>()
//
//    init {
//
//    }
//
//    @EventListener
//    fun onReady(event: BotReadyEvent) {
//        log.info("Discovering and registering slash commands")
//        slashCommands.forEach {
//            try {
//                slashCommandExecutor.discoverAndRegisterSlashCommands(context.getBean(it), it)
//            } catch (e: Exception) {
//                log.error("Could not register {}", it, e)
//            }
//        }
//
//        val commands = slashCommandExecutor.flattenSlashCommands()
//
//        if (slashCommandGuilds.isBlank()) {
//            log.info("Updating {} slash commands globally", commands.size)
//            shardManager.getShardById(0)?.updateCommands()?.addCommands(commands)?.queue {
//                log.info("Updated slash commands globally: {}", it.size)
//            }
//        } else {
//            log.info("Updating {} slash commands in guilds: {}", commands.size, slashCommandGuilds)
//            slashCommandGuilds.split(",").mapNotNull { shardManager.getGuildById(it) }
//                .forEach { guild ->
//                    guild.updateCommands().addCommands(commands).queue {
//                        log.info("Updated slash commands in {}: {}", guild, it.size)
//                    }
//                }
//        }
//    }
//
//    @EventListener
//    fun onSlashCommand(event: SlashCommandInteractionEvent) {
//        if (!slashCommandExecutor.executeSlashCommandIfAble(event)) {
//            event.reply("No slash command executor was available to handle your command!")
//                .setEphemeral(true).queue()
//        }
//    }
}