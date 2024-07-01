package com.mrkirby153.foodandfriends.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.util.toByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GoogleMapsConfig(
    @Value("\${google.maps.apikey:}")
    private val apiKey: String,
    private val jsonDeserializer: Json,
) {
    private val log = KotlinLogging.logger("GoogleHttpRequestClient")

    private val googleMapsApiKeyPlugin = createClientPlugin("GoogleMapsApiKey") {
        onRequest { request, _ ->
            request.url.parameters["key"] = apiKey
        }
    }

    private val requestLoggerPlugin = createClientPlugin("RequestLogger") {
        onRequest { request, content ->
            log.trace {
                "Making request to ${request.url} with body: $content"
            }
        }
    }

    private val requestDeserializerPlugin = createClientPlugin("RequestDeserializer") {
        transformResponseBody { _, content, requestedType ->
            val resp = String(content.toByteArray())
            jsonDeserializer.decodeFromString(serializer(requestedType.kotlinType!!), resp)
        }
    }


    fun isEnabled() = apiKey.isNotBlank()

    @Bean(name = ["mapsHttpClient"])
    fun httpClient() = HttpClient(CIO) {
        install(Resources)
        install(googleMapsApiKeyPlugin)
        install(requestLoggerPlugin)
        install(requestDeserializerPlugin)
        defaultRequest {
            url("https://maps.googleapis.com/")
        }
    }
}