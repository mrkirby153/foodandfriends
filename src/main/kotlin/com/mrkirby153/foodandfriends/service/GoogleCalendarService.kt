package com.mrkirby153.foodandfriends.service

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import com.mrkirby153.foodandfriends.entity.Event
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.RSVPSource
import com.mrkirby153.foodandfriends.entity.RSVPType
import com.mrkirby153.foodandfriends.google.AuthorizationHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import okio.IOException
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import com.google.api.services.calendar.model.Event as GoogleCalendarEvent

interface GoogleCalendarService {

    fun getCalendarEvent(event: Event): GoogleCalendarEvent?

    fun createCalendarEvent(event: Event): GoogleCalendarEvent?

    fun syncEvents()

    fun syncEvent(event: Event)

    fun getNewInvite(event: Event): GoogleCalendarEvent
}

@Service
class GoogleCalendarManager(
    private val authorizationHandler: AuthorizationHandler,
    private val transport: NetHttpTransport,
    private val gsonFactory: GsonFactory,
    private val orderService: OrderService,
    private val eventRepository: EventRepository,
    private val personService: PersonService,
    private val rsvpService: RSVPService
) : GoogleCalendarService {

    private val log = KotlinLogging.logger { }

    @EventListener
    fun onLocationChange(locationChangeEvent: EventLocationChangeEvent) {
        val existing = getCalendarEvent(locationChangeEvent.event)
        if (existing != null) {
            log.debug { "Updating existing event" }
            existing.location = locationChangeEvent.location
            val service = getService(locationChangeEvent.event)
            service.events().update("primary", existing.id, existing).execute()
        } else {
            // Create a new invite
            createCalendarEvent(locationChangeEvent.event)
        }
    }

    @EventListener
    fun onTimeChange(timeChangeEvent: EventTimeChangeEvent) {
        log.debug { "Processing time change event, new time ${timeChangeEvent.newTime}" }
        val existing = getCalendarEvent(timeChangeEvent.event) ?: return
        val service = try {
            getService(timeChangeEvent.event)
        } catch (e: Exception) {
            log.warn(e) { "Could not process time change event" }
            return
        }
        val existingCalendarEvent = try {
            service.events().get("primary", existing.id).execute() ?: return
        } catch (e: IOException) {
            log.warn(e) { "Could not retrieve existing event" }
            return
        }
        val timezone = timeChangeEvent.event.schedule?.timezone ?: TimeZone.getDefault()
        existingCalendarEvent.start =
            EventDateTime().setDateTime(DateTime(timeChangeEvent.event.absoluteDate, timezone))
        val endTime = timeChangeEvent.event.date.toInstant().plus(1, ChronoUnit.HOURS)
        val endDate = Date.from(endTime)
        existingCalendarEvent.end = EventDateTime().setDateTime(DateTime(endDate, timezone))
        val resp = service.events().update("primary", existing.id, existingCalendarEvent).execute()
        log.debug { "Updated the time for the new event! $resp" }
    }

    override fun getCalendarEvent(event: Event): GoogleCalendarEvent? {
        if (event.calendarEventId == null)
            return null
        val calendarUser = event.schedule?.calendarUser ?: return null
        val auth =
            authorizationHandler.getAuthorization(calendarUser) ?: error("No OAuth credentials")
        val service = getService(auth)
        return service.events().get("primary", event.calendarEventId).execute()
    }

    override fun createCalendarEvent(event: Event): com.google.api.services.calendar.model.Event? {
        log.debug { "Sending new event" }
        val service = getService(event)
        val gcalEvent = buildNewInvite(event)
        val newEvent =
            service.events().insert("primary", gcalEvent).setSendUpdates("all")
                .execute()
        event.calendarEventId = newEvent.id
        eventRepository.save(event)
        log.debug { "Created new event with id ${newEvent.id}" }
        return newEvent
    }

    private fun getService(event: Event) =
        getService(
            authorizationHandler.getAuthorization(
                event.schedule?.calendarUser ?: error("No Calendar User")
            ) ?: error("No OAuth credentials")
        )

    private fun getService(credentials: Credential) =
        Calendar.Builder(transport, gsonFactory, credentials).setApplicationName("FoodAndFriends")
            .build()


    private fun buildNewInvite(event: Event): GoogleCalendarEvent {
        log.trace { "Building new invite for ${event.id} (${event.absoluteDate}) at ${event.date} in timezone ${event.schedule?.timezone?.id}" }

        return GoogleCalendarEvent().apply {
            location = event.location
            summary = "Food & Friends"
            start =
                EventDateTime().setDateTime(
                    DateTime(
                        event.absoluteDate,
                        event.schedule?.timezone ?: TimeZone.getDefault()
                    )
                )
            val endTime = event.absoluteDate.toInstant().plus(1, ChronoUnit.HOURS)
            end = EventDateTime().setDateTime(
                DateTime(
                    Date.from(endTime),
                    event.schedule?.timezone ?: TimeZone.getDefault()
                )
            )
            val invites = event.schedule?.order?.run { orderService.getPeople(this) } ?: emptyList()
            val attendees = invites.filter { it.email != null }
                .map { EventAttendee().setEmail(it.email) }.toList()
            log.trace { "Built event ${event.location}: ${attendees.joinToString(", ") { it.email }}" }
            log.trace { "Start: $start / $end" }
            setAttendees(attendees)
        }
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES, initialDelay = 0)
    @Transactional
    override fun syncEvents() {
        log.debug { "Syncing calendar events" }
        val toSync =
            eventRepository.getAllByAbsoluteDateAndCalendarEventIdIsNotNull(Timestamp.from(Instant.now()))
        if (toSync.isEmpty()) {
            log.debug { "No events to sync!" }
        } else {
            log.debug { "Syncing ${toSync.size} event" }
        }

        toSync.forEach { event ->
            syncEvent(event)
        }
    }

    override fun syncEvent(event: Event) {
        if (event.calendarEventId == null)
            return
        log.trace { "Syncing event ${event.id}" }
        val service = try {
            getService(event)
        } catch (e: IllegalStateException) {
            log.warn { "Could not sync event ${event.id}: ${e.message}" }
            return
        }
        val calendarEvent = service.events().get("primary", event.calendarEventId).execute()
        log.trace { "Received event ${calendarEvent.id}" }
        calendarEvent.attendees.forEach attendees@{ eventAttendee ->
            val person = personService.getByEmail(eventAttendee.email)
            if (person == null) {
                log.trace { "No person found for ${eventAttendee.email}" }
                return@attendees
            } else {
                log.trace { "Mapped ${eventAttendee.email} to person ${person.discordUserId}" }
            }

            val response = when (eventAttendee.responseStatus) {
                "needsAction" -> RSVPType.MAYBE
                "declined" -> RSVPType.NO
                "tentative" -> RSVPType.MAYBE
                "accepted" -> RSVPType.YES
                else -> null
            }
            log.trace { "mapped response ${eventAttendee.responseStatus} to $response" }
            if (response == null) {
                log.trace { "Unknown response status ${eventAttendee.responseStatus} for ${eventAttendee.email}" }
                return@attendees
            }
            rsvpService.recordRSVP(event, person, response, RSVPSource.GOOGLE_CALENDAR)
        }
    }

    override fun getNewInvite(event: Event): GoogleCalendarEvent {
        return buildNewInvite(event)
    }
}