package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.config.GoogleMapsConfig
import com.mrkirby153.foodandfriends.google.maps.AddressComponent
import com.mrkirby153.foodandfriends.google.maps.DayOfWeek
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
import java.time.ZonedDateTime
import java.util.TimeZone
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

    suspend fun getOperatingHours(placeId: String): List<OpeningPeriod>

    suspend fun getRawOperatingHours(placeId: String): List<String>

    suspend fun getTimezoneAtLocation(placeId: String): TimeZone

    suspend fun getEstablishmentName(placeId: String): String?

    suspend fun placeToRestaurant(place: Place): List<Place>

    suspend fun getAddressComponents(place: Place): List<AddressComponent>

    fun isShareUrl(url: String): Boolean
}

private const val COMMA = "$\$COMMA$$"
private const val TIME_PERIOD_RE = "(\\d{2})(\\d{2})"

private const val SHARE_URL_REGEX = "^https://maps.app.goo.gl/[A-Za-z0-9]+$"
private const val SHARE_URL_REDIRECT_REGEX =
    "^https://www.google.com/maps/place/(.*)/@(-?\\d{1,3}.\\d{0,7},-?\\d{1,3}.\\d{0,7}),.*$"

private val DAY_OF_WEEK_MAP = mapOf(
    java.time.DayOfWeek.MONDAY to DayOfWeek.MONDAY,
    java.time.DayOfWeek.TUESDAY to DayOfWeek.TUESDAY,
    java.time.DayOfWeek.WEDNESDAY to DayOfWeek.WEDNESDAY,
    java.time.DayOfWeek.THURSDAY to DayOfWeek.THURSDAY,
    java.time.DayOfWeek.FRIDAY to DayOfWeek.FRIDAY,
    java.time.DayOfWeek.SATURDAY to DayOfWeek.SATURDAY,
    java.time.DayOfWeek.SUNDAY to DayOfWeek.SUNDAY
)


data class TimePeriod(val day: DayOfWeek, private val time: String) {
    val hour: Int
    val minute: Int

    init {
        val pattern = Pattern.compile(TIME_PERIOD_RE)
        val matcher = pattern.matcher(time)
        check(matcher.find()) { "Invalid time unit $time" }
        hour = matcher.group(1).toInt()
        minute = matcher.group(2).toInt()
    }
}


data class OpeningPeriod(
    val open: TimePeriod,
    val close: TimePeriod,
    private val timezone: TimeZone
) {

    private val log = KotlinLogging.logger {}

    fun isOpenDuringPeriod(time: ZonedDateTime): Boolean {
        val dayOfWeek = DAY_OF_WEEK_MAP[time.dayOfWeek]!!
        log.trace { "Comparing $time ($dayOfWeek) to $open and $close" }

        val localTime = time.withZoneSameInstant(timezone.toZoneId())

        log.trace { "Converted $time to $localTime" }


        // Open and close are on the same day
        if (open.day == close.day) {
            if (dayOfWeek != open.day)
                return false
            // time must be between hour and minute
            val hour = localTime.hour
            val minute = localTime.minute
            return hour >= open.hour && minute >= open.minute && hour <= close.hour && minute <= close.minute
        } else {
            // Close is after open and on different days
            assert(open.day.day < close.day.day)
            val hour = localTime.hour
            val minute = localTime.minute

            return when (dayOfWeek) {
                open.day -> {
                    // We care about opening to 23:59
                    hour >= open.hour && minute >= open.minute && hour <= 23 && minute <= 59
                }

                close.day -> {
                    // We care about midnight to close
                    hour <= close.hour && minute <= close.minute
                }

                else -> {
                    // Day of week is not any of these times
                    log.trace { "Day of week is not in range" }
                    false
                }
            }
        }
    }
}

