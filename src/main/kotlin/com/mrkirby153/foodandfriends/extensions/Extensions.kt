package com.mrkirby153.foodandfriends.extensions

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Instant.toLocalTimestamp(zone: ZoneId): Timestamp {
    return Timestamp.valueOf(LocalDateTime.ofInstant(this, zone))
}