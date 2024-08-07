package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.TimeZone


enum class DayOfWeek(val javaDayOfWeek: java.time.DayOfWeek) {
    MONDAY(java.time.DayOfWeek.MONDAY),
    TUESDAY(java.time.DayOfWeek.TUESDAY),
    WEDNESDAY(java.time.DayOfWeek.WEDNESDAY),
    THURSDAY(java.time.DayOfWeek.THURSDAY),
    FRIDAY(java.time.DayOfWeek.FRIDAY),
    SATURDAY(java.time.DayOfWeek.SATURDAY),
    SUNDAY(java.time.DayOfWeek.SUNDAY);
}

enum class ScheduleCadence {
    WEEKLY,
    FIRST_OF_MONTH
}

@Entity
@Table(name = "schedule")
class Schedule(
    @Id
    val id: String = generateUlid(),
    @Column(name = "post_time")
    var postTime: String = "09:00",
    var channel: Long = 0,
    @OneToOne
    @JoinColumn(name = "order_id")
    var order: Order? = null,

    @Column(name = "calendar_user")
    var calendarUser: Long = 0,

    @Column(name = "timezone")
    var timezone: TimeZone = TimeZone.getTimeZone("UTC")
) {

    @OneToMany(mappedBy = "schedule")
    var events: MutableList<Event> = mutableListOf()

    @Column(name = "event_day_of_week")
    var eventDayOfWeek: DayOfWeek = DayOfWeek.MONDAY

    @Column(name = "event_time")
    var eventTime: String = "18:00"

    @Column(name = "message")
    var message: String = ""

    @Column(name = "log_channel_id")
    var logChannel: Long? = null

    @ManyToOne
    @JoinColumn(name = "active_event")
    var activeEvent: Event? = null
        get() {
            if (field == null) {
                return null
            }
            val event = field!!
            if (event.absoluteDate.toInstant().isBefore(Instant.now())) {
                return null
            }
            return field
        }

    @Column(name = "schedule_cadence_type")
    var cadence: ScheduleCadence = ScheduleCadence.WEEKLY

    @Column(name = "post_offset_days")
    var postOffset: Int = 6
}

interface ScheduleRepository : JpaRepository<Schedule, String> {

    @Query("SELECT DISTINCT(a.timezone) FROM Schedule a")
    fun getTimezones(): List<TimeZone>
}