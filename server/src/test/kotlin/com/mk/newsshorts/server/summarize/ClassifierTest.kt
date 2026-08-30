package com.mk.newsshorts.server.summarize

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClassifierTest {

    private fun categories(json: String) = parseCategories(Json.parseToJsonElement(json))

    @Test
    fun `a known category is read back`() {
        assertEquals(setOf("sports"), categories("""["sports"]"""))
    }

    @Test
    fun `casing and padding from the model are tolerated`() {
        assertEquals(setOf("health", "science"), categories("""[" Health ", "SCIENCE"]"""))
    }

    @Test
    fun `an invented category invalidates the whole answer`() {
        // Not merely dropped: a model that has invented one name has not
        // understood the list, so the names beside it are no better evidence.
        assertNull(categories("""["sports", "politics"]"""))
    }

    @Test
    fun `an empty or absent list is not an answer`() {
        assertNull(categories("""[]"""))
        assertNull(parseCategories(null))
        assertNull(categories(""""sports""""))
    }
}
