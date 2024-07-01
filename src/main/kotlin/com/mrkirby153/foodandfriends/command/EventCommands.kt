package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.builder.message
import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.messageContextCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.confirm
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.botcore.modal.ModalManager
import com.mrkirby153.botcore.modal.await
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.google.maps.PlaceSearchStatus
import com.mrkirby153.foodandfriends.service.EventService
import com.mrkirby153.foodandfriends.service.GoogleMapsService
import io.github.oshai.kotlinlogging.KotlinLogging
import me.mrkirby153.kcutils.spring.coroutine.transaction
import me.mrkirby153.kcutils.ulid.generateUlid
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import kotlin.math.min

@Component
class EventCommands(
    private val scheduleRepository: ScheduleRepository,
    private val eventService: EventService,
    private val shardManager: ShardManager,
    private val googleMapsService: GoogleMapsService,
    private val eventRepository: EventRepository,
    private val modalManager: ModalManager
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
                        transaction {
                            var hook = deferReply(true).await()
                            val realSchedule = schedule()
                            val activeEvent =
                                realSchedule.activeEvent
                                    ?: throw CommandException("No active event")

                            val result = handleGoogleMapsLocation(this@run.user, hook, location())
                            hook = result.first
                            eventService.setLocation(activeEvent, result.second)
                            hook.editOriginal(message {
                                text(true) {
                                    append("Set the location to")
                                    code(result.second)
                                }
                            }.edit()).await()
                        }
                    }
                }
            }
            slashCommand("debug") {
                disableByDefault()
                val schedule by scheduleRepository.argument(
                    enableAutocomplete = true, autocompleteName = scheduleAutocompleteName
                ) {
                    description = "The schedule to set the location for"
                }.required()
                run {
                    transaction {
                        val realSchedule = schedule()
                        reply(true) {
                            text(false) {
                                appendLine("**Schedule Debug Information**")
                                appendLine("Schedule ID: `${realSchedule.id}`")
                                appendLine("Post Day: `${realSchedule.postDayOfWeek}` @ `${realSchedule.postTime}`")
                                appendLine("Timezone: `${realSchedule.timezone.id}`")
                                appendLine()
                                appendLine("**Current Event**")
                                val event = realSchedule.activeEvent
                                if (event != null) {
                                    appendLine("Event ID: `${event.id}`")
                                    appendLine("Google Calendar ID: `${event.calendarEventId ?: "N/A"}`")
                                    val location =
                                        if (event.location?.isNotBlank() == true) {
                                            event.location
                                        } else {
                                            "Not Set"
                                        }
                                    appendLine("Location: `$location`")
                                    appendLine(
                                        "Date: <t:${
                                            event.date.toInstant().toEpochMilli() / 1000
                                        }>"
                                    )
                                    val rsvps = event.attendees.groupBy { it.person }
                                    rsvps.forEach { (person, responses) ->
                                        appendLine(
                                            "<@${person.discordUserId}>: ${
                                                responses.joinToString(",") {
                                                    "${it.rsvpSource}/${it.type}"
                                                }
                                            }"
                                        )
                                    }
                                } else {
                                    appendLine("_No Active Event_")
                                }
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
                }.required()
                val minute by int {
                    min = 0
                    max = 59
                }.required()

                run {
                    val realSchedule = schedule()
                    val activeEvent =
                        realSchedule.activeEvent ?: throw CommandException("No active event")

                    val old = activeEvent.absoluteDate.time
                    val sdf = SimpleDateFormat("yyyy-MM-dd")
                    sdf.timeZone = realSchedule.timezone
                    val calendar = Calendar.getInstance(realSchedule.timezone)
                    calendar.time = activeEvent.date

                    val timestamp = try {
                        sdf.parse(day())
                    } catch (e: ParseException) {
                        throw CommandException("Could not parse `${day()}` as a date")
                    }
                    val parsedCalendar = Calendar.getInstance(realSchedule.timezone)
                    parsedCalendar.time = timestamp

                    log.debug { "Parsed calendar: $parsedCalendar" }
                    log.debug { "SDF: $timestamp" }

                    calendar.set(Calendar.YEAR, parsedCalendar.get(Calendar.YEAR))
                    calendar.set(Calendar.DAY_OF_YEAR, parsedCalendar.get(Calendar.DAY_OF_YEAR))
                    calendar.set(Calendar.HOUR_OF_DAY, hour())
                    calendar.set(Calendar.MINUTE, minute())
                    if (calendar.toInstant().isBefore(Instant.now())) {
                        throw CommandException("Cannot reschedule an event to the past; <t:${calendar.timeInMillis / 1000}>")
                    }
                    eventService.setTime(activeEvent, calendar.toInstant())
                    reply(true) {
                        text {
                            append("Rescheduled from <t:${old / 1000}> to <t:${calendar.timeInMillis / 1000}>!")
                        }
                    }.await()
                }
            }

            messageContextCommand("Set Location") {
                check {
                    if (this.instance.member?.hasPermission(Permission.MANAGE_SERVER) != true)
                        fail("You do not have permission to perform this command")
                }
                action {
                    transaction {
                        val event = eventRepository.getByDiscordMessageId(it.target.idLong)
                            ?: throw CommandException("Event not found")
                        val modal = modalManager.build {
                            title = "Set Event Location"
                            textInput("location") {
                                name = "Location"
                                style = TextInputStyle.PARAGRAPH
                                value =
                                    if (event.location?.isBlank() == true) null else event.location
                                max = 2048
                            }
                        }
                        it.replyModal(modal).await()
                        val modalResult = modalManager.await(modal)
                        val location = modalResult.data["location"]
                        val hook = modalResult.deferReply(true).await()
                        if (location?.isEmpty() == true) {
                            hook.editOriginal("No location specified!").await()
                        } else {
                            val (newHook, resolvedLocation) = handleGoogleMapsLocation(
                                it.user,
                                hook,
                                location!!
                            )
                            eventService.setLocation(event, resolvedLocation)
                            newHook.editOriginal("Updated the location").await()
                        }
                    }

                }
            }
        }
    }

    private suspend fun handleGoogleMapsLocation(
        user: User,
        hook: InteractionHook,
        locationQuery: String
    ): Pair<InteractionHook, String> {
        if (!googleMapsService.isGoogleMapsEnabled()) {
            log.trace { "Google maps is disabled. Returning literal" }
            return Pair(hook, locationQuery)
        }

        log.trace { "Searching google for \"$locationQuery\"" }
        val searchResults = googleMapsService.search(locationQuery)
        val success = searchResults.status == PlaceSearchStatus.OK
        log.trace { "Google returned ${searchResults.results.size}. Success? $success" }
        if (!success) {
            log.trace { "Returning literal, google result was ${searchResults.status}" }
            return Pair(hook, locationQuery)
        }
        val places = searchResults.results

        if (places.isEmpty()) {
            log.trace { "Returning literal, google did not return any places" }
            return Pair(hook, locationQuery)
        }

        if (places.size == 1) {
            // Single result, ask the user to confirm
            val first = places.first()
            val address = first.formattedAddress ?: "No address found"
            val (newHook, confirmed) = hook.confirm(user, true) {
                text {
                    appendLine("Google Maps has resolved the following location:")
                    code(address)
                    if (first.url != null) {
                        appendLine("URL: ${first.url}")
                    }
                    appendLine("Use this location?")
                }
            }
            return if (confirmed) {
                newHook.editOriginal("Using the google suggested location: $address").await()
                Pair(newHook, address)
            } else {
                newHook.editOriginal("Using the provided location: $locationQuery").await()
                Pair(hook, locationQuery)
            }
        } else {
            // Take the first 5 results and display them
            // TODO: Maybe use Interactions Menus here?

            val firstFive = places.subList(0, min(places.size, 4))
            val id = generateUlid()
            val p = firstFive.mapIndexed { index, place -> Pair("${id}-${index}", place) }.toMap()
            var selectId = ""
            hook.editOriginal(message {
                text {
                    appendLine("Google Maps has resolved multiple locations. Please select one")
                }
                actionRow {
                    selectId = select {
                        p.forEach { (id, place) ->
                            option(id) {
                                value = place.formattedAddress ?: "No address found"
                            }
                        }
                    }
                }
            }.edit()).await()

            assert(selectId.isNotEmpty())

            val evt = shardManager.await<StringSelectInteractionEvent> {
                it.componentId == selectId
            }
            val selected = evt.selectedOptions.first()
            return Pair(hook, p[selected.value]?.formattedAddress ?: locationQuery)
        }
    }
}