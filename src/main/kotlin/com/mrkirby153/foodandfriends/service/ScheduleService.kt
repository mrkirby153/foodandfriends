package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.DayOfWeek
import com.mrkirby153.foodandfriends.entity.Schedule
import com.mrkirby153.foodandfriends.entity.ScheduleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Calendar

interface ScheduleService {

    fun getNextPostTime(): Pair<Instant, Schedule>?

    fun postScheduleMessage(schedule: Schedule)

    fun createNew(
        channel: TextChannel,
        postDay: DayOfWeek,
        postTime: String,
        eventDay: DayOfWeek,
        eventTime: String,
        message: String
    ): Schedule

    fun delete(schedule: Schedule) {
        delete(schedule.id)
    }

    fun delete(id: String)

    fun getNextOccurrence(schedule: Schedule): Instant
}

@Service
class ScheduleManager(
    private val scheduleRepository: ScheduleRepository,
) : ScheduleService {

    private val calendarDayMap = mutableMapOf<Int, DayOfWeek>()
    private val log = KotlinLogging.logger { }

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
        val now = Calendar.getInstance()
        val currentDayOfWeek = calendarDayMap[Calendar.getInstance().get(Calendar.DAY_OF_WEEK)]
            ?: error("No current day of week")
        log.debug { "it is currently $currentDayOfWeek" }
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_WEEK, 1)
        while (calendar.get(Calendar.DAY_OF_WEEK) != now.get(Calendar.DAY_OF_WEEK)) {
            val day = calendarDayMap[calendar.get(Calendar.DAY_OF_WEEK)]
            log.debug { "Checking for events on $day" }
            if (day != null) {
                val toPost = scheduleRepository.getByPostDayOfWeek(day)
                log.debug { "Found ${toPost.size} events" }
                if (toPost.isNotEmpty()) {
                    val first = toPost.associateBy {
                        val eventCalendar = calendar.clone() as Calendar
                        val hour = it.postTime.split(":")[0].toInt()
                        val minute = it.postTime.split(":")[1].toInt()
                        eventCalendar.set(Calendar.HOUR_OF_DAY, hour)
                        eventCalendar.set(Calendar.MINUTE, minute)
                        eventCalendar
                    }.entries.minByOrNull { (k, _) -> k }
                    log.debug { "Next event is ${first?.value?.message}" }
                    if (first != null) {
                        return Pair(first.key.toInstant(), first.value)
                    }
                }
            }
            calendar.add(Calendar.DAY_OF_WEEK, 1)
        }
        return null
    }

    override fun postScheduleMessage(schedule: Schedule) {
        TODO("Not yet implemented")
    }

    override fun createNew(
        channel: TextChannel,
        postDay: DayOfWeek,
        postTime: String,
        eventDay: DayOfWeek,
        eventTime: String,
        message: String
    ): Schedule {
        val schedule =
            Schedule(postDayOfWeek = postDay, postTime = postTime, channel = channel.idLong)
        schedule.eventDayOfWeek = eventDay
        schedule.eventTime = eventTime
        schedule.message = message
        return scheduleRepository.save(schedule)
    }

    @Transactional
    override fun delete(id: String) {
        scheduleRepository.deleteById(id)
    }

    override fun getNextOccurrence(schedule: Schedule): Instant {
        val calendar = Calendar.getInstance()
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

}