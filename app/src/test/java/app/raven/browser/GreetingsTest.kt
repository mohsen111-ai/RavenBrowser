package app.raven.browser

import app.raven.browser.ui.browser.DayPart
import app.raven.browser.ui.browser.Greetings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The home page's greetings: over a hundred, two lines each, none twice, and the right ones for the hour. */
class GreetingsTest {
    private val all = Greetings.MORNING + Greetings.AFTERNOON + Greetings.EVENING + Greetings.NIGHT + Greetings.ANY_TIME

    @Test fun manyAndDifferent() {
        assertTrue("only ${all.size}", all.size >= 100)
        assertEquals(all.size, all.toSet().size)
        DayPart.entries.forEach { assertTrue(Greetings.forPart(it).size >= 40) }
    }

    @Test fun twoLinesEach() {
        all.forEach { assertEquals(it, 1, it.count { c -> c == '\n' }) }
    }

    @Test fun partsOfTheDay() {
        assertEquals(DayPart.NIGHT, DayPart.at(0))
        assertEquals(DayPart.NIGHT, DayPart.at(4))
        assertEquals(DayPart.MORNING, DayPart.at(5))
        assertEquals(DayPart.MORNING, DayPart.at(11))
        assertEquals(DayPart.AFTERNOON, DayPart.at(12))
        assertEquals(DayPart.EVENING, DayPart.at(18))
        assertEquals(DayPart.EVENING, DayPart.at(22))
        assertEquals(DayPart.NIGHT, DayPart.at(23))
    }
}
