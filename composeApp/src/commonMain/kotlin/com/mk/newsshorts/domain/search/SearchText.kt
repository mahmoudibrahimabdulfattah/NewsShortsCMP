package com.mk.newsshorts.domain.search

/**
 * Folding text into the form search compares on.
 *
 * Exact substring matching is close to useless for Arabic, because the script
 * as it is actually typed is not the script as it is actually published:
 *
 *  - Hamza is optional in practice. أحمد and احمد are the same name to every
 *    reader and to most keyboards; إسرائيل, اسرائيل and أسرائيل all get typed.
 *    Which letter it sits on varies too — مسؤول and مسئول are the same word.
 *  - ة and ه are interchanged constantly at the end of a word (غزة / غزه), and
 *    so are ى and ي (على / علي, مصطفى / مصطفي).
 *  - Diacritics (harakat, shadda) are optional decoration. A publisher may set
 *    them, a reader searching almost never types them, and a single fatha in a
 *    headline would otherwise hide it from every query.
 *  - Arabic-Indic digits (٢٠٢٦) and Western digits (2026) name the same year.
 *
 * So both sides — the article text and the query — are folded to one canonical
 * form before they ever meet. Latin gets the same treatment for the same
 * reason at a smaller scale (café / cafe, Über / uber), since the feed also
 * publishes German, French and Spanish sources under English.
 *
 * The folding is deliberately lossy in one direction only: it can make two
 * different words look the same (a rare false hit, which ranking pushes down),
 * and it can never make the same word look like two different ones, which is
 * what would actually lose a result.
 */

/**
 * Marks a character that folds to nothing at all — a value no real text
 * contains, rather than a space: a dropped diacritic must not split the word
 * it was sitting on.
 */
private const val DROPPED: Char = '\u0000'

/**
 * The shortest query worth running. One character matches most of the corpus
 * in any language and effectively none of it usefully, and in Arabic it is
 * usually half a letter pair the reader has not finished typing.
 */
const val MIN_SEARCH_LENGTH: Int = 2

/** Whether [query] has enough left after folding to be worth searching for. */
fun isSearchable(query: String): Boolean =
    normalizeForSearch(query).length >= MIN_SEARCH_LENGTH

/**
 * The comparable form of [text]: folded letters, digits normalized to Western
 * ones, and every run of anything else collapsed to a single space.
 */
fun normalizeForSearch(text: String): String {
    val out = StringBuilder(text.length)
    for (raw in text) {
        val expansion = raw.expandedForSearch()
        if (expansion != null) {
            out.append(expansion)
            continue
        }
        val folded = raw.foldedForSearch()
        when {
            folded == DROPPED -> Unit
            folded.isLetterOrDigit() -> out.append(folded)
            // Punctuation, symbols and whitespace all become one separator, so
            // "COVID-19" and "covid 19" tokenize identically.
            out.isNotEmpty() && out.last() != ' ' -> out.append(' ')
        }
    }
    return out.toString().trim()
}

/**
 * The words of [query] to match on, folded and de-duplicated.
 *
 * Empty when the query is only punctuation, which the caller reads as "nothing
 * to search for" rather than as "search for everything".
 */
fun searchTokens(query: String): List<String> =
    normalizeForSearch(query)
        .split(' ')
        .filter { it.isNotEmpty() }
        .map { it.withoutDefiniteArticle() }
        .distinct()

/**
 * ال on the front of a word, dropped so a reader who types the article finds a
 * headline written without it. The other direction needs nothing: matching is
 * by substring, so a bare حرب already finds الحرب.
 *
 * Only when enough word is left afterwards to still mean something — الف and
 * الم are words in their own right, not an article and a stem.
 */
private fun String.withoutDefiniteArticle(): String =
    if (length >= DEFINITE_ARTICLE.length + MIN_STEM_LENGTH && startsWith(DEFINITE_ARTICLE)) {
        substring(DEFINITE_ARTICLE.length)
    } else {
        this
    }

/** ا + ل, as it looks after folding. */
private const val DEFINITE_ARTICLE: String = "ال"

/** How much word has to survive dropping ال for the drop to be worth doing. */
private const val MIN_STEM_LENGTH: Int = 3

/**
 * The three letters that fold to two, kept out of [foldedForSearch] so it can
 * stay character-to-character. Null for everything else.
 */
private fun Char.expandedForSearch(): String? = when (lowercaseChar()) {
    'ß' -> "ss"
    'æ' -> "ae"
    'œ' -> "oe"
    else -> null
}

private fun Char.foldedForSearch(): Char = when (this) {
    // Optional marks: harakat and shadda, the superscript alef, the Quranic
    // annotation range, and the tatweel that only stretches a word visually.
    in 'ً'..'ٟ', 'ٰ', 'ـ', in 'ۖ'..'ۭ' -> DROPPED

    // The alefs a reader types interchangeably, plus the wasla, all to bare ا.
    'أ', 'إ', 'آ', 'ٱ' -> 'ا'

    // ة → ه and ى → ي: the two endings nobody distinguishes when typing.
    'ة' -> 'ه'
    'ى' -> 'ي'

    // Every hamza disappears, seat and all. Folding ؤ to و and ئ to ي instead
    // would keep the word's shape but leave مسؤول and مسئول as two different
    // words, and both spellings are in daily use — the seat a writer picks is
    // a house-style choice, not a sound. The alefs above are the exception,
    // and only because dropping theirs outright would turn أحمد into حمد.
    'ؤ', 'ئ', 'ء' -> DROPPED

    // Both Arabic digit sets name the same numbers as the Western one.
    in '٠'..'٩' -> '0' + (this - '٠')
    in '۰'..'۹' -> '0' + (this - '۰')

    else -> {
        val lower = lowercaseChar()
        val accented = LATIN_ACCENTED.indexOf(lower)
        if (accented >= 0) LATIN_PLAIN[accented] else lower
    }
}

/**
 * Latin letters that carry a mark, and the letters they fold to. Positionally
 * paired, so the two strings must stay the same length.
 */
private const val LATIN_ACCENTED: String = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿšžøđ"
private const val LATIN_PLAIN: String = "aaaaaaceeeeiiiinooooouuuuyyszod"
