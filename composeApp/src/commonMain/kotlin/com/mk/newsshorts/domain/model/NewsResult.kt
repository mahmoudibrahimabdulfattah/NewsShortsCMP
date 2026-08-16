package com.mk.newsshorts.domain.model

sealed class NewsResult<out T> {
    data class Success<T>(val data: T) : NewsResult<T>()
    data class Error(val error: NewsError) : NewsResult<Nothing>()
}

sealed class NewsError(val message: String) {
    data object NetworkError : NewsError("Unable to connect. Please check your internet connection.")
    data object ServerError : NewsError("Server error. Please try again later.")
    data object NoDataError : NewsError("No news articles found.")

    /**
     * The file asked for is not published.
     *
     * Its own outcome rather than a server error, because for a later page it
     * is the expected end of the feed: retention empties the deepest page and
     * the publish that follows simply stops writing it. Offering a reader a
     * "try again" there would be offering a retry of the end.
     */
    data object NotFound : NewsError("Not published.")
    data class UnknownError(val errorMessage: String) : NewsError(errorMessage)
}

