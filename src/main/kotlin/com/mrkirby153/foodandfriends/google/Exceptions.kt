package com.mrkirby153.foodandfriends.google


sealed class GoogleOAuthException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class AuthorizationExpiredException(message: String) : GoogleOAuthException(message)

    class AuthenticationFailedException(message: String, cause: Throwable? = null) :
        GoogleOAuthException(message, cause)

    class AlreadyAuthenticatedException(message: String) : GoogleOAuthException(message)
}

