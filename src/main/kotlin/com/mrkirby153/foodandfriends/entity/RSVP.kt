package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository


enum class RSVPSource {
    REACTION,
    GOOGLE_CALENDAR
}

enum class RSVPType {
    YES,
    NO,
    MAYBE
}

@Table(name = "rsvp")
@Entity
class RSVP(
    @Column(name = "rsvp_source")
    val rsvpSource: RSVPSource = RSVPSource.REACTION,

    @Column(name = "type")
    var type: RSVPType = RSVPType.MAYBE
) {
    @Id
    val id: String = generateUlid()

    @ManyToOne
    @JoinColumn(name = "event_id")
    lateinit var event: Event

    @ManyToOne
    @JoinColumn(name = "person")
    lateinit var person: Person
}

interface RSVPRepository : JpaRepository<RSVP, RSVP> {

}