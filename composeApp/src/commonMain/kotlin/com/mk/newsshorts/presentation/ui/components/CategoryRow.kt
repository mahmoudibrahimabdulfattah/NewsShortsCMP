package com.mk.newsshorts.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.presentation.localization.categoryName

@Composable
fun CategoryRow(
    selectedCategory: NewsCategory,
    onCategorySelected: (NewsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    SelectorRow(
        items = NewsCategory.entries,
        selected = selectedCategory,
        key = { category -> category.name },
        onSelect = onCategorySelected,
        modifier = modifier,
        onImagery = true,
        leading = { category -> category.emoji },
        label = { category -> categoryName(category.apiValue, category.displayName) },
    )
}