@Service
class GoogleMapsManager(
    private val googleMapsConfig: GoogleMapsConfig,
    @Qualifier("mapsHttpClient") private val client: HttpClient,
    @Value("\${google.maps.origin:}") googleMapsOrigin: String?,
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
        log.info { "Google Maps API Origin: $googleMapsLocation" }
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

    override suspend fun getOperatingHours(placeId: String): List<OpeningPeriod> {
        val place = GoogleMapsApiRequests.Places.details(
            client,
            GoogleMapsApi.Place.Details(placeId = placeId, fields = listOf("opening_hours"))
        )
        return place.result.openingHours?.periods?.map {
            log.trace { "Operating ${it.open.day} @ ${it.open.time} -> ${it.close.day} @ ${it.close.time}" }
            OpeningPeriod(
                TimePeriod(it.open.day, it.open.time),
                TimePeriod(it.close.day, it.close.time),
                getTimezoneAtLocation(placeId)
            )
        } ?: emptyList()
    }

    override suspend fun getRawOperatingHours(placeId: String): List<String> {
        val place = GoogleMapsApiRequests.Places.details(
            client,
            GoogleMapsApi.Place.Details(placeId = placeId, fields = listOf("opening_hours"))
        )
        return place.result.openingHours?.weekdayText ?: emptyList()
    }

    override suspend fun getTimezoneAtLocation(placeId: String): TimeZone {
        val place = GoogleMapsApiRequests.Places.details(
            client,
            GoogleMapsApi.Place.Details(placeId = placeId, fields = listOf("geometry"))
        )
        val location = place.result.geometry!!.location
        val timezone = GoogleMapsApiRequests.TimeZone.getAtLocation(
            client,
            GoogleMapsApi.TimeZone(
                location = "${location.lat},${location.long}",
                timestamp = "${System.currentTimeMillis() / 1000}"
            )
        )
        return TimeZone.getTimeZone(timezone.timeZoneId)
    }

    override suspend fun getEstablishmentName(placeId: String): String? {
        val place = GoogleMapsApiRequests.Places.details(
            client,
            GoogleMapsApi.Place.Details(placeId = placeId, fields = listOf("name"))
        )
        return place.result.name
    }

    override suspend fun placeToRestaurant(place: Place): List<Place> {
        log.trace { "Turning address place ${place.adrAddress}" }
        val addressComponents = place.addressComponents ?: getAddressComponents(place)
        if (addressComponents.isEmpty()) {
            log.trace { "Place has no address components" }
            return emptyList()
        }
        val nearby = GoogleMapsApiRequests.Places.nearby(
            client,
            GoogleMapsApi.Place.Nearby(
                location = place.geometry!!.location.toHumanReadable(),
                type = "restaurant",
                radius = 50
            )
        )
        if (nearby.status != PlaceSearchStatus.OK) {
            log.trace { "Failed to find nearby places" }
            return emptyList()
        }
        log.trace { "Discovered ${nearby.results.size} nearby restaurants" }
        log.trace {
            buildString {
                nearby.results.forEach {
                    appendLine(" - ${it.name} (${it.placeId})")
                }
            }
        }

        return nearby.results.filter {
            val components = GoogleMapsApiRequests.Places.details(
                client,
                GoogleMapsApi.Place.Details(
                    placeId = it.placeId!!,
                    fields = listOf("address_components")
                )
            ).result.addressComponents ?: return@filter false
            compareAddressComponents(components, addressComponents)
        }
    }

    override suspend fun getAddressComponents(place: Place): List<AddressComponent> {
        log.trace { "Fetching address components for ${place.placeId}" }
        val place = GoogleMapsApiRequests.Places.details(
            client,
            GoogleMapsApi.Place.Details(placeId = place.placeId!!, fields = listOf("address_components"))
        )
        return place.result.addressComponents ?: emptyList()
    }


    private fun compareAddressComponents(
        first: List<AddressComponent>,
        second: List<AddressComponent>
    ): Boolean {
        val firstMap = first.associateBy { it.types.joinToString(".") }
        val secondMap = second.associateBy { it.types.joinToString(".") }
        // We only care about the keys matching because the second map may be more specific
        return firstMap.all { (key, value) ->
            value.longName == secondMap[key]?.longName
        }
    }

    override fun isShareUrl(url: String): Boolean {
        return url.matches(Regex(SHARE_URL_REGEX))
    }
}