package app.raven.browser.ui.browser

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/** The parts of the day the home page greets you in. */
enum class DayPart {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun at(hour: Int): DayPart = when (hour) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..22 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * The home page's greeting, two lines in the big letters: a new one each time Raven opens. Like the wallpapers they
 * take turns in a shuffled order, so you see every one for that part of the day before any comes back.
 */
class Greetings(app: Application) {
    private val sp = app.getSharedPreferences("greetings", 0)

    private val _current = MutableStateFlow(pick(DayPart.at(LocalTime.now().hour)))
    val current: StateFlow<Greeting> = _current.asStateFlow()

    /** Raven was opened again: the next greeting's turn. */
    fun next() {
        _current.value = pick(DayPart.at(LocalTime.now().hour))
    }

    /** The clock moved on (Raven stayed open into the evening, say): a greeting for the new part of the day. */
    fun follow(hour: Int) {
        val part = DayPart.at(hour)
        if (part != _current.value.part) _current.value = pick(part)
    }

    private fun pick(part: DayPart): Greeting {
        val pool = forPart(part)
        val key = "bag_${part.name.lowercase()}"
        val last = sp.getString("last", null)
        var bag = sp.getString(key, "")!!.split('|').filter { it in pool }
        if (bag.isEmpty()) {
            bag = pool.shuffled()
            if (bag.size > 1 && bag.first() == last) bag = bag.drop(1) + bag.first()
        }
        val text = bag.first()
        sp.edit().putString(key, bag.drop(1).joinToString("|")).putString("last", text).apply()
        return Greeting(text, part)
    }

    companion object {
        fun forPart(part: DayPart): List<String> = when (part) {
            DayPart.MORNING -> MORNING
            DayPart.AFTERNOON -> AFTERNOON
            DayPart.EVENING -> EVENING
            DayPart.NIGHT -> NIGHT
        } + ANY_TIME

        val MORNING = listOf(
            "Good\nmorning.", "Rise and\nshine.", "Fresh\nstart.", "Morning,\nearly bird.", "Coffee\nfirst?",
            "New day,\nnew tabs.", "Up and\nabout.", "Sun's\nup.", "Morning\nlight.", "Bright\nand early.",
            "Top of the\nmorning.", "Slow\nmorning?", "A brand\nnew day.", "Wide\nawake.", "First\nlight.",
            "Breakfast\nreading?", "Morning\nnews?", "The day\nbegins.", "Easy\nmorning.", "Hello,\nsunshine.",
            "Early\nstart.", "Dawn\npatrol.", "Ready for\ntoday?", "Let's\nbegin.", "Good\nday ahead.",
        )
        val AFTERNOON = listOf(
            "Good\nafternoon.", "Afternoon\nbreak?", "Halfway\nthere.", "Lunch\nbreak?", "Bright\nafternoon.",
            "Keep it\nrolling.", "Midday\ncheck-in.", "Still\ngoing.", "Tea\ntime?", "A quick\nlook?",
            "Slow\nafternoon.", "Back at\nit.", "Second\nwind.", "Sun's\nhigh.", "Afternoon\ndrift.",
            "On a\nroll.", "Middle of\nthe day.", "Lazy\nafternoon.", "Time for\na break?", "Look who's\nback.",
            "Day's\nflying.", "Steady\ngoing.", "Afternoon\nlight.", "What's\nnext?", "Still\nbright out.",
        )
        val EVENING = listOf(
            "Good\nevening.", "Evening\nfalls.", "Golden\nhour.", "Winding\ndown.", "Dusk\nsettles.",
            "Evening\nplans?", "Day's\ndone.", "Lights\ndown low.", "First\nstars.", "The moon\nrises.",
            "Evening\nchill.", "Feet\nup.", "Calm\nevening.", "Sun's\ngone down.", "The sky\ndarkens.",
            "Easy\nevening.", "Twilight\nhours.", "Time to\nunwind.", "Evening\nreading?", "Dinner,\nthen tabs?",
            "Night is\ncoming.", "Lamps\non.", "Cozy\nevening.", "Long\nday?", "Slow\nevening.",
        )
        val NIGHT = listOf(
            "Late\nnight.", "Still\nup?", "Night\nowl.", "Can't\nsleep?", "Midnight\nbrowsing.",
            "Quiet\nhours.", "The world\nsleeps.", "Moon's\nout.", "After\nhours.", "Dark\nskies.",
            "Insomnia\nclub.", "Starry\nnight.", "Hush\nhour.", "Just one\nmore tab.", "Up late\nagain?",
            "Small\nhours.", "Night\nshift.", "Owls and\nravens.", "Deep\nnight.", "Sleep is\noverrated.",
            "Silent\nnight.", "Raven's\nhour.", "Under the\nstars.", "Nocturnal\nmode.", "Dreams\ncan wait.",
        )
        /** Fit any hour, so they turn up in every part of the day. */
        val ANY_TIME = listOf(
            "Welcome\nback.", "Hello\nagain.", "Where\nto?", "Ready when\nyou are.", "Look\naround.",
            "Wander\nfreely.", "Fly\nfree.", "Off we\ngo.", "Hey\nthere.", "Nice to\nsee you.",
            "Let's\nexplore.", "What's\nup?", "Curious\nagain?", "Go\nanywhere.", "Wings\nready.",
            "The web\nawaits.", "Find\nsomething.", "Pick a\ndirection.", "Search\nthe sky.", "Back for\nmore?",
            "Safe\nskies.", "Ad-free\nskies.",
        )
    }
}

class Greeting(val text: String, val part: DayPart)
