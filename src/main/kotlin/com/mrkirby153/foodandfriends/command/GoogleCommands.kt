package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
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
            slashCommand("sync-calendar") {
                defaultPermissions(Permission.MANAGE_SERVER)
                run {
                    defer(true) {
                        googleCalendarService.syncEvents()
                        it.editOriginal("Syncing calendar!").await()
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
                            reply("Click [this](${authorizationHandler.getAuthorizationUrl(user, force())}) link to get your authorization code!").setEphemeral(
                                true
                            ).await()
                        }
                    }
                }
            }
        }
    }
}
