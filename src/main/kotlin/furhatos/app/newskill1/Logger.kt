package furhatos.app.newskill1

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Simple CSV logger. Writes one line per logged event.
 * File is created in the skill's working directory as interaction_log.csv
 */
object Logger {
    private val file = File("interaction_log.csv")
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    init {
        if (!file.exists()) {
            file.writeText("timestamp,scenario,node,event_type,user_utterance,note\n")
        }
    }

    fun log(scenario: Int, node: String, eventType: String, userUtterance: String = "", note: String = "") {
        val timestamp = timeFormat.format(Date())
        val line = listOf(timestamp, scenario.toString(), node, eventType, sanitize(userUtterance), sanitize(note))
            .joinToString(",") { "\"$it\"" }
        file.appendText(line + "\n")
        println("[LOG] $line")
    }

    private fun sanitize(s: String): String {
        return s.replace("\"", "'").replace("\n", " ")
    }
}
