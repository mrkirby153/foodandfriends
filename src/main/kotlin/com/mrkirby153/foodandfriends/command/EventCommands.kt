package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.service.EventService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar

@Component
class EventCommands(
    private val scheduleRepository: ScheduleRepository,
    private val eventService: EventService,
    private val shardManager: ShardManager
) : ProvidesSlashCommands {

    private val log = KotlinLogging.logger { }

    private val scheduleAutocompleteName: (Schedule) -> String = {
        val channelName = shardManager.getTextChannelById(it.channel)?.name ?: "${it.channel}"
        "$channelName: ${it.eventTime} at ${it.eventDayOfWeek}"
    }

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("location") {
                defaultPermissions(Permission.MANAGE_SERVER)
                subCommand("set") {
                    val location by string { }.required()
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true, autocompleteName = scheduleAutocompleteName
                    ) {
                        description = "The schedule to set the location for"
                    }.required()
                    run {
                        val realSchedule = schedule()
                        val activeEvent =
                            realSchedule.activeEvent ?: throw CommandException("No active event")
                        eventService.setLocation(activeEvent, location())
                        reply(true) {
                            text(true) {
                                append("Set the location to")
                                code(location())
                            }
                        }.await()
                    }
                }
            }
            slashCommand("reschedule") {
                defaultPermissions(Permission.MANAGE_SERVER)
                val schedule by scheduleRepository.argument(
                    enableAutocomplete = true,
                    autocompleteName = scheduleAutocompleteName
                ).required()
                val day by string {

                }.required()
                val hour by int {
                    min = 0
                    max = 24
                }.optional()
                val minute by int {
                    min = 0
                    max = 59
                }.optional()

                run {
                    val realSchedule = schedule()
                    val activeEvent =
                        realSchedule.activeEvent ?: throw CommandException("No active event")

                    val old = activeEvent.date.time


                    val sdf = SimpleDateFormat("yyyy-MM-dd")
                    val calendar = Calendar.getInstance(realSchedule.timezone)
                    calendar.time = activeEvent.date

                    val timestamp = try {
                        sdf.parse(day())
                    } catch (e: ParseException) {
                        throw CommandException("Could not parse `${day()}` as a date")
                    }
                    val parsedCalendar = Calendar.getInstance(realSchedule.timezone)
                    parsedCalendar.time = timestamp

                    log.info { "Parsed calendar: $parsedCalendar" }
                    log.info { "SDF: $timestamp" }

                    calendar.set(Calendar.YEAR, parsedCalendar.get(Calendar.YEAR))
                    calendar.set(Calendar.DAY_OF_YEAR, parsedCalendar.get(Calendar.DAY_OF_YEAR))
                    if (hour() != null) {
                        calendar.set(Calendar.HOUR_OF_DAY, hour()!!)
                    }
                    if (minute() != null) {
                        calendar.set(Calendar.MINUTE, minute()!!)
                    }
                    if(calendar.toInstant().isBefore(Instant.now())) {
                        throw CommandException("Cannot reschedule an event to the past; <t:${calendar.timeInMillis / 1000}>")
                    }
                    eventService.setTime(activeEvent, Timestamp.from(calendar.toInstant()))
                    reply(true) {
                        text {
                            append("Rescheduled from <t:${old / 1000}> to <t:${calendar.timeInMillis / 1000}>!")
                        }
                    }.await()
                }
            }
        }
    }

}