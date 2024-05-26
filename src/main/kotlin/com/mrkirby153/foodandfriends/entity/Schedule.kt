package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid


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
    @Enumerated(EnumType.STRING)
    var postDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    @Column(name = "post_time")
    var postTime: String = "09:00",
    var channel: Long = 0,
    @OneToOne
    @JoinColumn(name = "order_id")
    var order: Order? = null
)