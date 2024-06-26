package com.mrkirby153.foodandfriends.service

import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.foodandfriends.entity.DayOfWeek
import com.mrkirby153.foodandfriends.entity.Order
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import kotlinx.coroutines.runBlocking
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.ScheduledFuture

interface ScheduleService {

    fun getNextPostTime(): Pair<Instant, Schedule>?

    suspend fun postScheduleMessage(schedule: Schedule)

    fun createNew(
        owner: User,
        channel: TextChannel,
        postDay: DayOfWeek,
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

    fun getNextOccurrence(schedule: Schedule): Instant

    fun link(schedule: Schedule, order: Order): Schedule

    fun unlink(schedule: Schedule): Schedule

    fun setTimezone(schedule: Schedule, timezone: TimeZone): Schedule
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
        val timezonesToConsider = scheduleRepository.getTimezones()
        log.debug { "There are ${timezonesToConsider.size} timezones to consider" }
        var earliest: Pair<Instant, Schedule>? = null
        timezonesToConsider.forEach { timezone ->
            log.trace { "Processing timezone ${timezone.id}" }
            val now = Calendar.getInstance(timezone)
            val currentDayOfWeek =
                calendarDayMap[now.get(Calendar.DAY_OF_WEEK)] ?: error("No current day of week")
            log.debug { "It is currently $currentDayOfWeek in ${timezone.id}" }
            val calendar = Calendar.getInstance(timezone)
            do {
                val day = calendarDayMap[calendar.get(Calendar.DAY_OF_WEEK)]
                log.trace { "Checking for events on $day in $timezone" }
                if (day != null) {
                    val toPost = scheduleRepository.getByPostDayOfWeekAndTimezone(day, timezone)
                    log.debug { "Found ${toPost.size} events" }
                    if (toPost.isNotEmpty()) {
                        val isToday =
                            calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                        val first = toPost.associateBy {
                            val eventCalendar = calendar.clone() as Calendar
                            val hour = it.postTime.split(":")[0].toInt()
                            val minute = it.postTime.split(":")[1].toInt()
                            eventCalendar.set(Calendar.HOUR_OF_DAY, hour)
                            eventCalendar.set(Calendar.MINUTE, minute)
                            eventCalendar.set(Calendar.SECOND, 0)
                            eventCalendar.set(Calendar.MILLISECOND, 0)

                            if (eventCalendar.toInstant().isBefore(now.toInstant()) && isToday) {
                                log.debug { "Event is today and before now, adding 7 days..." }
                                eventCalendar.add(Calendar.DAY_OF_YEAR, 7)
                            }

                            eventCalendar
                        }.entries.filter {
                            log.debug {
                                "${it.value.id} has active event? ${it.value.activeEvent != null} and ${
                                    it.key.toInstant().isAfter(Instant.now())
                                }"
                            }
                            it.key.toInstant().isAfter(Instant.now()) && it.value.activeEvent?.getTime()?.isBefore(it.key.toInstant()) == true
                        }.minByOrNull { (k, _) -> k }
                        log.debug { "Next event is ${first?.value?.message}" }
                        if (first != null) {
                            if (earliest == null || earliest!!.first.isAfter(first.key.toInstant())) {
                                log.debug { "new earliest event!" }
                                earliest = Pair(first.key.toInstant(), first.value)
                            }
                        }
                    }
                }
                calendar.add(Calendar.DAY_OF_WEEK, 1)
            } while (calendar.get(Calendar.DAY_OF_WEEK) != now.get(Calendar.DAY_OF_WEEK))
        }
        log.debug { "Earliest schedule is: ${earliest?.second?.id}" }
        return earliest
    }

    override suspend fun postScheduleMessage(schedule: Schedule) {
        val next = eventService.createNextEvent(schedule)
        eventService.postEvent(next)
    }

    override fun createNew(
        owner: User,
        channel: TextChannel,
        postDay: DayOfWeek,
        postTime: String,
        eventDay: DayOfWeek,
        eventTime: String,
        message: String,
        timezone: TimeZone
    ): Schedule {
        val schedule =
            Schedule(
                postDayOfWeek = postDay,
                postTime = postTime,
                channel = channel.idLong,
                calendarUser = owner.idLong
            )
        schedule.eventDayOfWeek = eventDay
        schedule.eventTime = eventTime
        schedule.message = message
        val new = scheduleRepository.save(schedule)
        scheduleNextPost()
        return new
    }

    @Transactional
    override fun delete(id: String) {
        scheduleRepository.deleteById(id)
    }

    override fun getNextOccurrence(schedule: Schedule): Instant {
        val calendar = Calendar.getInstance(schedule.timezone)
        while (calendarDayMap[calendar.get(Calendar.DAY_OF_WEEK)] != schedule.eventDayOfWeek) {
            calendar.add(Calendar.DAY_OF_WEEK, 1)
        }
        val hour = schedule.eventTime.split(":")[0].toInt()
        val minute = schedule.eventTime.split(":")[1].toInt()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time.toInstant()
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
        nextTriggerJob = taskExecutor.schedule({ postNext(schedule) }, postAt)
        nextRunAt = postAt
        log.info {
            val sdf = SimpleDateFormat("MM-dd-yy HH:mm:ss")
            val duration = Time.format(1, nextRunAt!!.toEpochMilli() - System.currentTimeMillis())
            "Scheduling run for ${sdf.format(Date.from(postAt))} ($duration)"
        }
    }

    private fun postNext(next: Schedule) {
        log.info {
            "Posting next event for schedule ${next.id}"
        }
        runBlocking {
            eventService.createAndPostNextEvent(next)
        }
        scheduleNextPost()
    }

}