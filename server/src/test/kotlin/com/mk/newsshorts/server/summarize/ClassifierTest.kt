package com.mk.newsshorts.server.summarize

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClassifierTest {

    private fun category(json: String) = parseCategory(Json.parseToJsonElement(json))

    @Test
    fun `a known category is read back`() {
        assertEquals("sports", category(""""sports""""))
    }

    @Test
    fun `casing and padding from the model are tolerated`() {
        assertEquals("science", category("""" SCIENCE """"))
    }

    @Test
    fun `an invented category invalidates the answer`() {
        // Not accepted as a fallback: a model that has invented a name has not
        // understood the list, so the answer is not trustworthy.
        assertNull(category(""""politics""""))
    }

    @Test
    fun `a one-element array is tolerated`() {
        assertEquals("health", category("""[" Health "]"""))
    }

    @Test
    fun `a two-element array is refused`() {
        assertNull(category("""["sports", "entertainment"]"""))
    }

    @Test
    fun `an empty or absent value is not an answer`() {
        assertNull(category("""[]"""))
        assertNull(parseCategory(null))
    }
}
