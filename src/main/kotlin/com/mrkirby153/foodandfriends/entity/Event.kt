package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import java.sql.Timestamp


@Entity
@Table(name = "event")
class Event(
    @Column(name = "discord_message_id")
    val discordMessageId: Long = 0,
    @Column(name = "calendar_event_id")
    var calendarEventId: String? = null,
    var date: Timestamp = Timestamp(System.currentTimeMillis()),
) {
    @Id
    val id: String = generateUlid()
    var active: Boolean = true

    @ManyToOne
    @JoinColumn(name = "schedule_id")
    var schedule: Schedule? = null
}