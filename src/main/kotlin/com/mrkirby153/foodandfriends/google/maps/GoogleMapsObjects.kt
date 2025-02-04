package com.mrkirby153.foodandfriends.google.maps

import com.mrkirby153.foodandfriends.kutils.GoogleDayOfWeekSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


enum class PlaceSearchStatus {
    OK,
    ZERO_RESULTS,
    INVALID_REQUEST,
    OVER_QUERY_LIMIT,
    REQUEST_DENIED,
    UNKNOWN_ERROR
}

@Serializable(GoogleDayOfWeekSerializer::class)
enum class DayOfWeek(val day: Int) {
    SUNDAY(0),
    MONDAY(1),
    TUESDAY(2),
    WEDNESDAY(3),
    THURSDAY(4),
    FRIDAY(5),
    SATURDAY(6);
}

@Serializable
data class TextSearchResponse(
    @SerialName("html_attributions")
    val htmlAttributions: List<String>,
    val results: List<Place>,
    val status: PlaceSearchStatus,
    val errorMessage: String? = null,
    val infoMessages: List<String>? = null,
    val nextPageToken: String? = null
)

@Serializable
data class PlacesDetailsResponse(
    @SerialName("html_attributions")
    val htmlAttributions: List<String>,
    val result: Place,
    val status: PlaceSearchStatus,
    val infoMessages: List<String>? = null,
)

@Serializable
data class Place(
    @SerialName("address_components")
    val addressComponents: List<AddressComponent>? = null,
    @SerialName("adr_address")
    val adrAddress: String? = null,
    @SerialName("business_status")
    val businessStatus: String? = null,
    @SerialName("curbside_pickup")
    val curbsidePickup: Boolean? = null,
    @SerialName("current_opening_hours")
    val currentOpeningHours: PlaceOpeningHours? = null,
    val delivery: Boolean? = null,
    @SerialName("dine_in")
    val dineIn: Boolean? = null,
    @SerialName("editorial_summary")
    val editorialSummary: PlaceEditorialSummary? = null,
    @SerialName("formatted_address")
    val formattedAddress: String? = null,
    @SerialName("formatted_phone_number")
    val formattedPhoneNumber: String? = null,
    val geometry: Geometry? = null,
    val icon: String? = null,
    @SerialName("icon_background_color")
    val iconBackgroundColor: String? = null,
    @SerialName("icon_mask_base_uri")
    val iconMaskBaseUri: String? = null,
    @SerialName("international_phone_number")
    val internationalPhoneNumber: String? = null,
    val name: String? = null,
    @SerialName("opening_hours")
    val openingHours: PlaceOpeningHours? = null,
    val photos: List<PlacePhoto>? = null,
    @SerialName("place_id")
    val placeId: String? = null,
    @SerialName("plus_code")
    val plusCode: PlusCode? = null,
    @SerialName("price_level")
    val priceLevel: Int? = null,
    val rating: Float? = null,
    val reservable: Boolean? = null,
    val reviews: List<PlaceReview>? = null,
    @SerialName("secondary_opening_hours")
    val secondaryOpeningHours: PlaceOpeningHours? = null,
    @SerialName("serves_beer")
    val servesBeer: Boolean? = null,
    @SerialName("serves_breakfast")
    val servesBreakfast: Boolean? = null,
    @SerialName("serves_brunch")
    val servesBrunch: Boolean? = null,
    @SerialName("serves_dinner")
    val servesDinner: Boolean? = null,
    @SerialName("serves_lunch")
    val servesLunch: Boolean? = null,
    @SerialName("serves_vegetarian_food")
    val servesVegetarianFood: Boolean? = null,
    @SerialName("serves_wine")
    val servesWine: Boolean? = null,
    val takeout: Boolean? = null,
    val types: List<String>? = null,
    val url: String? = null,
    @SerialName("user_ratings_total")
    val userRatingsTotal: Int? = null,
    @SerialName("utc_offset")
    val utcOffset: Int? = null,
    val vicinity: String? = null,
    val website: String? = null,
    @SerialName("wheelchair_accessible_entrance")
    val wheelchairAccessibleEntrance: String? = null,
)

@Serializable
data class AddressComponent(
    @SerialName("long_name")
    val longName: String,
    @SerialName("short_name")
    val shortName: String,
    val types: List<String>,
)

@Serializable
data class PlaceOpeningHours(
    @SerialName("open_now")
    val openNow: Boolean? = null,
    val periods: List<PlaceOpeningHoursPeriod>? = null,
    @SerialName("special_days")
    val specialDays: List<PlaceSpecialDay>? = null,
    val type: String? = null,
    @SerialName("weekday_text")
    val weekdayText: List<String>? = null
)


@Serializable
data class PlaceOpeningHoursPeriod(
    val open: PlaceOpeningHoursPeriodDetail,
    val close: PlaceOpeningHoursPeriodDetail
)

@Serializable
data class PlaceOpeningHoursPeriodDetail(
    val day: DayOfWeek,
    val time: String,
    val date: String? = null,
    val truncated: Boolean? = null
)

@Serializable
data class PlaceSpecialDay(
    val date: String? = null,
    @SerialName("exceptional_hours")
    val exceptionalHours: Boolean
)

@Serializable
data class PlaceEditorialSummary(
    val language: String? = null,
    val overview: String?
)

@Serializable
data class Geometry(
    val location: LatLong,
    val viewport: Bounds
)

@Serializable
data class PlacePhoto(
    val height: Int,
    @SerialName("html_attributions")
    val htmlAttributions: List<String>,
    @SerialName("photo_reference")
    val photoReference: String,
    val width: Int
)

@Serializable
data class PlusCode(
    @SerialName("global_code")
    val globalCode: String,
    @SerialName("compound_code")
    val compoundCode: String?
)

@Serializable
data class PlaceReview(
    @SerialName("author_name")
    val authorName: String,
    val rating: Int,
    @SerialName("relative_time_description")
    val relativeTimeDescription: String,
    val time: Long,
    @SerialName("author_url")
    val authorUrl: String? = null,
    val language: String? = null,
    @SerialName("original_language")
    val originalLanguage: String? = null,
    @SerialName("profile_photo_url")
    val profilePhotoUrl: String? = null,
    val text: String? = null,
    val translated: Boolean?
)

@Serializable
data class LatLong(
    val lat: Float,
    @SerialName("lng")
    val long: Float
) {
    fun toHumanReadable(): String {
        return "$lat,$long"
    }
}

@Serializable
data class Bounds(
    val northeast: LatLong,
    val southwest: LatLong,
)

enum class TimeZoneStatus {
    OK,
    INVALID_REQUEST,
    OVER_QUERY_LIMIT,
    REQUEST_DENIED,
    UNKNOWN_ERROR,
    ZERO_RESULTS
}

@Serializable
data class TimeZoneResponse(
    val status: TimeZoneStatus,
    val dstOffset: Int? = null,
    val errorMessage: String? = null,
    val rawOffset: Int? = null,
    val timeZoneId: String? = null,
    val timeZoneName: String? = null
)

@Serializable
data class PlacesNearbySearchResponse(
    @SerialName("html_attributions")
    val htmlAttributions: List<String>,
    val results: List<Place>,
    val status: PlaceSearchStatus,
    val errorMessage: String? = null,
    @SerialName("info_messages")
    val infoMessages : List<String>? = null,
    @SerialName("next_page_token")
    val nextPageToken: String? = null
)