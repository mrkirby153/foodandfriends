package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.service.PersonService
import org.springframework.stereotype.Component

@Component
class EmailCommands(
    private val personService: PersonService,
) : ProvidesSlashCommands {
    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("email") {
                description = "Manages the email that receives calendar invites"

                subCommand("get") {
                    description = "Gets your current email"
                    run {
                        val email = personService.getEmail(user)
                        if (email == null) {
                            reply(true) {
                                text(true) {
                                    append("No Email is currently configured")
                                }
                            }.await()
                        } else {
                            reply(true) {
                                text(true) {
                                    bold(email)
                                    append(" is currently configured as your email")
                                }
                            }.await()
                        }
                    }
                }
                subCommand("set") {
                    description = "Sets your current email"
                    val email by string {
                        description = "The email you wish to receive emails from"
                    }.required()

                    run {
                        personService.setEmail(user, email())
                        reply(true) {
                            text(true) {
                                append("You will now receive emails at ")
                                bold(email())
                            }
                        }.await()
                    }
                }
            }
        }
    }
}