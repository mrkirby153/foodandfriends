package com.mrkirby153.foodandfriends.service

import com.mrkirby153.botcore.builder.MessageBuilder
import com.mrkirby153.botcore.builder.message
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.Event
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.RSVPType
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mrkirby153.kcutils.coroutines.runAsync
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.awt.Color
import java.sql.Timestamp

interface EventService {

    @Transactional
    suspend fun postEvent(event: Event): Event

    fun createNextEvent(schedule: Schedule): Event

    fun getByMessage(message: Message) = getByMessage(message.idLong)

    fun getByMessage(messageId: Long): Event?

    fun setLocation(event: Event, location: String)
}

data class EventLocationChangeEvent(val event: Event, val location: String)

@Service
class EventManager(
    private val shardManager: ShardManager,
    private val eventRepository: EventRepository,
    private val scheduleService: ScheduleService,
    private val scheduleRepository: ScheduleRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) : EventService {

    private val log = KotlinLogging.logger { }

    override suspend fun postEvent(event: Event): Event {
        val schedule = event.schedule
        checkNotNull(schedule) { "Event not associated with a schedule" }
        val channel = shardManager.getTextChannelById(schedule.channel)
        checkNotNull(channel) { "Channel not found" }
        val msg = buildMessage(event)
        log.debug { "B" }
        if (event.discordMessageId != 0L) {
            // There already is a message, check if it exists
            try {
                val m = channel.retrieveMessageById(event.discordMessageId).await()
                m.addReaction(Emoji.fromUnicode(THUMBS_UP)).await()
            } catch (e: ErrorResponseException) {
                // Message doesn't exist anymore
                val newMessage = channel.sendMessage(msg.create()).await()
                event.discordMessageId = newMessage.idLong
                newMessage.addReaction(Emoji.fromUnicode(THUMBS_UP)).await()
                return withContext(Dispatchers.IO) {
                    eventRepository.save(event)
                }
            } catch (e: InsufficientPermissionException) {
                error("Missing permissions")
            }
            return event
        } else {
            val newMessage = channel.sendMessage(msg.create()).await()
            newMessage.addReaction(Emoji.fromUnicode(THUMBS_UP)).await()
            event.discordMessageId = newMessage.idLong
            return withContext(Dispatchers.IO) {
                eventRepository.save(event)
            }
        }
    }

    override fun createNextEvent(schedule: Schedule): Event {
        val nextOccurrence = scheduleService.getNextOccurrence(schedule)
        val date = Timestamp.from(nextOccurrence)
        val existing = eventRepository.getByDateAndSchedule(date, schedule)
        if (existing != null) {
            return existing
        }
        val event = Event(date = date)
        event.schedule = schedule
        val newEvent = eventRepository.save(event)
        schedule.activeEvent = newEvent
        scheduleRepository.save(schedule)
        return newEvent
    }

    override fun getByMessage(messageId: Long): Event? {
        return eventRepository.getByDiscordMessageId(messageId)
    }

    override fun setLocation(event: Event, location: String) {
        event.location = location
        val new = eventRepository.save(event)
        applicationEventPublisher.publishEvent(EventLocationChangeEvent(new, location))
    }

    @EventListener
    fun onRsvp(rsvpEvent: RSVPEvent) {
        runAsync {
            transaction {
                val event = eventRepository.getReferenceById(rsvpEvent.event.id)
                val channelId = event.schedule?.channel ?: return@transaction
                val channel = shardManager.getTextChannelById(channelId) ?: return@transaction
                val msg = channel.retrieveMessageById(event.discordMessageId).await()
                log.trace { "Updating message for event ${rsvpEvent.event}" }
                msg.editMessage(buildMessage(event).edit()).await()
            }
        }
    }

    private suspend fun buildMessage(event: Event): MessageBuilder {
        val rsvps = event.attendees.groupBy { it.person }.mapKeys { (person) ->
            shardManager.retrieveUserById(person.discordUserId).await()
        }
        return message {
            text {
                append(event.schedule!!.message)
            }
            if (event.attendees.isNotEmpty())
                embed {
                    title = "Attendees"
                    description = buildString {
                        rsvps.forEach { (user, rsvps) ->
                            appendLine(
                                "${user.asMention}: ${
                                    rsvps.joinToString(" / ") {
                                        getRsvpEmoji(
                                            it.type
                                        )
                                    }
                                }"
                            )
                        }
                    }
                    color {
                        color = Color.blue
                    }
                }
        }
    }

    private fun getRsvpEmoji(type: RSVPType) = when (type) {
        RSVPType.YES -> "✅"
        RSVPType.NO -> "❌"
        RSVPType.MAYBE -> "❓"
    }
}