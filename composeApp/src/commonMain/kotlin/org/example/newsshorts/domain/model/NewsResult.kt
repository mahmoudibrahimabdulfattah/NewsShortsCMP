package org.example.newsshorts.domain.model

sealed class NewsResult<out T> {
    data class Success<T>(val data: T) : NewsResult<T>()
    data class Error(val error: NewsError) : NewsResult<Nothing>()
}

sealed class NewsError(val message: String) {
    data object NetworkError : NewsError("Unable to connect. Please check your internet connection.")
    data object ServerError : NewsError("Server error. Please try again later.")
    data object NoDataError : NewsError("No news articles found.")
    data class UnknownError(val errorMessage: String) : NewsError(errorMessage)
}

