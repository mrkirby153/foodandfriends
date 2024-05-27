package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.enum
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.command.slashcommand.dsl.types.textChannel
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.DayOfWeek
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.service.EventService
import com.mrkirby153.foodandfriends.service.ScheduleService
import io.github.oshai.kotlinlogging.KotlinLogging
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat

@Component
class ScheduleCommands(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleService: ScheduleService,
    private val shardManager: ShardManager,
    private val eventService: EventService
) : ProvidesSlashCommands {

    private val scheduleAutocompleteName: (Schedule) -> String = {
        val channelName = shardManager.getTextChannelById(it.channel)?.name ?: "${it.channel}"
        "$channelName: ${it.eventTime} at ${it.eventDayOfWeek}"
    }

    private val log = KotlinLogging.logger {}

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("schedule") {
                subCommand("add") {
                    val postDayOfWeek by enum<DayOfWeek> {
                        name = "post_day"
                    }.required()
                    val postTime by string {
                        name = "post_time"
                    }.required()
                    val eventDayOfWeek by enum<DayOfWeek> {
                        name = "event_day"
                    }.required()
                    val eventTime by string {
                        name = "event_time"
                    }.required()
                    val message by string {}.required()
                    val channel by textChannel {
                        description = "The channel the message will be posted in"
                    }.optional()


                    run {
                        val realChannel = channel() ?: this.channel as TextChannel
                        val schedule = scheduleService.createNew(
                            user,
                            realChannel,
                            postDayOfWeek(),
                            postTime(),
                            eventDayOfWeek(),
                            eventTime(),
                            message()
                        )
                        reply("Created a new schedule with id ${schedule.id}!").await()
                    }
                }
                subCommand("list") {
                    run {
                        transaction {
                            val schedules = scheduleRepository.findAll()
                            if (schedules.isEmpty()) {
                                reply("No schedules configured!").await()
                                return@transaction
                            }
                            reply {
                                text {
                                    append("The following schedules are configured")
                                    code(buildString {
                                        schedules.forEach { schedule ->
                                            val channelName =
                                                shardManager.getTextChannelById(schedule.id)?.name
                                                    ?: "${schedule.channel}"
                                            append("- ${schedule.id}: #${channelName} ${schedule.eventDayOfWeek} @ ${schedule.eventTime}")
                                        }
                                    })
                                }
                            }.await()
                        }
                    }
                }
                subCommand("remove") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true, autocompleteName = scheduleAutocompleteName
                    ) {
                        description = "The schedule to remove"
                    }.required()

                    run {
                        scheduleService.delete(schedule())
                        reply("Deleted the schedule ${schedule().id}").await()
                    }
                }
                subCommand("get_next") {
                    run {
                        defer(true) {
                            val next = scheduleService.getNextPostTime()
                                ?: throw CommandException("nothing next!")
                            val sdf = SimpleDateFormat("MM-dd-yy HH:mm:ss")
                            it.editOriginal("Next post time is ${sdf.format(next.first.toEpochMilli())} for ${next.second.id}")
                                .await()
                        }
                    }
                }

                subCommand("post") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true, autocompleteName = scheduleAutocompleteName
                    ) {
                        description = "The schedule to post"
                    }.required()
                    run {
                        val event = eventService.createAndPostNextEvent(schedule())
                        reply("Posted ${event.id}").await()
                    }
                }
            }
        }
    }
}