package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.SentAnnouncementMessage
import com.mrkirby153.foodandfriends.entity.SentMessageRepo
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import net.dv8tion.jda.api.sharding.ShardManager
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.concurrent.ScheduledFuture
import java.util.stream.Collectors

interface EventService {

    fun sendMessage()

    fun onReact(event: MessageReactionAddEvent)

    fun onUnreact(event: MessageReactionRemoveEvent)
}

const val THUMBS_UP = "\uD83D\uDC4D"

@Service
class EventManager(
    private val shardManager: ShardManager,
    private val localizationService: LocalizationService,
    private val sentAnnouncementMessageRepo: SentMessageRepo,
    private val scheduler: TaskScheduler,
    @Value("\${bot.announcement-channel}") private val channelId: String
) : EventService {

    private val channel
        get() = shardManager.getTextChannelById(channelId)
            ?: throw IllegalArgumentException("Channel $channelId not found")

    private val updateFuture: MutableMap<String, ScheduledFuture<*>> = mutableMapOf()

    companion object {
        private val log = LogManager.getLogger()
    }

    fun buildMessage(attendees: List<User>): String {
        return buildString {
            appendLine(localizationService.translate("chat.header"))
            if (attendees.isNotEmpty()) {
                appendLine()
                appendLine(
                    localizationService.translate(
                        "chat.body",
                        attendees.joinToString("\n") { it.asMention })
                )
            }
        }
    }

    @Scheduled(cron = "\${bot.announce-cron:0 0 9 * * 2}", zone = "\${bot.announce-cron-zone:UTC}")
    override fun sendMessage() {
        log.debug("Sending F&F message")
//        channel.sendMessage(buildMessage(emptyList())).allowedMentions(emptyList()).queue { msg ->
//            msg.addReaction(THUMBS_UP).queue()
//            val entity = SentAnnouncementMessage(messageId = msg.id, channelId = msg.channel.id)
//            sentAnnouncementMessageRepo.save(entity)
//        }
    }

    @EventListener
    override fun onReact(event: MessageReactionAddEvent) {
        handleReactionChange(event)
    }

    @EventListener
    override fun onUnreact(event: MessageReactionRemoveEvent) {
        handleReactionChange(event)
    }

    private fun handleReactionChange(event: GenericMessageReactionEvent) {
//        if (event.reactionEmote.emoji == THUMBS_UP && event.user?.isBot == false) {
//            sentAnnouncementMessageRepo.getFirstByMessageId(event.messageId) ?: return
//            if (updateFuture[event.messageId] == null) {
//                log.debug("Queueing update for {}", event.messageId)
//                updateFuture[event.messageId] =
//                    scheduler.schedule(
//                        { updateMessage(event.messageId) },
//                        Date(System.currentTimeMillis() + 5000)
//                    )
//            }
//        }
    }


    private fun updateMessage(id: String) {
//        log.debug("Updating message {}", id)
//        try {
//            val savedMsg = sentAnnouncementMessageRepo.getFirstByMessageId(id) ?: return
//            val chan = shardManager.getTextChannelById(savedMsg.channelId) ?: return
//            chan.retrieveMessageById(id).queue { msg ->
//                scheduler.schedule({
//                    val users =
//                        msg.retrieveReactionUsers(THUMBS_UP).stream()
//                            .filter { it != msg.jda.selfUser }.collect(Collectors.toList())
//                    log.debug("Users: {}", users.joinToString(", "))
//                    msg.editMessage(buildMessage(users)).allowedMentions(emptyList()).queue()
//                }, Instant.now())
//            }
//        } finally {
//            updateFuture.remove(id)
//        }
    }
}