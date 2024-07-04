package com.mrkirby153.foodandfriends.service

import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.foodandfriends.entity.DayOfWeek
import com.mrkirby153.foodandfriends.entity.Order
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleCadence
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import kotlinx.coroutines.runBlocking
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.data.jpa.domain.AbstractPersistable_.id
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.ScheduledFuture

interface ScheduleService {

    fun getNextPostTime(): Pair<Instant, Schedule>?

    fun getPostTime(schedule: Schedule, eventTime: Instant): Instant

    suspend fun postScheduleMessage(schedule: Schedule)

    fun createNew(
        owner: User,
        channel: TextChannel,
        postTime: String,
        eventDay: DayOfWeek,
        eventTime: String,
        message: String,
        timezone: TimeZone
    ): Schedule

    fun delete(schedule: Schedule) {
        delete(schedule.id)
    }

    fun delete(id: String)

    fun getNextOccurrence(schedule: Schedule, startTime: Instant = Instant.now()): Instant

    fun link(schedule: Schedule, order: Order): Schedule

    fun unlink(schedule: Schedule): Schedule

    fun setTimezone(schedule: Schedule, timezone: TimeZone): Schedule

    fun setCadence(schedule: Schedule, cadence: ScheduleCadence): Schedule

    fun setPostOffset(schedule: Schedule, offset: Int): Schedule
}

