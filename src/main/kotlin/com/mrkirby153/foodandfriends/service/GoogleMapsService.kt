package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.config.GoogleMapsConfig
import com.mrkirby153.foodandfriends.google.maps.AddressComponent
import com.mrkirby153.foodandfriends.google.maps.GoogleMapsApi
import com.mrkirby153.foodandfriends.google.maps.GoogleMapsApiRequests
import com.mrkirby153.foodandfriends.google.maps.Place
import com.mrkirby153.foodandfriends.google.maps.PlaceSearchStatus
import com.mrkirby153.foodandfriends.google.maps.TextSearchResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.regex.Pattern

interface GoogleMapsService {
    fun isGoogleMapsEnabled(): Boolean

    suspend fun search(
        query: String,
        location: String? = null,
        radius: Int? = null
    ): TextSearchResponse

    suspend fun getAddress(placeId: String): String

    suspend fun resolveShareURL(url: String): List<Place>

    fun isShareUrl(url: String): Boolean
}

private const val COMMA = "$\$COMMA$$"

private const val SHARE_URL_REGEX = "^https://maps.app.goo.gl/[A-Za-z0-9]+$"
private const val SHARE_URL_REDIRECT_REGEX =
    "^https://www.google.com/maps/place/(.*)/@(-?\\d{1,3}.\\d{0,7},-?\\d{1,3}.\\d{0,7}),.*$"

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

    private val shareUrlClient = HttpClient {
        followRedirects = false
    }

    init {
        log.info { "Initializing Google Maps API Service? ${isGoogleMapsEnabled()}" }
    }

    override fun isGoogleMapsEnabled(): Boolean {
        return googleMapsConfig.isEnabled()
    }

    override suspend fun search(
        query: String,
        location: String?,
        radius: Int?
    ): TextSearchResponse {
        check(isGoogleMapsEnabled()) { "Google Maps API is not enabled" }
        return GoogleMapsApiRequests.Places.search(
            client,
            GoogleMapsApi.Place.TextSearch(
                query = query,
                radius = radius ?: 50_000,
                location = location ?: googleMapsLocation
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

    override suspend fun resolveShareURL(url: String): List<Place> {
        check(isShareUrl(url)) { "\"$url\" is not a valid share url" }
        check(isGoogleMapsEnabled()) { "Google Maps API is not enabled" }

        log.trace { "Resolving share URL $url" }

        val response = shareUrlClient.request(Url(url)) {
            method = HttpMethod.Head
        }
        val redirectUrl = Url(response.headers["Location"] ?: "").toString()
            .decodeURLQueryComponent(plusIsSpace = true)
        log.trace { "$url -> $redirectUrl" }
        val pattern = Pattern.compile(SHARE_URL_REDIRECT_REGEX)
        val match = pattern.matcher(redirectUrl)
        if (!match.find()) {
            log.trace { "No match found for $redirectUrl" }
            return emptyList()
        }

        val name = match.group(1)
        val coordinates = match.group(2)
        log.trace { "Performing search for \"$name\" around $coordinates" }

        val searchResponse = search(name, location = coordinates, radius = 300)
        if (searchResponse.status != PlaceSearchStatus.OK) {
            log.trace { "Search failed: ${searchResponse.status}" }
            return emptyList()
        }
        return searchResponse.results
    }

    override fun isShareUrl(url: String): Boolean {
        return url.matches(Regex(SHARE_URL_REGEX))
    }
}