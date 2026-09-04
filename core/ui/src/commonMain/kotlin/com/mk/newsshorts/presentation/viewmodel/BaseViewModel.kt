package com.mk.newsshorts.presentation.viewmodel

import androidx.lifecycle.ViewModel

/**
 * One ViewModel base for all six targets.
 *
 * This used to be an `expect class` with five actuals, which existed only
 * because `androidx.lifecycle.ViewModel` was Android-only — and it left the
 * lifetime rules inconsistent: Android deliberately never cancelled its scope
 * while iOS, JS and Wasm always did. `lifecycle-viewmodel` is multiplatform
 * now, so the scope is `androidx.lifecycle.viewModelScope` everywhere, cleared
 * by the ViewModel store on every target under the same rule.
 */
abstract class BaseViewModel : ViewModel()
