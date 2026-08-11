package org.example.newsshorts.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owns its coroutine scope rather than borrowing
 * `androidx.lifecycle.viewModelScope`, for the same reason as the Android
 * actual: the instance is a process-wide `single`, so tying its scope to a
 * window's lifecycle leaves it permanently cancelled after the first teardown.
 */
actual abstract class BaseViewModel : ViewModel() {

    private val job = SupervisorJob()
    actual val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main + job)

    actual override fun onCleared() {
        super.onCleared()
    }
}
