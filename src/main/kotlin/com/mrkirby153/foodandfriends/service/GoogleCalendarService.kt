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
import com.mrkirby153.foodandfriends.google.AuthorizationHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit
import com.google.api.services.calendar.model.Event as GoogleCalendarEvent

interface GoogleCalendarService {

    fun getCalendarEvent(event: Event): GoogleCalendarEvent?

    fun createCalendarEvent(event: Event): GoogleCalendarEvent?
}

@Service
class GoogleCalendarManager(
    private val authorizationHandler: AuthorizationHandler,
    private val transport: NetHttpTransport,
    private val gsonFactory: GsonFactory,
    private val orderService: OrderService,
    private val eventRepository: EventRepository
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
        log.info { "BUILT: $gcalEvent" }
        val newEvent =
            service.events().insert("primary", buildNewInvite(event)).setSendUpdates("all")
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
        return GoogleCalendarEvent().apply {
            location = event.location
            summary = "Food & Friends"
            start =
                EventDateTime().setDateTime(DateTime(event.date)).setTimeZone("America/Los_Angeles")
            val endTime = event.date.toInstant().plus(1, ChronoUnit.HOURS)
            end = EventDateTime().setDateTime(DateTime(endTime.toEpochMilli()))
                .setTimeZone("America/Los_Angeles")

            val invites = event.schedule?.order?.run { orderService.getPeople(this) } ?: emptyList()
            val attendees = invites.filter { it.email != null }
                .map { EventAttendee().setEmail(it.email) }.toList()
            log.trace { "Built event ${event.location}: ${attendees.joinToString(", ") { it.email }}" }
            log.trace { "Start: $start / $end" }
            setAttendees(attendees)
        }
    }

    private fun syncEvents() {
        TODO("Not implemented yet")
    }
}