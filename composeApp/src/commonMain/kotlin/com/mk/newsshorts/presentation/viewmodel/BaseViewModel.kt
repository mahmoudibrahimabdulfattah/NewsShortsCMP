package com.mk.newsshorts.presentation.viewmodel

import kotlinx.coroutines.CoroutineScope

expect abstract class BaseViewModel() {
    val viewModelScope: CoroutineScope
    
    protected open fun onCleared()
}

