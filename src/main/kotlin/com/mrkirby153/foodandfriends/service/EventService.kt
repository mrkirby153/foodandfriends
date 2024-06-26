package com.mrkirby153.foodandfriends.service

import com.mrkirby153.botcore.builder.MessageBuilder
import com.mrkirby153.botcore.builder.message
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.Event
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.RSVPSource
import com.mrkirby153.foodandfriends.entity.RSVPType
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import com.mrkirby153.foodandfriends.extensions.toLocalTimestamp
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.mrkirby153.kcutils.coroutines.runAsync
import me.mrkirby153.kcutils.spring.coroutine.transaction
import me.mrkirby153.kcutils.timing.Debouncer
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.awt.Color
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

interface EventService {

    @Transactional
    suspend fun postEvent(event: Event): Event

    fun createNextEvent(schedule: Schedule): Event

    fun getByMessage(messageId: Long): Event?

    fun setLocation(event: Event, location: String)

    fun setTime(event: Event, timestamp: Instant)

    suspend fun createAndPostNextEvent(schedule: Schedule): Event
}

data class EventLocationChangeEvent(val event: Event, val location: String)
data class EventTimeChangeEvent(val event: Event, val newTime: Instant)

@Service
class EventManager(
    private val shardManager: ShardManager,
    private val eventRepository: EventRepository,
    private val scheduleService: ScheduleService,
    private val scheduleRepository: ScheduleRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    threadFactory: ThreadFactory,
) : EventService {

    private val log = KotlinLogging.logger { }

    private val eventUpdateDebouncer = Debouncer<String>({
        runAsync {
            transaction {
                val event = eventRepository.getReferenceById(it!!)
                update(event)
            }
        }
    }, threadFactory = threadFactory)

    override suspend fun postEvent(event: Event): Event {
        val schedule = event.schedule
        checkNotNull(schedule) { "Event not associated with a schedule" }
        val channel = shardManager.getTextChannelById(schedule.channel)
        checkNotNull(channel) { "Channel not found" }
        val msg = buildMessage(event)
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
        val date = nextOccurrence.toLocalTimestamp(schedule.timezone.toZoneId())
        val existing = eventRepository.getByDateAndSchedule(date, schedule)
        if (existing != null) {
            schedule.activeEvent = existing
            scheduleRepository.save(schedule)
            return existing
        }
        val event = Event(date = date, absoluteDate = Timestamp.from(nextOccurrence))
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
        var new = eventRepository.save(event)
        new = runBlocking {
            update(new)
        }
        applicationEventPublisher.publishEvent(EventLocationChangeEvent(new, location))
    }

    override fun setTime(event: Event, timestamp: Instant) {
        event.date = timestamp.toLocalTimestamp(
            event.schedule?.timezone?.toZoneId() ?: ZoneId.systemDefault()
        )
        event.absoluteDate = Timestamp.from(timestamp)
        var new = eventRepository.save(event)
        new = runBlocking {
            update(new)
        }
        applicationEventPublisher.publishEvent(EventTimeChangeEvent(new, timestamp))
    }

    @Transactional
    override suspend fun createAndPostNextEvent(schedule: Schedule): Event {
        val nextEvent = createNextEvent(schedule)
        postEvent(nextEvent)
        return createAndUpdateLogMessage(nextEvent)
    }

    @EventListener
    fun onRsvp(rsvpEvent: RSVPEvent) {
        eventUpdateDebouncer.debounce(rsvpEvent.event.id, 1, TimeUnit.SECONDS)
    }

    @EventListener
    fun onLocationChange(locationChangeEvent: EventLocationChangeEvent) {
        eventUpdateDebouncer.debounce(locationChangeEvent.event.id, 1, TimeUnit.SECONDS)
    }

    private suspend fun buildMessage(event: Event): MessageBuilder {
        val rsvps = event.attendees.groupBy { it.person }.mapKeys { (person) ->
            shardManager.retrieveUserById(person.discordUserId).await()
        }
        return message {
            text {
                appendLine(event.schedule!!.message)
                if (event.location != null) {
                    appendLine()
                    if (event.location?.isBlank() == false)
                        appendLine("Location: ${event.location}")
                }
            }
            if (event.attendees.isNotEmpty())
                embed {
                    title = "Attendees"
                    description = buildString {
                        rsvps.forEach { (user, rsvps) ->
                            val rsvpsMatch = rsvps.map { it.type }.toSet().size == 1
                            append(user.asMention)
                            append(": ")
                            if (rsvpsMatch) {
                                append(getRsvpEmoji(rsvps.first().type))
                            } else {
                                append(rsvps.sortedBy { it.rsvpSource }.joinToString(" / ") {
                                    val emoji = getRsvpEmoji(it.type)
                                    val abbrev = getProviderAbbreviation(it.rsvpSource)
                                    "$abbrev $emoji"
                                })
                            }
                            appendLine()
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

    private fun getProviderAbbreviation(source: RSVPSource) = when (source) {
        RSVPSource.GOOGLE_CALENDAR -> "G"
        RSVPSource.REACTION -> "D"
    }

    private suspend fun createAndUpdateLogMessage(event: Event): Event {
        return transaction {
            val logChannelId = event.schedule?.logChannel ?: return@transaction event
            val logChannel =
                shardManager.getTextChannelById(logChannelId) ?: return@transaction event

            val message = if (event.logMessageId != null) {
                try {
                    logChannel.retrieveMessageById(event.logMessageId!!).await()
                } catch (e: ErrorResponseException) {
                    if (e.errorResponse == ErrorResponse.UNKNOWN_MESSAGE) {
                        null
                    } else {
                        throw e
                    }
                }
            } else {
                null
            }

            val location =
                if (event.location?.isBlank() == true) "No Location Set" else event.location
            val messageText = "<t:${
                event.absoluteDate.toInstant().toEpochMilli() / 1000
            }>: $location"
            if (message != null) {
                message.editMessage(messageText).await()
                return@transaction event
            } else {
                val newMessage = logChannel.sendMessage(messageText).await()
                event.logMessageId = newMessage.idLong
                return@transaction eventRepository.save(event)
            }
        }
    }

    private suspend fun update(event: Event): Event {
        log.debug { "Editing event ${event.id}" }
        val channelId = event.schedule?.channel ?: return event
        val channel = shardManager.getTextChannelById(channelId) ?: return event
        val msg = channel.retrieveMessageById(event.discordMessageId).await()
        msg.editMessage(buildMessage(event).edit()).await()
        return createAndUpdateLogMessage(event)
    }
}