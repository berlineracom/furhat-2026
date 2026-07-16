package furhatos.app.newskill1

import furhatos.flow.kotlin.*
import furhatos.util.Gender
import furhatos.util.Language

val MultiScenarioInit: State = state {
    onEntry {
        // Force English voice/language regardless of SDK default settings
        furhat.setVoice(Language.ENGLISH_US, Gender.FEMALE)

        DialogHistory.reset()
        Logger.log(ACTIVE_SCENARIO, "INIT", "session_start")
        when (ACTIVE_SCENARIO) {
            1 -> goto(S1_00)
            2 -> goto(S2_00)
            3 -> goto(S3_00)
            4 -> goto(S4_00)
            else -> goto(S3_00)
        }
    }
}

/**
 * Detects opt-out phrases ("I'm done", "stop", "forget it", etc.)
 */
fun isOptOut(text: String): Boolean {
    val t = text.lowercase()
    return t.contains("i'm done") || t.contains("im done") || t.contains("forget it") ||
            t.contains("never mind") || t.contains("stop") || t.contains("that's all") ||
            t.contains("thats all") || t.contains("i want to stop") || t.contains("i'd like to stop")
}

/**
 * Helper: say a line, log it, and record it in the dialog history.
 */
fun furhatSayAndLog(furhat: furhatos.flow.kotlin.Furhat, text: String) {
    furhat.say(text)
    DialogHistory.addAssistant(text)
}

fun furhatAskAndLog(furhat: furhatos.flow.kotlin.Furhat, text: String) {
    furhat.ask(text)
    DialogHistory.addAssistant(text)
}
