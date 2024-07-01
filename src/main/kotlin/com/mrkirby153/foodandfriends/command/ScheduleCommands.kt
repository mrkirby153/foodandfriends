package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.messageContextCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.enum
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.command.slashcommand.dsl.types.textChannel
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.botcore.modal.ModalManager
import com.mrkirby153.botcore.modal.await
import com.mrkirby153.foodandfriends.entity.DayOfWeek
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.service.EventManager
import com.mrkirby153.foodandfriends.service.EventService
import com.mrkirby153.foodandfriends.service.ScheduleService
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.TimeZone

@Component
class ScheduleCommands(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleService: ScheduleService,
    private val shardManager: ShardManager,
    private val eventService: EventService,
    private val modalManager: ModalManager,
    private val eventRepository: EventRepository,
) : ProvidesSlashCommands {

    private val scheduleAutocompleteName: (Schedule) -> String = {
        val channelName = shardManager.getTextChannelById(it.channel)?.name ?: "${it.channel}"
        "$channelName: ${it.eventTime} at ${it.eventDayOfWeek}"
    }

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("schedule") {
                defaultPermissions(Permission.MANAGE_SERVER)
                subCommand("add") {
                    val timezone by string {
                    }.required()
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
                        val timezone = TimeZone.getTimeZone(timezone())

                        val schedule = scheduleService.createNew(
                            user,
                            realChannel,
                            postDayOfWeek(),
                            postTime(),
                            eventDayOfWeek(),
                            eventTime(),
                            message(),
                            timezone
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
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true,
                        autocompleteName = scheduleAutocompleteName
                    ).optional()
                    run {
                        defer(true) {
                            val realSchedule = schedule()
                            if (realSchedule != null) {
                                val nextEvent = scheduleService.getNextOccurrence(realSchedule)
                                it.editOriginal("Next occurrence is <t:${nextEvent.toEpochMilli() / 1000}>")
                                    .await()
                            } else {
                                val next = scheduleService.getNextPostTime()
                                    ?: throw CommandException("nothing next!")
                                val sdf = SimpleDateFormat("MM-dd-yy HH:mm:ss")
                                val nextTime = scheduleService.getNextOccurrence(next.second)
                                it.editOriginal("Next post time is ${sdf.format(next.first.toEpochMilli())} (<t:${next.first.toEpochMilli() / 1000}>) for ${next.second.id} (<t:${nextTime.toEpochMilli() / 1000}>)")
                                    .await()
                            }
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

                subCommand("timezone") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true,
                        autocompleteName = scheduleAutocompleteName
                    ).required()
                    val timezone by string {

                    }.optional()
                    run {
                        if (timezone() != null) {
                            val newTimezone = TimeZone.getTimeZone(timezone())
                            scheduleService.setTimezone(schedule(), newTimezone)
                            reply("Set the timezone for ${schedule().id} to `${newTimezone.displayName}`").await()
                        } else {
                            reply("${schedule().id}'s timezone is `${schedule().timezone.displayName}`").await()
                        }
                    }
                }
                subCommand("log-channel") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true,
                        autocompleteName = scheduleAutocompleteName
                    ).required()
                    val channel by textChannel { }.optional()
                    run {
                        val realSchedule = schedule()
                        realSchedule.logChannel = channel()?.idLong
                        scheduleRepository.save(realSchedule)
                        if (channel() == null) {
                            reply("Cleared the log channel for `${realSchedule.id}`").await()
                        } else {
                            reply("Set the log channel for `${realSchedule.id}` to ${channel()!!.asMention}").await()
                        }
                    }
                }
            }

        }
    }
}