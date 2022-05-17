package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.SlashCommand
import com.mrkirby153.foodandfriends.service.EventService
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.springframework.stereotype.Service

@Service
class EventSlashCommands(
    private val eventService: EventService
) {

    @SlashCommand(name = "trigger", description = "Manually trigger an event")
    fun trigger(event: SlashCommandInteractionEvent) {
        eventService.sendMessage()
        event.reply("Triggered event message").setEphemeral(true).queue()
    }
}