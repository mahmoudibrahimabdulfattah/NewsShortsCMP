package com.mk.newsshorts.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.newsshorts.presentation.localization.countryName
import com.mk.newsshorts.presentation.mvi.CountryOption

/**
 * Countries used to be a row of two-line tiles — a 28sp flag stacked over its
 * name — standing about 110dp tall directly beneath the masthead. That is a lot
 * of the reader's screen given the row below it is a feed, and it meant the two
 * filter rows in the app looked nothing like each other despite doing the same
 * job one tap apart. The flag now sits inline exactly where a category's emoji
 * does, and the row is the same height as any other.
 */
@Composable
fun CountrySelector(
    selectedCountry: CountryOption,
    onCountrySelected: (CountryOption) -> Unit,
    modifier: Modifier = Modifier
) {
    SelectorRow(
        items = CountryOption.entries,
        selected = selectedCountry,
        key = { country -> country.code },
        onSelect = onCountrySelected,
        modifier = modifier,
        onImagery = true,
        leading = { country -> country.flag },
        label = { country -> countryName(country.code, country.displayName) },
    )
}
