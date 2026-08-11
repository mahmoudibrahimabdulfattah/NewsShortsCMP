package org.example.newsshorts.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Still a [ViewModel] so `koinViewModel()` can resolve it, but it owns its
 * coroutine scope instead of borrowing `androidx.lifecycle.viewModelScope`.
 *
 * The DI definition is a `single`, so one instance serves the whole process.
 * `viewModelScope` is cancelled when the Activity's ViewModelStore clears, and
 * Koin then hands the same instance back on the next launch — with a dead scope
 * and no second `init`. Every coroutine in the app (feed loading, settings
 * writes, the deep-link collector) stopped after the user backed out once.
 *
 * Matches what iosMain, jsMain and wasmJsMain already do.
 */
actual abstract class BaseViewModel : ViewModel() {

    private val job = SupervisorJob()
    actual val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main + job)

    actual override fun onCleared() {
        // Deliberately does not cancel: the scope outlives any single Activity,
        // matching the singleton lifetime of the instance itself.
        super.onCleared()
    }
}
