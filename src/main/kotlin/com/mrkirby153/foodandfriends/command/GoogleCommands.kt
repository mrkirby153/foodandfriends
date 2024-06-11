package com.mrkirby153.foodandfriends.command

import com.google.api.services.calendar.model.Event
import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.messageContextCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.boolean
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.google.AuthorizationHandler
import com.mrkirby153.foodandfriends.google.GoogleOAuthException
import com.mrkirby153.foodandfriends.service.GoogleCalendarService
import io.github.oshai.kotlinlogging.KotlinLogging
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.Permission
import org.springframework.stereotype.Component

@Component
class GoogleCommands(
    private val authorizationHandler: AuthorizationHandler,
    private val googleCalendarService: GoogleCalendarService,
    private val eventRepository: EventRepository
) : ProvidesSlashCommands {

    private val log = KotlinLogging.logger { }

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            messageContextCommand("Sync Event") {
                check {
                    if (this.instance.member?.hasPermission(Permission.MANAGE_SERVER) == false) {
                        fail("You do not have permission to perform this command")
                    }
                }
                action {
                    transaction {
                        val hook = it.deferReply(true).await()
                        val event = eventRepository.getByDiscordMessageId(it.target.idLong)
                            ?: throw CommandException("Event not found. Are you sure this is an event message?")
                        googleCalendarService.syncEvent(event)
                        hook.editOriginal("Synced event ${event.id}").await()
                    }
                }
            }
            slashCommand("sync-calendar") {
                defaultPermissions(Permission.MANAGE_SERVER)
                run {
                    defer(true) {
                        googleCalendarService.syncEvents()
                        it.editOriginal("Syncing calendar!").await()
                    }
                }
            }
            slashCommand("event-debug") {
                defaultPermissions(Permission.MANAGE_SERVER)
                val event by eventRepository.argument {
                    description = "The event to debug"
                }.required()
                run {
                    defer(true) {
                        val invite = googleCalendarService.getNewInvite(event())
                        val googleEvent = googleCalendarService.getCalendarEvent(event())

                        fun build(event: Event) = buildString {
                            append("- Subject: `")
                            append(event.summary)
                            appendLine("`")
                            append("- Attendees: `")
                            append(event.attendees.joinToString(",") { attendee -> "${attendee.email}/${attendee.responseStatus}" })
                            appendLine("`")
                            appendLine("- Start: `${event.start}`")
                            appendLine("- End: `${event.end}`")
                        }
                        it.editOriginal(buildString {
                            appendLine("**Invite:**")
                            appendLine(build(invite))
                            if (googleEvent != null) {
                                appendLine("**Google Invite**")
                                appendLine("`${googleEvent.id}`")
                                appendLine(build(googleEvent))
                            }
                        }).await()
                    }
                }
            }
            slashCommand("oauth") {
                defaultPermissions(Permission.MANAGE_SERVER)
                subCommand("check-auth") {
                    run {
                        if (authorizationHandler.authorized(user)) {
                            val credentials = authorizationHandler.getAuthorization(user)
                            if (credentials != null) {
                                log.debug { "Credentials: ${credentials.accessToken}\nRefresh token: ${credentials.refreshToken}\n${credentials.expiresInSeconds}" }
                            }
                            reply("Authorized! Has refresh token? ${credentials?.refreshToken != null}").setEphemeral(
                                true
                            ).await()
                        } else {
                            reply("Not Authorized").setEphemeral(true).await()
                        }
                    }
                }

                subCommand("auth") {
                    val codeParam by string {
                        name = "code"
                        description = "The oauth callback code"
                    }.optional()
                    val force by boolean {
                        description = "Force a reauth (Retrieves a refresh token)"
                    }.optional(false)
                    run {
                        val code = codeParam()
                        if (code != null) {
                            log.debug { "Validating code!" }
                            try {
                                authorizationHandler.authorize(user, code, force())
                                reply("Authorized!").setEphemeral(true).await()
                            } catch (e: GoogleOAuthException.AuthenticationFailedException) {
                                reply("Failed to authenticate you. is your code correct?").setEphemeral(
                                    true
                                ).await()
                            } catch (e: GoogleOAuthException.AlreadyAuthenticatedException) {
                                reply("You are already authenticated!").setEphemeral(true).await()
                            }
                        } else {
                            log.debug { "Not authorized!" }
                            reply(
                                "Click [this](${
                                    authorizationHandler.getAuthorizationUrl(
                                        user,
                                        force()
                                    )
                                }) link to get your authorization code!"
                            ).setEphemeral(
                                true
                            ).await()
                        }
                    }
                }
            }
        }
    }
}
