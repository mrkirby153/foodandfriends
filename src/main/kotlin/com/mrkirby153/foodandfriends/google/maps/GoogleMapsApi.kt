package com.mrkirby153.foodandfriends.google.maps

import io.ktor.resources.Resource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.mrkirby153.kcutils.ktor.get

@Resource("/maps/api")
class GoogleMapsApi {
    @Resource("place")
    class Place(val parent: GoogleMapsApi = GoogleMapsApi()) {
        @Resource("textsearch/json")
        class TextSearch(
            val parent: Place = Place(),
            val query: String,
            val radius: Int = 50_000,
            val location: String? = null
        )

        @Resource("details/json")
        class Details(
            val parent: Place = Place(),
            @SerialName("place_id")
            val placeId: String,
            @Serializable(PlacesFieldSerializer::class)
            val fields: List<String>
        )

        @Resource("nearbysearch/json")
        class Nearby(
            val parent: Place = Place(),
            val location: String,
            val radius: Int = 100,
            val keyword: String? = null,
            val language: String? = null,
            @SerialName("maxprice")
            val maxPrice: Int? = null,
            @SerialName("minprice")
            val minPrice: Int? = null,
            @SerialName("opennow")
            val openNow: Boolean? = null,
            @SerialName("pagetoken")
            val pageToken: String? = null,
            @SerialName("rankby")
            val rankBy: String? = null,
            @SerialName("type")
            val type: String? = null
        )
    }

    @Resource("timezone/json")
    class TimeZone(
        val location: String,
        val timestamp: String,
        val parent: GoogleMapsApi = GoogleMapsApi()
    )
}


object PlacesFieldSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Places", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<String> {
        val str = decoder.decodeString()
        return str.split(",")
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeString(value.joinToString(","))
    }
}

object GoogleMapsApiRequests {
    object Places {
        val search by get<GoogleMapsApi.Place.TextSearch, TextSearchResponse>()
        val details by get<GoogleMapsApi.Place.Details, PlacesDetailsResponse>()
        val nearby by get<GoogleMapsApi.Place.Nearby, PlacesNearbySearchResponse>()
    }

    object TimeZone {
        val getAtLocation by get<GoogleMapsApi.TimeZone, TimeZoneResponse>()
    }
}