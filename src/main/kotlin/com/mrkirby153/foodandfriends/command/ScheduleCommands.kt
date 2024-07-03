package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.builder.message
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.enum
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.command.slashcommand.dsl.types.textChannel
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.DayOfWeek
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleCadence
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.service.EventService
import com.mrkirby153.foodandfriends.service.ScheduleService
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.TimeZone

@Component
class ScheduleCommands(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleService: ScheduleService,
    private val shardManager: ShardManager,
    private val eventService: EventService,
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
                    ).required()
                    val amount by int { }.optional(1)
                    run {
                        defer(true) {
                            val realSchedule = schedule()

                            val occurrences = mutableListOf<Instant>()

                            var start = Instant.now()
                            for (i in 0 until amount()) {
                                val next = scheduleService.getNextOccurrence(realSchedule, start)
                                occurrences.add(next)
                                start = next.plus(1, ChronoUnit.DAYS)
                            }
                            it.editOriginal(message {
                                text {
                                    appendLine("Next ${amount()} occurrences:")
                                    occurrences.forEach { occ ->
                                        appendLine(
                                            "- <t:${occ.toEpochMilli() / 1000}> (Posts at <t:${
                                                scheduleService.getPostTime(
                                                    schedule(),
                                                    occ
                                                ).toEpochMilli() / 1000
                                            }>)"
                                        )
                                    }
                                }
                            }.edit()).await()
                        }
                    }
                }
                subCommand("next_post") {
                    run {
                        defer(true) {
                            val next = scheduleService.getNextPostTime()
                            if (next != null) {
                                it.editOriginal(message {
                                    text(false) {
                                        appendLine("Next Post: `${next.second.id}` @ <t:${next.first.toEpochMilli() / 1000}>")
                                    }
                                }.edit()).await()
                            } else {
                                it.editOriginal("No next post time!").await()
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

                subCommand("cadence") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true,
                        autocompleteName = scheduleAutocompleteName
                    ) {
                        description = "The schedule to adjust the cadence for"
                    }.required()
                    val cadence by enum<ScheduleCadence> { }.required()
                    run {
                        scheduleService.setCadence(schedule(), cadence())
                        reply("Updated cadence for `${schedule().id}`").await()
                    }
                }

                subCommand("post_offset") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true,
                        autocompleteName = scheduleAutocompleteName
                    ) {
                        description = "The schedule to adjust the cadence for"
                    }.required()
                    val offset by int { }.required()
                    run {
                        scheduleService.setPostOffset(schedule(), offset())
                        reply("Updated the post offset for `${schedule().id}").await()
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