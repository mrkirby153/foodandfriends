package com.mrkirby153.foodandfriends

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AbstractPromptReceiver
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import java.io.File
import java.io.InputStreamReader


private val scopes = listOf(CalendarScopes.CALENDAR)

private val JSON_FACTORY = GsonFactory.getDefaultInstance()


class TestReceiver : AbstractPromptReceiver() {
    override fun getRedirectUri(): String {
        return "https://www.mrkirby153.com/authorization_callback.html"
    }
}


private fun getCredentials(transport: NetHttpTransport): Credential? {
    val credentialFile = File("config/credentials.json")

    val secrets = credentialFile.inputStream().use {
        GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(it))
    }

    val flow = GoogleAuthorizationCodeFlow.Builder(transport, JSON_FACTORY, secrets, scopes)
        .setDataStoreFactory(
            FileDataStoreFactory(File("tokens"))
        ).setAccessType("offline").build()

    val credential = AuthorizationCodeInstalledApp(flow, TestReceiver()).authorize("user")
    return credential
}

fun main() {
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    val service = Calendar.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
        .setApplicationName("FoodAndFriends").build()

    val events = service.events().list("primary").setMaxResults(10)
        .setTimeMin(DateTime(System.currentTimeMillis())).setOrderBy("startTime")
        .setSingleEvents(true).execute()

    events.items.forEach { item ->
        println("Event: ${item.summary}")
    }
}