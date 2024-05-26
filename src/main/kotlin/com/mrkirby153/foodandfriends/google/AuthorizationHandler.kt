package com.mrkirby153.foodandfriends.google

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.CredentialRefreshListener
import com.google.api.client.auth.oauth2.TokenErrorResponse
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

interface AuthorizationHandler {

    fun authorize(user: User, code: String, force: Boolean = false): Credential

    fun getAuthorization(user: User): Credential?

    fun getAuthorizationUrl(user: User): String

    fun authorized(user: User): Boolean
}


@Service
class AuthorizationManager(
    private val flow: GoogleAuthorizationCodeFlow,
    @Value("\${google.credentials.redirect-uri}") private val redirectUri: String
) : AuthorizationHandler {

    private val log = KotlinLogging.logger {}

    override fun authorize(user: User, code: String, force: Boolean): Credential {
        log.debug { "Authorizing user $user" }

        val existing = try {
            getAuthorization(user)
        } catch (e: GoogleOAuthException.AuthorizationExpiredException) {
            null
        }
        if (existing != null && !force) {
            throw GoogleOAuthException.AlreadyAuthenticatedException("Already authenticated")
        }
        val resp = try {
            flow.newTokenRequest(code).setRedirectUri(redirectUri).execute()
        } catch (e: Exception) {
            throw GoogleOAuthException.AuthenticationFailedException("Authentication Error", e)
        }
        log.debug { "Authorized user $user" }
        return flow.createAndStoreCredential(resp, user.id)
    }

    override fun getAuthorization(user: User): Credential? {
        val credential =
            flow.loadCredential(user.id) ?: return null
        credential.refreshListeners.add(object : CredentialRefreshListener {
            override fun onTokenResponse(credential: Credential, tokenResponse: TokenResponse) {
                log.debug { "Persisting token after refresh for $user" }
                flow.createAndStoreCredential(tokenResponse, user.id)
            }

            override fun onTokenErrorResponse(
                credential: Credential?,
                tokenErrorResponse: TokenErrorResponse?
            ) {
                log.warn { "RefreshListener failed with $tokenErrorResponse" }
            }
        })

        if (credential.refreshToken != null || credential.expiresInSeconds == null || credential.expiresInSeconds > 60) {
            if (credential.expiresInSeconds < 60) {
                try {
                    log.debug { "Refreshing token for $user" }
                    if (!credential.refreshToken()) {
                        log.debug { "Refresh failed!" }
                        throw GoogleOAuthException.AuthorizationExpiredException("Refresh failed")
                    }
                    log.debug { "Refreshed token for $user" }
                } catch (e: TokenResponseException) {
                    log.debug(e) { "Refresh failed!" }
                    throw GoogleOAuthException.AuthorizationExpiredException("Authentication expired")
                }
            }
            return credential
        }
        throw GoogleOAuthException.AuthorizationExpiredException("Token expired")
    }

    override fun getAuthorizationUrl(user: User): String {
        return flow.newAuthorizationUrl().setRedirectUri(redirectUri).setAccessType("offline")
            .build()
    }

    override fun authorized(user: User): Boolean {
        return try {
            getAuthorization(user) != null
        } catch (e: GoogleOAuthException.AuthorizationExpiredException) {
            false
        }
    }

}