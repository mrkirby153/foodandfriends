package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Calendar


enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

@Entity
@Table(name = "schedule")
class Schedule(
    @Id
    val id: String = generateUlid(),
    @Column(name = "post_day_of_week")
    var postDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    @Column(name = "post_time")
    var postTime: String = "09:00",
    var channel: Long = 0,
    @OneToOne
    @JoinColumn(name = "order_id")
    var order: Order? = null,
) {

    @OneToMany(mappedBy = "schedule")
    var events: MutableList<Event> = mutableListOf()

    @Column(name = "event_day_of_week")
    var eventDayOfWeek: DayOfWeek = DayOfWeek.MONDAY

    @Column(name = "event_time")
    var eventTime: String = "18:00"

    @Column(name = "message")
    var message: String = ""
}

interface ScheduleRepository : JpaRepository<Schedule, String> {

    fun getByPostDayOfWeek(postDayOfWeek: DayOfWeek): List<Schedule>
}