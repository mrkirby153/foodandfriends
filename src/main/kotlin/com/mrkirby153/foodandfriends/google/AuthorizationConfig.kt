package com.mrkirby153.foodandfriends.google

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.CalendarScopes
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.InputStreamReader

@Configuration
class AuthorizationConfig(
    @Value("\${google.credentials}") private val googleCredentialsPath: String,
) {

    @Bean
    fun httpTransport() = GoogleNetHttpTransport.newTrustedTransport()

    @Bean
    fun gsonFactory() = GsonFactory.getDefaultInstance()

    @Bean
    fun authorizationFlow(
        gson: GsonFactory,
        transport: NetHttpTransport
    ): GoogleAuthorizationCodeFlow? {
        val credentialFile = File(googleCredentialsPath)
        val secrets = credentialFile.inputStream().use {
            GoogleClientSecrets.load(gson, InputStreamReader(it))
        }

        return GoogleAuthorizationCodeFlow.Builder(
            transport,
            gson,
            secrets,
            listOf(CalendarScopes.CALENDAR)
        ).setDataStoreFactory(FileDataStoreFactory(File("config/tokens"))).build()
    }
}