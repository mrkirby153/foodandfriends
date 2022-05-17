package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.SlashCommand
import com.mrkirby153.botcore.command.slashcommand.SlashCommandParameter
import com.mrkirby153.foodandfriends.service.EmailService
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.springframework.stereotype.Service

@Service
class EmailSlashCommands(
    val emailService: EmailService
) {

    @SlashCommand(name = "email get", description = "Gets your saved email")
    fun get(event: SlashCommandInteractionEvent) {
        event.deferReply(true).queue { hook ->
            val email = emailService.getEmail(event.user)
            hook.editOriginal(if (email != null) "You will receive calendar invites to the following email: `$email`" else "You do not have an email set and will not receive calendar invites")
                .queue()
        }

    }

    @SlashCommand(name = "email set", description = "Sets your saved email")
    fun set(
        event: SlashCommandInteractionEvent,
        @SlashCommandParameter(
            name = "email",
            description = "The email to receive calendar invites to"
        ) email: String
    ) {
        event.deferReply(true).queue { hook ->
            emailService.setEmail(event.user, email)
            hook.editOriginal("Calendar invites will automatically be sent to `${email}`").queue()
        }

    }

    @SlashCommand(name = "email delete", description = "Removes your saved email")
    fun delete(event: SlashCommandInteractionEvent) {
        event.deferReply(true).queue { hook ->
            emailService.removeEmail(event.user)
            hook.editOriginal("Removed your email. You will no longer receive calendar invites").queue()
        }
    }
}