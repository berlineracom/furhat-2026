package furhatos.app.newskill1

/**
 * Simple per-session conversation history shared across all scenario flows.
 * Stores (role, text) pairs in order: "assistant" = Furhat, "user" = participant.
 */
object DialogHistory {
    private val turns = mutableListOf<Pair<String, String>>()

    fun addUser(text: String) {
        turns.add("user" to text)
        trim()
    }

    fun addAssistant(text: String) {
        turns.add("assistant" to text)
        trim()
    }

    fun history(): List<Pair<String, String>> = turns.toList()

    fun reset() {
        turns.clear()
    }

    // Keep the history bounded so prompts stay short
    private fun trim() {
        while (turns.size > 16) {
            turns.removeAt(0)
        }
    }
}
