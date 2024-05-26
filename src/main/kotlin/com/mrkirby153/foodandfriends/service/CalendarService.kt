package com.mrkirby153.foodandfriends.service

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import com.google.gson.Gson
import com.mrkirby153.foodandfriends.google.AuthorizationHandler
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service

interface CalendarService {

    fun buildCalendarInvite(): Event

    fun getService(user: User): Calendar
}


@Service
class CalendarManager(
    private val transport: NetHttpTransport,
    private val gsonFactory: GsonFactory,
    private val gson: Gson,
    private val authorizationHandler: AuthorizationHandler
) : CalendarService {


    override fun buildCalendarInvite(): Event {
        val event = Event().apply {
            location = "444 De Haro Street, San Francisco"
            summary = "Food & Friends"
        }

        val startTime = DateTime("2024-05-26T18:00:00-07:00")
        val start = EventDateTime().setDateTime(startTime).setTimeZone("America/Los_Angeles")
        event.setStart(start)

        val endTime = DateTime("2024-05-26T19:00:00-07:00")
        val end = EventDateTime().setTimeZone("America/Los_Angeles").setDateTime(endTime)
        event.setEnd(end)

        val attendees = listOf(
            EventAttendee().setEmail("mr.austinwhyte@gmail.com"),
            EventAttendee().setEmail("mrkirby153@mrkirby153.com")
        )
        event.setAttendees(attendees)
        return event
    }

    override fun getService(user: User): Calendar {
        return Calendar.Builder(transport, gsonFactory, authorizationHandler.getAuthorization(user))
            .setApplicationName("Food & Friends").build()
    }

}