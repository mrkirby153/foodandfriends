package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.service.EventService
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component

@Component
class EventCommands(
    private val scheduleRepository: ScheduleRepository,
    private val eventService: EventService,
    private val shardManager: ShardManager
) : ProvidesSlashCommands {

    private val scheduleAutocompleteName: (Schedule) -> String = {
        val channelName = shardManager.getTextChannelById(it.channel)?.name ?: "${it.channel}"
        "$channelName: ${it.eventTime} at ${it.eventDayOfWeek}"
    }

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.slashCommand("location") {
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
    }

}