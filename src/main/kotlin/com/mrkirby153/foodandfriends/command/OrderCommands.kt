package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.command.slashcommand.dsl.types.user
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.OrderRepository
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.service.OrderService
import com.mrkirby153.foodandfriends.service.PersonService
import com.mrkirby153.foodandfriends.service.ScheduleService
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.Permission
import org.springframework.stereotype.Component

@Component
class OrderCommands(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val personService: PersonService,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleService: ScheduleService
) : ProvidesSlashCommands {


    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("order") {
                defaultPermissions(Permission.MANAGE_SERVER)
                subCommand("list") {
                    run {
                        transaction {
                            val orders = orderService.getAllOrders()
                            if (orders.isEmpty()) {
                                reply("No orders are currently configured").await()
                            } else {
                                reply {
                                    text {
                                        append("The following orders are configured:")
                                        code(buildString {
                                            orders.forEach { order ->
                                                append("- ${order.name} (${order.id})")
                                                if (order.schedule != null) {
                                                    appendLine(": ${order.schedule!!.id}")
                                                } else {
                                                    appendLine()
                                                }
                                            }
                                        })
                                    }
                                }.await()
                            }
                        }
                    }
                }
                subCommand("create") {
                    val name by string { }.required()
                    run {
                        val newOrder = orderService.create(name())
                        reply {
                            text {
                                append("Created new order ")
                                bold(newOrder.name)
                                append(" with ID ")
                                appendBlock(newOrder.id)
                            }
                        }.await()
                    }
                }
                subCommand("link") {
                    val order by orderRepository.argument(enableAutocomplete = true,
                        autocompleteName = { it.name }) {}.required()
                    val schedule by scheduleRepository.argument {}.required()
                    run {
                        scheduleService.link(schedule(), order())
                        reply("Linked order ${order().id} to ${schedule().id}").await()
                    }
                }
                subCommand("unlink") {
                    val schedule by scheduleRepository.argument { }.required()
                    run {
                        val newSchedule = scheduleService.unlink(schedule())
                        reply("Unlinked order from schedule ${newSchedule.id}").await()
                    }
                }
                subCommand("remove") {
                    val order by orderRepository.argument { }.required()
                    run {
                        transaction {
                            if (order().schedule != null) {
                                reply("Order is linked to schedule `${order().schedule!!.id}`, remove it first!").await()
                                return@transaction
                            }
                            orderService.delete(order())
                            reply("Deleted!").await()
                        }
                    }
                }
                subCommand("add-person") {
                    val order by orderRepository.argument { }.required()
                    val person by user { }.required()
                    run {
                        orderService.addPerson(personService.getOrCreate(person()), order())
                        reply {
                            text { appendLine("Added ${person().asMention} to order ${order().name}") }
                        }.await()
                    }
                }
                subCommand("remove-person") {
                    val order by orderRepository.argument { }.required()
                    val person by user { }.required()
                    run {
                        orderService.removePerson(personService.getOrCreate(person()), order())
                        reply {
                            text { appendLine("Removed ${person().asMention} from order ${order().name}") }
                        }.await()
                    }
                }
            }
        }
    }
}