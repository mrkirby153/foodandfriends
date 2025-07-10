package com.mrkirby153.foodandfriends.command

import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.ProvidesSlashCommands
import com.mrkirby153.botcore.command.slashcommand.dsl.messageContextCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.subCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.botcore.modal.ModalManager
import com.mrkirby153.botcore.modal.await
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.google.maps.Place
import com.mrkirby153.foodandfriends.google.maps.PlaceSearchStatus
import com.mrkirby153.foodandfriends.service.EventService
import com.mrkirby153.foodandfriends.service.GoogleMapsService
import com.mrkirby153.interactionmenus.MenuManager
import com.mrkirby153.interactionmenus.StatefulMenu
import io.github.oshai.kotlinlogging.KotlinLogging
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Calendar

@Component
class EventCommands(
    private val scheduleRepository: ScheduleRepository,
    private val eventService: EventService,
    private val shardManager: ShardManager,
    private val googleMapsService: GoogleMapsService,
    private val eventRepository: EventRepository,
    private val modalManager: ModalManager,
    private val menuManager: MenuManager
) : ProvidesSlashCommands {

    private val log = KotlinLogging.logger { }

    private val scheduleAutocompleteName: (Schedule) -> String = {
        val channelName = shardManager.getTextChannelById(it.channel)?.name ?: "${it.channel}"
        "$channelName: ${it.eventTime} at ${it.eventDayOfWeek}"
    }

    override fun registerSlashCommands(executor: DslCommandExecutor) {
        executor.registerCommands {
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
                                appendLine("Post Day: `${realSchedule.postOffset}` days prior @ `${realSchedule.postTime}`")
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

            slashCommand("refresh") {
                defaultPermissions(Permission.MANAGE_SERVER)
                subCommand("event") {
                    val schedule by scheduleRepository.argument(
                        enableAutocomplete = true,
                        autocompleteName = scheduleAutocompleteName
                    ).required()

                    run {
                        val realSchedule = schedule()
                        val activeEvent =
                            realSchedule.activeEvent ?: throw CommandException("No active event")
                        eventService.updateEvent(activeEvent)
                        reply(true) {
                            text {
                                append("Scheduled refresh of event ${activeEvent.id}")
                            }
                        }.await()
                    }
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
                            actionRow {
                                text("location", "Location") {
                                    style(TextInputStyle.PARAGRAPH)
                                    if (event.location?.isBlank() != true) {
                                        value(event.location!!)
                                    }
                                    maxLength(2048)
                                }
                            }
                        }
                        it.replyModal(modal).await()
                        val modalResult = modalManager.await(modal)
                        val location = modalResult.data["location"]
                        val hook = modalResult.deferReply(true).await()
                        if (location?.isEmpty() == true) {
                            hook.editOriginal("No location specified!").await()
                        } else {
                            val eventTime =
                                event.getTime().atZone(event.schedule!!.timezone.toZoneId())
                            handleGoogleMapsLocation(
                                hook,
                                eventTime,
                                location!!
                            ) { location, name ->
                                eventService.setLocation(event, location, name)
                            }
                        }
                    }

                }
            }
        }
    }

    private suspend fun handleGoogleMapsLocation(
        hook: InteractionHook,
        time: ZonedDateTime,
        locationQuery: String,
        resultCallback: (String, String?) -> Unit
    ) {
        val initialPage: GoogleMapsMenuPages
        val places: List<Place>
        if (googleMapsService.isGoogleMapsEnabled()) {
            places = if (googleMapsService.isShareUrl(locationQuery)) {
                googleMapsService.resolveShareURL(locationQuery)
            } else {
                log.trace { "Searching google for \"$locationQuery\"" }
                val searchResults = googleMapsService.search(locationQuery)
                val success = searchResults.status == PlaceSearchStatus.OK
                log.trace { "Google returned ${searchResults.results.size}. Success? $success" }
                if (!success) {
                    emptyList<Place>()
                }
                searchResults.results
            }
            if (places.isEmpty()) {
                initialPage = GoogleMapsMenuPages.LOCATION_NAME
            } else {
                initialPage = if (places.size > 1) {
                    GoogleMapsMenuPages.MANY_RESULTS
                } else {
                    GoogleMapsMenuPages.SINGLE_RESULT
                }
            }
        } else {
            initialPage = GoogleMapsMenuPages.LOCATION_NAME
            places = emptyList()
        }

        val menu = StatefulMenu<GoogleMapsMenuPages, GoogleMapsMenuState>(
            initialPage,
            ::GoogleMapsMenuState
        ) {


            suspend fun verifyHours(place: Place): Boolean {
                val hours =
                    googleMapsService.getOperatingHours(place.placeId!!)
                if (hours.isNotEmpty()) {
                    log.trace { "Place has hours, checking hours" }
                    // Check hours
                    val isOpen =
                        hours.any { it.isOpenDuringPeriod(time) }
                    if (!isOpen) {
                        log.trace { "Place will be closed!" }
                        return false
                    }
                }
                return true
            }


            suspend fun selectPlace(place: Place, verifyHours: Boolean = true) {
                val maybeAsRestaurant = googleMapsService.placeToRestaurant(place)
                val formattedAddress = googleMapsService.getAddress(place.placeId!!)
                state.place = maybeAsRestaurant.ifEmpty {
                    listOf(place)
                }
                state.finalLocation = formattedAddress
                if (!verifyHours(place) && verifyHours) {
                    currentPage = GoogleMapsMenuPages.PLACE_CLOSED
                }
            }


            page(GoogleMapsMenuPages.SINGLE_RESULT) {
                val first = places.first()
                val address = googleMapsService.getAddress(first.placeId!!)
                text {
                    append("Google Maps has resolved the following location:")
                    appendLine("```\n${address}\n```")
                    append("Use this location?")
                }
                actionRow {
                    button("Yes") {
                        style = ButtonStyle.SUCCESS
                        onClick {
                            selectPlace(first)
                            if ((state.place?.size ?: 0) > 1) {
                                currentPage = GoogleMapsMenuPages.MULTIPLE_LOCATION_NAMES
                            } else {
                                currentPage = GoogleMapsMenuPages.LOCATION_NAME
                                state.locationName = state.place?.first()?.name
                            }
                        }
                    }
                    button("No") {
                        style = ButtonStyle.DANGER
                        onClick {
                            // Did not select the location, use what was passed in
                            state.finalLocation = address
                            currentPage = GoogleMapsMenuPages.LOCATION_NAME
                        }
                    }
                }
            }

            page(GoogleMapsMenuPages.MANY_RESULTS) {
                if (places.size > 5) {
                    text("Google Maps has returned more than 5 results. Please refine your query")
                } else {
                    text {
                        appendLine("Google Maps has resolved multiple locations. Please select one")
                    }
                    actionRow {
                        stringSelect {
                            places.forEach { place ->
                                option(place.formattedAddress ?: "No Address Found") {
                                    onSelect {
                                        selectPlace(place)
                                        currentPage = if ((state.place?.size ?: 0) > 1) {
                                            GoogleMapsMenuPages.MULTIPLE_LOCATION_NAMES
                                        } else {
                                            GoogleMapsMenuPages.LOCATION_NAME
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            page(GoogleMapsMenuPages.PLACE_CLOSED) {
                val place = state.place!!.first()
                val hoursStr = googleMapsService.getRawOperatingHours(place.placeId!!)
                text {
                    appendLine(
                        "Google maps has indicated that this place is not open on <t:${
                            time.toEpochSecond()
                        }>"
                    )
                    appendLine("Its reported hours are:")
                    appendLine("```\n${hoursStr.joinToString("\n")}\n```")
                    appendLine("Ignore?")
                }
                actionRow {
                    button("Yes") {
                        style = ButtonStyle.SUCCESS
                        onClick {
                            selectPlace(place, false)
                            currentPage = GoogleMapsMenuPages.LOCATION_NAME
                        }
                    }
                    button("No") {
                        style = ButtonStyle.DANGER
                        onClick {
                            currentPage = GoogleMapsMenuPages.ABORTED
                        }
                    }
                }
            }

            page(GoogleMapsMenuPages.LOCATION_NAME) {
                text {
                    appendLine("Search query resolved to:")
                    if (state.locationName != null) {
                        appendLine("**Name:** ${state.locationName}")
                    }
                    appendLine("**Address:** ${state.finalLocation}")
                }
                actionRow {
                    button("Confirm") {
                        style = ButtonStyle.SUCCESS
                        onClick {
                            resultCallback(state.finalLocation!!, state.locationName)
                            currentPage = GoogleMapsMenuPages.FINAL
                        }
                    }
                    button("Abort") {
                        style = ButtonStyle.DANGER
                        onClick {
                            currentPage = GoogleMapsMenuPages.ABORTED
                        }
                    }
                }
            }

            page(GoogleMapsMenuPages.MULTIPLE_LOCATION_NAMES) {
                text {
                    appendLine("Google Maps has resolved multiple restaurants. Please select one")
                }
                actionRow {
                    stringSelect {
                        (state.place ?: emptyList()).take(4).forEach { place ->
                            option(place.name ?: "No Name") {
                                onSelect {
                                    state.locationName = place.name
                                    currentPage = GoogleMapsMenuPages.LOCATION_NAME
                                }
                            }
                        }
                        option("None of these") {
                            onSelect { hook ->
                                val modal = modalManager.build {
                                    title = "Location Name"
                                    actionRow {
                                        text("location", "Location") {
                                            placeholder("Enter the name of the location")
                                        }
                                    }
                                }

                                hook.displayModal(modal)
                                val results = modalManager.await(modal)
                                results.deferEdit().await()

                                state.locationName = results.data["location"]
                                currentPage = GoogleMapsMenuPages.LOCATION_NAME
                            }
                        }
                    }
                }
            }

            page(GoogleMapsMenuPages.FINAL) {
                text {
                    appendLine("Updated the address to:```")
                    if (state.locationName != null) {
                        appendLine("Name: ${state.locationName}")
                    }
                    appendLine("Address: ${state.finalLocation}")
                    append("```")
                }
            }

            page(GoogleMapsMenuPages.ABORTED) {
                text {
                    append("Aborted!")
                }
            }
        }
        menuManager.show(menu, hook).queue()
    }
}

private data class GoogleMapsMenuState(
    var finalLocation: String? = null,
    var place: List<Place>? = null,
    var locationName: String? = null
)

private enum class GoogleMapsMenuPages {
    SINGLE_RESULT,
    MANY_RESULTS,
    MULTIPLE_LOCATION_NAMES,
    PLACE_CLOSED,
    FINAL,
    ABORTED,
    LOCATION_NAME
}