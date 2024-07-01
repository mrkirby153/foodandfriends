package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.config.GoogleMapsConfig
import com.mrkirby153.foodandfriends.google.maps.AddressComponent
import com.mrkirby153.foodandfriends.google.maps.GoogleMapsApi
import com.mrkirby153.foodandfriends.google.maps.GoogleMapsApiRequests
import com.mrkirby153.foodandfriends.google.maps.TextSearchResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

interface GoogleMapsService {
    fun isGoogleMapsEnabled(): Boolean

    suspend fun search(query: String): TextSearchResponse

    suspend fun getAddress(placeId: String): String
}

private const val COMMA = "$\$COMMA$$"

@Service
class GoogleMapsManager(
    private val googleMapsConfig: GoogleMapsConfig,
    @Qualifier("mapsHttpClient") private val client: HttpClient,
    @Value("\${google.maps.origin:}") googleMapsOrigin: String?
) : GoogleMapsService {

    private final val addressComponents = arrayOf(
        "street_number",
        "route",
        "subpremise",
        COMMA,
        "locality",
        COMMA,
        "administrative_area_level_1",
        "postal_code"
    )

    private val log = KotlinLogging.logger {}

    private val googleMapsLocation =
        if (googleMapsOrigin?.isBlank() == false) googleMapsOrigin else null

    init {
        log.info { "Initializing Google Maps API Service? ${isGoogleMapsEnabled()}" }
    }

    override fun isGoogleMapsEnabled(): Boolean {
        return googleMapsConfig.isEnabled()
    }

    override suspend fun search(query: String): TextSearchResponse {
        check(isGoogleMapsEnabled()) { "Google Maps API is not enabled" }
        return GoogleMapsApiRequests.Places.search(
            client,
            GoogleMapsApi.Place.TextSearch(
                query = query,
                radius = 50_000,
                location = googleMapsLocation
            )
        )
    }

    override suspend fun getAddress(placeId: String): String {
        val place = GoogleMapsApiRequests.Places.details(
            client,
            GoogleMapsApi.Place.Details(placeId = placeId, fields = listOf("address_components"))
        )
        val components = mutableMapOf<String, MutableList<AddressComponent>>()
        place.result.addressComponents!!.forEach {
            it.types.forEach { type ->
                val sub = components.computeIfAbsent(type) { mutableListOf() }
                sub.add(it)
            }
        }

        return buildString {
            addressComponents.forEach {
                if (it == COMMA) {
                    append(",")
                } else {
                    append(components[it]?.joinToString(" ") { a -> " ${a.shortName}" } ?: "")
                }
            }
        }.trim(' ')
    }
}