@Service
class ScheduleManager(
    private val scheduleRepository: ScheduleRepository,
    @Lazy private val eventService: EventService,
    private val taskExecutor: TaskScheduler
) : ScheduleService {

    private val calendarDayMap = mutableMapOf<Int, DayOfWeek>()
    private val log = KotlinLogging.logger { }

    private var nextTriggerJob: ScheduledFuture<*>? = null
    private var nextRunAt: Instant? = null

    init {
        calendarDayMap[Calendar.MONDAY] = DayOfWeek.MONDAY
        calendarDayMap[Calendar.TUESDAY] = DayOfWeek.TUESDAY
        calendarDayMap[Calendar.WEDNESDAY] = DayOfWeek.WEDNESDAY
        calendarDayMap[Calendar.THURSDAY] = DayOfWeek.THURSDAY
        calendarDayMap[Calendar.FRIDAY] = DayOfWeek.FRIDAY
        calendarDayMap[Calendar.SATURDAY] = DayOfWeek.SATURDAY
        calendarDayMap[Calendar.SUNDAY] = DayOfWeek.SUNDAY
    }


    override fun getNextPostTime(): Pair<Instant, Schedule>? {
        log.debug { "Looking up next post time" }

        val timesBySchedule = scheduleRepository.findAll().associateBy { schedule ->
            val nextEvent = if (schedule.activeEvent != null) {
                getNextOccurrence(schedule, schedule.activeEvent!!.getTime())
            } else {
                getNextOccurrence(schedule)
            }
            getPostTime(schedule, nextEvent)
        }

        val next = timesBySchedule.minByOrNull { it.key }
        log.debug { "Next post is ${next?.value?.id} @ ${next?.key}" }
        return next?.run {
            Pair(this.key, this.value)
        }
    }

    override fun getPostTime(schedule: Schedule, eventTime: Instant): Instant {
        val hour = schedule.postTime.split(":")[0].toInt()
        val minute = schedule.postTime.split(":")[1].toInt()
        val postTime =
            eventTime.atZone(schedule.timezone.toZoneId()).withHour(hour).withMinute(minute)
                .withSecond(0).minusDays(schedule.postOffset.toLong())
        return postTime.toInstant()
    }

    override suspend fun postScheduleMessage(schedule: Schedule) {
        val next = eventService.createNextEvent(schedule)
        eventService.postEvent(next)
    }

    override fun createNew(
        owner: User,
        channel: TextChannel,
        postTime: String,
        eventDay: DayOfWeek,
        eventTime: String,
        message: String,
        timezone: TimeZone
    ): Schedule {
        val schedule =
            Schedule(
                postTime = postTime,
                channel = channel.idLong,
                calendarUser = owner.idLong
            )
        schedule.eventDayOfWeek = eventDay
        schedule.eventTime = eventTime
        schedule.message = message
        schedule.postOffset = 6 // Post a week before
        val new = scheduleRepository.save(schedule)
        scheduleNextPost()
        return new
    }

    @Transactional
    override fun delete(id: String) {
        scheduleRepository.deleteById(id)
    }

    override fun getNextOccurrence(schedule: Schedule, startTime: Instant): Instant {
        val hour = schedule.eventTime.split(":")[0].toInt()
        val minute = schedule.eventTime.split(":")[1].toInt()
        when (schedule.cadence) {
            ScheduleCadence.WEEKLY -> {
                val now = LocalDateTime.ofInstant(startTime, schedule.timezone.toZoneId()).truncatedTo(ChronoUnit.SECONDS)
                log.trace { "It is $now" }
                val eventTime =
                    now.with(TemporalAdjusters.next(schedule.eventDayOfWeek.javaDayOfWeek))
                        .withHour(hour).withMinute(minute).withSecond(0)
                log.trace { "The event is at $eventTime" }
                val final = eventTime.atZone(schedule.timezone.toZoneId()).toInstant()
                log.trace { "The final time is $final" }
                return final
            }

            ScheduleCadence.FIRST_OF_MONTH -> {
                val now = LocalDateTime.ofInstant(startTime, schedule.timezone.toZoneId()).truncatedTo(ChronoUnit.SECONDS)
                log.trace { "It is $now" }
                val adjuster = TemporalAdjusters.firstInMonth(schedule.eventDayOfWeek.javaDayOfWeek)
                var eventTime =
                    now.with(adjuster).withHour(hour).withMinute(minute).withSecond(0)
                while (eventTime.isBefore(now)) {
                    log.trace { "The event ($eventTime) is before $now, moving forward 1 month" }
                    eventTime = eventTime.plusMonths(1).with(adjuster)
                }
                val final = eventTime.atZone(schedule.timezone.toZoneId()).toInstant()
                log.trace { "The event is at $final" }
                return final
            }
        }
    }

    override fun link(schedule: Schedule, order: Order): Schedule {
        schedule.order = order
        order.schedule = schedule
        return scheduleRepository.save(schedule)
    }

    override fun unlink(schedule: Schedule): Schedule {
        schedule.order = null
        return scheduleRepository.save(schedule)
    }

    override fun setTimezone(schedule: Schedule, timezone: TimeZone): Schedule {
        schedule.timezone = timezone
        val newSchedule = scheduleRepository.save(schedule)
        scheduleNextPost()
        return newSchedule
    }

    override fun setCadence(schedule: Schedule, cadence: ScheduleCadence): Schedule {
        schedule.cadence = cadence
        val result = scheduleRepository.save(schedule)
        scheduleNextPost()
        return result
    }

    override fun setPostOffset(schedule: Schedule, offset: Int): Schedule {
        schedule.postOffset = offset
        val result = scheduleRepository.save(schedule)
        scheduleNextPost()
        return result
    }

    @EventListener
    fun onReady(event: BotReadyEvent) {
        scheduleNextPost()
    }

    private fun scheduleNextPost() {
        val next = getNextPostTime()
        if (next == null) {
            log.debug { "No schedule to post, running again in 1 hour" }
            nextTriggerJob?.cancel(true)
            nextRunAt = Instant.now().plus(1, ChronoUnit.HOURS)
            log.debug {
                "Running in ${
                    Time.format(
                        1,
                        nextRunAt!!.toEpochMilli() - System.currentTimeMillis()
                    )
                }"
            }
            nextTriggerJob = taskExecutor.schedule({
                scheduleNextPost()
            }, nextRunAt!!)
            return
        }
        val postAt = next.first
        val schedule = next.second

        if (nextRunAt != null && nextRunAt?.isAfter(postAt) == true) {
            // There is a newer event, reschedule
            log.debug { "Rescheduling post task" }
            nextTriggerJob?.cancel(true)
            nextTriggerJob = null
            nextRunAt = null
        }
        nextTriggerJob = taskExecutor.schedule({ postNext(schedule.id) }, postAt)
        nextRunAt = postAt
        log.info {
            val sdf = SimpleDateFormat("MM-dd-yy HH:mm:ss")
            val duration = Time.format(1, nextRunAt!!.toEpochMilli() - System.currentTimeMillis())
            "Scheduling run for ${sdf.format(Date.from(postAt))} ($duration)"
        }
    }

    private fun postNext(nextId: String) {
        log.info {
            "Posting next event for schedule $nextId"
        }
        val next = scheduleRepository.findByIdOrNull(nextId)
        if (next == null) {
            log.info { "Schedule $id not found" }
        } else {
            runBlocking {
                eventService.createAndPostNextEvent(next)
            }
        }
        scheduleNextPost()
    }
}