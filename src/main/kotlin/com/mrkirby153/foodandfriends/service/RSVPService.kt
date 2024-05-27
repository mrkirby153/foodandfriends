package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.Event
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.Person
import com.mrkirby153.foodandfriends.entity.RSVP
import com.mrkirby153.foodandfriends.entity.RSVPRepository
import com.mrkirby153.foodandfriends.entity.RSVPSource
import com.mrkirby153.foodandfriends.entity.RSVPType
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

interface RSVPService {
    fun getRSVP(event: Event, vararg responses: RSVPType): List<Person>

    fun recordRSVP(event: Event, person: Person, type: RSVPType, source: RSVPSource): Event
}

const val THUMBS_UP = "\uD83D\uDC4D"
const val THUMBS_DOWN = "\uD83D\uDC4E"
const val SHRUG = "\uD83E\uDD37"

data class RSVPEvent(
    val event: Event,
    val rsvp: RSVP,
    val rsvpSource: RSVPSource,
    val rsvpType: RSVPType
)

@Service
class RSVPManager(
    private val eventRepository: EventRepository,
    private val eventService: EventService,
    private val personService: PersonService,
    private val eventPublisher: ApplicationEventPublisher,
    private val rSVPRepository: RSVPRepository
) : RSVPService {

    private val log = KotlinLogging.logger {}

    @Transactional
    override fun getRSVP(event: Event, vararg responses: RSVPType): List<Person> {
        return event.attendees.filter {
            responses.isEmpty() || it.type in responses
        }.map { it.person }
    }

    @Transactional
    override fun recordRSVP(
        event: Event,
        person: Person,
        type: RSVPType,
        source: RSVPSource
    ): Event {
        log.trace {
            "Attendees: ${
                event.attendees.joinToString(", ") { it.id }
            }"
        }
        val existing =
            event.attendees.firstOrNull { it.person.id == person.id && it.rsvpSource == source }
        if (existing != null) {
            log.trace { "Removing existing rsvp with id ${existing.id}" }
            event.attendees.remove(existing)
            rSVPRepository.delete(existing)
        }
        val new = rSVPRepository.saveAndFlush(RSVP(source, type).apply {
            this.person = person
            this.event = event
        })
        log.trace { "Creating RSVP with id ${new.id}" }
        event.attendees.add(new)
        val newEvent = eventRepository.saveAndFlush(event)
        eventPublisher.publishEvent(RSVPEvent(event, new, source, type))
        return newEvent
    }


    @EventListener
    @Transactional
    fun onReaction(reactionEvent: MessageReactionAddEvent) {
        val event = eventService.getByMessage(reactionEvent.messageIdLong) ?: return
        val person = personService.getByUser(reactionEvent.user!!) ?: return
        val type = when (reactionEvent.reaction.emoji.name) {
            THUMBS_UP -> RSVPType.YES
            THUMBS_DOWN -> RSVPType.NO
            SHRUG -> RSVPType.MAYBE
            else -> {
                log.debug { "Unknown reaction by ${reactionEvent.user}, ignoring..." }
                return
            }
        }
        log.debug { "Recording $type for ${reactionEvent.user} to ${event.id}" }
        recordRSVP(event, person, type, RSVPSource.REACTION)
    }

    @EventListener
    @Transactional
    fun onReactionRemove(reactionEvent: MessageReactionRemoveEvent) {
        val event = eventService.getByMessage(reactionEvent.messageIdLong) ?: return
        val person = personService.getByUser(reactionEvent.userIdLong) ?: return
        val type = when (reactionEvent.reaction.emoji.name) {
            THUMBS_UP -> RSVPType.NO
            THUMBS_DOWN -> RSVPType.MAYBE
            else -> {
                log.debug { "Unknown reaction by ${reactionEvent.user}, ignoring..." }
                return
            }
        }
        log.debug { "Recording $type for ${reactionEvent.user} to ${event.id}" }
        recordRSVP(event, person, type, RSVPSource.REACTION)
    }
}