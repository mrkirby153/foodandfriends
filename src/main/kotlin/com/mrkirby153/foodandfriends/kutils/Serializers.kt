package com.mrkirby153.foodandfriends.kutils

import com.mrkirby153.foodandfriends.google.maps.DayOfWeek
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


object GoogleDayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(
            "com.mrkirby153.fnf.kutils.GoogleDayOfWeekSerializer",
            PrimitiveKind.INT
        )

    override fun deserialize(decoder: Decoder): DayOfWeek {
        val value = decoder.decodeInt()
        return DayOfWeek.entries.first { it.day == value }
    }

    override fun serialize(encoder: Encoder, value: DayOfWeek) {
        encoder.encodeInt(value.day)
    }
}