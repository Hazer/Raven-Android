package io.vithor.sentry.raven

class InvalidDsnException : RuntimeException {
    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}