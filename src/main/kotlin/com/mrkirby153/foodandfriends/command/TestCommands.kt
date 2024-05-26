package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.service.DataStoreService
import org.springframework.stereotype.Component
import java.io.Serializable

@Component
class TestCommands(
    dataStoreService: DataStoreService
) : ProvidesSlashCommands {

    private val ds =
        dataStoreService.getDataStoreFactory()
            .getDataStore<TestObject>("testing")

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
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