package com.mrkirby153.foodandfriends.google

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.User
import org.springframework.beans.factory.annotation.Value

interface AuthorizationHandler {

    fun authorize(user: User, code: String): Credential

    fun getAuthorization(user: User): Credential?

    fun getAuthorizationUrl(user: User): String
}


class AuthorizationManager(
    private val flow: GoogleAuthorizationCodeFlow,
    @Value("\${google.credentials.redirect-uri}") private val redirectUri: String
) : AuthorizationHandler {

    private val log = KotlinLogging.logger {}

    override fun authorize(user: User, code: String): Credential {
        log.debug { "Authorizing user $user" }

        val existing = try {
            getAuthorization(user)
        } catch (e: AuthorizationExpiredException) {
            null
        }
        if (existing != null) {
            log.trace { "using existing credentials" }
            return existing
        }
        val resp = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute()
        return flow.createAndStoreCredential(resp, user.id)
    }

    override fun getAuthorization(user: User): Credential? {
        val credential =
            flow.loadCredential(user.id) ?: return null

        if (credential.refreshToken != null || credential.expiresInSeconds == null || credential.expiresInSeconds > 60)
            return credential
        throw AuthorizationExpiredException("Refresh token expired")
    }

    override fun getAuthorizationUrl(user: User): String {
        return flow.newAuthorizationUrl().setRedirectUri(redirectUri).build()
    }

}