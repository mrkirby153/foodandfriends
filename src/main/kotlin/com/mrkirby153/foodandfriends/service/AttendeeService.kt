package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.Attendee
import com.mrkirby153.foodandfriends.entity.AttendeeRepo
import com.mrkirby153.foodandfriends.entity.Event
import com.mrkirby153.foodandfriends.kutils.asCompletedFuture
import com.mrkirby153.foodandfriends.kutils.getAll
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.sharding.ShardManager
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

interface AttendeeService {

    fun markGoing(user: User, event: Event)

    fun markNotGoing(user: User, event: Event)

    fun getAttendees(event: Event): CompletableFuture<List<User>>
}

@Service
class AttendeeManager(
    private val shardManager: ShardManager,
    private val attendeeRepo: AttendeeRepo
) : AttendeeService {

    override fun markGoing(user: User, event: Event) {
        if (attendeeRepo.findFirstByEventAndUser(event, user.id) != null) {
            log.debug("Not marking {} as going to {}, as they're already attending", user, event)
            return
        }
        log.debug("Marking {} as going to {}", user, event)
        val attendee = Attendee(user.id, event)
        attendeeRepo.save(attendee)
    }

    override fun markNotGoing(user: User, event: Event) {
        val existing = attendeeRepo.findFirstByEventAndUser(event, user.id)
        existing?.run {
            attendeeRepo.delete(this)
        }
    }

    override fun getAttendees(event: Event): CompletableFuture<List<User>> {
        val attendees = attendeeRepo.findAllByEvent(event)
        if (attendees.isEmpty()) {
            return emptyList<User>().asCompletedFuture()
        }
        return attendees.mapNotNull { it.user }.map { shardManager.retrieveUserById(it) }
            .map { it.submit() }.toList().getAll()
    }

    companion object {
        private val log = LogManager.getLogger()
    }

}