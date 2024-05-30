package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone


@Entity
@Table(name = "event")
class Event(
    @Column(name = "discord_message_id")
    var discordMessageId: Long = 0,
    @Column(name = "calendar_event_id")
    var calendarEventId: String? = null,
    var date: Timestamp = Timestamp(System.currentTimeMillis()),
) {
    @Id
    val id: String = generateUlid()

    @ManyToOne
    @JoinColumn(name = "schedule_id")
    var schedule: Schedule? = null

    var location: String? = ""

    @OneToMany(mappedBy = "event")
    var attendees: MutableList<RSVP> = mutableListOf()


    fun getTime(): Instant {
        val calendar = Calendar.getInstance(schedule?.timezone ?: TimeZone.getTimeZone("UTC"))
        calendar.time = this.date
        return calendar.toInstant()
    }
}

interface EventRepository : JpaRepository<Event, String> {

    fun getByDateAndSchedule(date: Timestamp, schedule: Schedule): Event?

    fun getByDiscordMessageId(discordMessageId: Long): Event?

    fun getAllByDateAfterAndCalendarEventIdIsNotNull(date: Timestamp): List<Event>
}