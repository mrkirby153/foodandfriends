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
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.service.ScheduleService
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat

@Component
class ScheduleCommands(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleService: ScheduleService,
    private val shardManager: ShardManager
) : ProvidesSlashCommands {

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
                    val message by string {
                    }.required()
                    val channel by textChannel {
                        description = "The channel the message will be posted in"
                    }.optional()


                    run {
                        val realChannel = channel() ?: this.channel as TextChannel
                        val schedule = scheduleService.createNew(
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
                subCommand("remove") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true,
                        autocompleteName = {
                            val channelName =
                                shardManager.getTextChannelById(it.channel)?.name ?: "${it.channel}"
                            "$channelName: ${it.eventTime} at ${it.eventDayOfWeek}"
                        }) {
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
            }
        }
    }
}