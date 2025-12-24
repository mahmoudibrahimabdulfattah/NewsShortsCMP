package org.example.newsshorts.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope as androidViewModelScope
import kotlinx.coroutines.CoroutineScope

actual abstract class BaseViewModel : ViewModel() {
    actual val viewModelScope: CoroutineScope
        get() = androidViewModelScope
    
    actual override fun onCleared() {
        super.onCleared()
    }
}

