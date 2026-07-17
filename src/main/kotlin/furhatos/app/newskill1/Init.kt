package furhatos.app.newskill1

import furhatos.flow.kotlin.*
import furhatos.flow.kotlin.voice.PollyNeuralVoice
import furhatos.flow.kotlin.voice.Voice

/**
 * Just under the SDK default (1.0), tuned down from 1.05.
 */
private const val SPEECH_RATE = 0.95

/**
 * Each scenario gets its own fixed, named voice (2 male, 2 female) instead of
 * one default voice shared by every run.
 */
private fun scenarioVoice(scenario: Int): Voice {
    val voice = when (scenario) {
        1 -> PollyNeuralVoice.Joanna()   // Veranda Consultation - female
        2 -> PollyNeuralVoice.Matthew()  // Travel Plan - male
        3 -> PollyNeuralVoice.Kimberly() // Doctor's Appointment - female
        4 -> PollyNeuralVoice.Joey()     // Insurance Claim - male
        else -> PollyNeuralVoice.Joanna()
    }
    voice.rate = SPEECH_RATE
    return voice
}

val MultiScenarioInit: State = state {
    onEntry {
        try {
            furhat.setVoice(scenarioVoice(ACTIVE_SCENARIO))
        } catch (e: Exception) {
            // A voice-setup failure must never block the interaction from starting.
            Logger.log(ACTIVE_SCENARIO, "INIT", "voice_setup_failed", note = e.message ?: "unknown error")
        }

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
 * Wrapped defensively so a robot/TTS/network hiccup logs and moves on
 * instead of throwing and stranding the flow in the current state.
 */
fun furhatSayAndLog(furhat: furhatos.flow.kotlin.Furhat, text: String) {
    val safeText = text.ifBlank { "..." }
    try {
        furhat.say(safeText)
    } catch (e: Exception) {
        Logger.log(ACTIVE_SCENARIO, "SAY", "say_failed", note = e.message ?: "unknown error")
    }
    DialogHistory.addAssistant(safeText)
}

fun furhatAskAndLog(furhat: furhatos.flow.kotlin.Furhat, text: String) {
    val safeText = text.ifBlank { "Sorry, could you say that again?" }
    try {
        furhat.ask(safeText)
    } catch (e: Exception) {
        Logger.log(ACTIVE_SCENARIO, "ASK", "ask_failed", note = e.message ?: "unknown error")
    }
    DialogHistory.addAssistant(safeText)
}
