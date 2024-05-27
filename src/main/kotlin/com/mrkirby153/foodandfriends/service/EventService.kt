package com.mrkirby153.foodandfriends.service

import com.mrkirby153.botcore.builder.MessageBuilder
import com.mrkirby153.botcore.builder.message
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.foodandfriends.entity.Event
import com.mrkirby153.foodandfriends.entity.EventRepository
import com.mrkirby153.foodandfriends.entity.RSVPType
import com.mrkirby153.foodandfriends.entity.Schedule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mrkirby153.kcutils.coroutines.runAsync
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.awt.Color
import java.sql.Timestamp

interface EventService {

    suspend fun postEvent(event: Event): Event

    fun createNextEvent(schedule: Schedule): Event

    fun getByMessage(message: Message) = getByMessage(message.idLong)

    fun getByMessage(messageId: Long): Event?
}

@Service
class EventManager(
    private val shardManager: ShardManager,
    private val eventRepository: EventRepository,
    private val scheduleService: ScheduleService
) : EventService {

    private val log = KotlinLogging.logger { }

    override suspend fun postEvent(event: Event): Event {
        val schedule = event.schedule
        checkNotNull(schedule) { "Event not associated with a schedule" }
        val channel = shardManager.getTextChannelById(schedule.channel)
        checkNotNull(channel) { "Channel not found" }
        if (event.discordMessageId != 0L) {
            // There already is a message, check if it exists
            try {
                val m = channel.retrieveMessageById(event.discordMessageId).await()
                m.addReaction(Emoji.fromUnicode(THUMBS_UP)).await()
            } catch (e: ErrorResponseException) {
                // Message doesn't exist anymore
                val newMessage = channel.sendMessage(buildMessage(event).create()).await()
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
            val newMessage = channel.sendMessage(buildMessage(event).create()).await()
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
        return eventRepository.save(event)
    }

    override fun getByMessage(messageId: Long): Event? {
        return eventRepository.getByDiscordMessageId(messageId)
    }

    @EventListener
    fun onRsvp(rsvpEvent: RSVPEvent) {
        runAsync {
            transaction {
                val event = eventRepository.getReferenceById(rsvpEvent.eventId)
                val channelId = event.schedule?.channel ?: return@transaction
                val channel = shardManager.getTextChannelById(channelId) ?: return@transaction
                val msg = channel.retrieveMessageById(event.discordMessageId).await()
                log.trace { "Updating message for event ${rsvpEvent.eventId}" }
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