package com.mk.newsshorts.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.presentation.localization.categoryName

@Composable
fun CategoryRow(
    selectedCategory: NewsCategory,
    onCategorySelected: (NewsCategory) -> Unit,
    modifier: Modifier = Modifier,
    // Ordered by what the reader picked at onboarding, defaulting to the
    // declared order for anyone who skipped. Nothing is ever removed from it.
    categories: List<NewsCategory> = NewsCategory.entries,
) {
    SelectorRow(
        items = categories,
        selected = selectedCategory,
        key = { category -> category.name },
        onSelect = onCategorySelected,
        modifier = modifier,
        onImagery = true,
        leading = { category -> category.emoji },
        label = { category -> categoryName(category.apiValue, category.displayName) },
    )
}
