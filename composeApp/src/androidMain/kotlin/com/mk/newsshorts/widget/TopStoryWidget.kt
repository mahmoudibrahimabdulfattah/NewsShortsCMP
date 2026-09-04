package com.mk.newsshorts.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mk.newsshorts.MainActivity
import com.mk.newsshorts.R
import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.core.model.FeedLanguage
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.NewsResult
import com.mk.newsshorts.core.domain.use_case.GetTopHeadlinesRequest
import com.mk.newsshorts.core.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.core.model.deeplink.ArticleDeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

class TopStoryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val article = loadTopStory()
        provideContent {
            TopStoryContent(
                context = context,
                article = article,
            )
        }
    }

    private suspend fun loadTopStory(): NewsArticle? = withContext(Dispatchers.IO) {
        runCatching {
            val koin = GlobalContext.get()
            val settingsManager = koin.get<SettingsManager>()
            val getTopHeadlines = koin.get<GetTopHeadlinesUseCase>()
            val language = FeedLanguage.resolve(settingsManager.preferences.value.newsLanguage)
            val request = GetTopHeadlinesRequest(
                category = NewsCategory.GENERAL,
                language = language,
                useCountry = false,
            )
            when (val result = getTopHeadlines.execute(request)) {
                is NewsResult.Success -> result.data.articles.firstOrNull()
                is NewsResult.Error -> when (val cached = getTopHeadlines.getCached(request)) {
                    is NewsResult.Success -> cached.data.articles.firstOrNull()
                    is NewsResult.Error, null -> null
                }
            }
        }.getOrNull()
    }
}

class TopStoryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopStoryWidget()
}

/**
 * [ColorProvider] taking a resource id is annotated `@RestrictedApi` to
 * androidx's own library group, which is why `check` had to run with
 * `-x lintDebug` from phase 0 onward — four errors, all of them this call.
 *
 * Suppressed rather than replaced. Glance 1.2.0 offers no public alternative
 * that keeps the behaviour: the only other overload takes a resolved `Color`,
 * and resolving `values/` against `values-night/` ourselves would move the
 * day/night decision out of the resource system and into app code, evaluated
 * in the app's configuration rather than the host launcher's. The restriction
 * is over-broad for a colour resource; the suppression is scoped to this one
 * composable so anything else restricted still fails the build.
 */
@SuppressLint("RestrictedApi")
@Composable
private fun TopStoryContent(
    context: Context,
    article: NewsArticle?,
) {
    val openIntent = article?.toDeepLinkIntent(context)
        ?: Intent(context, MainActivity::class.java)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_background))
            .clickable(actionStartActivity(openIntent))
            .padding(16.dp),
    ) {
        Text(
            text = context.getString(R.string.widget_top_story),
            style = TextStyle(
                color = ColorProvider(R.color.widget_accent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = article?.title?.value
                ?: context.getString(R.string.widget_unavailable),
            style = TextStyle(
                color = ColorProvider(R.color.widget_on_background),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 3,
        )
        article?.source?.name?.value?.takeIf(String::isNotBlank)?.let { source ->
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = source,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_muted),
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun NewsArticle.toDeepLinkIntent(context: Context): Intent {
    val uri = Uri.Builder()
        .scheme(ArticleDeepLinks.SCHEME)
        .authority(ArticleDeepLinks.HOST)
        .appendQueryParameter("url", articleUrl.value)
        .appendQueryParameter("title", title.value)
        .appendQueryParameter("summary", description.value)
        .appendQueryParameter("source", source.name.value)
        .appendQueryParameter("category", category.apiValue)
        .appendQueryParameter("published", publishedAt.epochMillis.toString())
        .apply { imageUrl?.value?.let { appendQueryParameter("image", it) } }
        .build()

    return Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}
