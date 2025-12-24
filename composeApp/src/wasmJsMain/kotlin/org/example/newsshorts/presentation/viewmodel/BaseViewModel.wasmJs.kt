package org.example.newsshorts.presentation.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

actual abstract class BaseViewModel {
    private val job = SupervisorJob()
    actual val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main + job)
    
    actual protected open fun onCleared() {
        viewModelScope.cancel()
    }
}

