package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.google.AuthorizationHandler
import com.mrkirby153.foodandfriends.google.GoogleOAuthException
import com.mrkirby153.foodandfriends.service.CalendarService
import com.mrkirby153.foodandfriends.service.DataStoreService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.io.Serializable

@Component
class TestCommands(
    dataStoreService: DataStoreService,
    private val authorizationHandler: AuthorizationHandler,
    private val calendarService: CalendarService
) : ProvidesSlashCommands {

    private val log = KotlinLogging.logger { }

    private val ds =
        dataStoreService.getDataStoreFactory()
            .getDataStore<TestObject>("testing")

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("test-invite") {
                run {
                    val event = calendarService.buildCalendarInvite()
                    val evt =
                        calendarService.getService(user).events().insert("primary", event)
                            .setSendUpdates("all").execute()
                    reply("Created event: ${evt.iCalUID}").await()
                }
            }

            slashCommand("oauth") {
                subCommand("check-auth") {
                    run {
                        if (authorizationHandler.authorized(user)) {
                            val credentials = authorizationHandler.getAuthorization(user)
                            if (credentials != null) {
                                log.debug { "Credentials: ${credentials.accessToken}\nRefresh token: ${credentials.refreshToken}\n${credentials.expiresInSeconds}" }
                            }
                            reply("Authorized!").setEphemeral(true).await()
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
                    run {
                        val code = codeParam()
                        if (code != null) {
                            log.debug { "Validating code!" }
                            try {
                                authorizationHandler.authorize(user, code)
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
                            reply("Click [this](${authorizationHandler.getAuthorizationUrl(user)}) link to get your authorization code!").setEphemeral(
                                true
                            ).await()
                        }
                    }
                }
            }

            slashCommand("data-store") {
                subCommand("get") {
                    val key by string {
                        description = "The key to get"
                    }.optional()
                    run {
                        if (key() == null) {
                            // get all
                            reply {
                                text {
                                    val keys = ds.keySet()
                                    appendLine("Available keys: ```")
                                    if (keys.isNotEmpty()) {
                                        keys.forEach {
                                            appendLine(it)
                                        }
                                    } else {
                                        appendLine("no keys available!")
                                    }
                                    appendLine("```")
                                }
                            }.await()
                        } else {
                            val data = ds.get(key())
                            reply {
                                text {
                                    appendLine("The data: $data")
                                }
                            }.await()
                        }
                    }
                }

                subCommand("set") {
                    val key by string {
                        description = "The key to set"
                    }.required()
                    val one by string {
                        description = "the first param"
                    }.required()
                    val two by int {
                        description = "the second param"
                    }.required()
                    run {
                        val newObj = TestObject(one(), two())
                        ds.set(key(), newObj)
                        reply("Done!").await()
                    }
                }
                subCommand("delete") {
                    val key by string { description = "the key to delete" }.required()
                    run {
                        ds.delete(key())
                        reply("Done!").await()
                    }
                }

            }
        }
    }
}

data class TestObject(
    val one: String,
    val two: Int
) : Serializable