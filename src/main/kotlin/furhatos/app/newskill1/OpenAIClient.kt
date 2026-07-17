package furhatos.app.newskill1

import io.github.sashirestela.openai.SimpleOpenAI
import io.github.sashirestela.openai.domain.chat.ChatMessage
import io.github.sashirestela.openai.domain.chat.ChatRequest
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Loads the OpenAI API key from src/main/resources/openai.properties
 * (file should contain: OPENAI_API_KEY=sk-...)
 * Falls back to environment variable OPENAI_API_KEY if the file is missing.
 */
object OpenAIClient {

    private val apiKey: String by lazy {
        val props = Properties()
        val stream = javaClass.classLoader.getResourceAsStream("openai.properties")
        if (stream != null) {
            props.load(stream)
            props.getProperty("OPENAI_API_KEY") ?: System.getenv("OPENAI_API_KEY") ?: ""
        } else {
            System.getenv("OPENAI_API_KEY") ?: ""
        }
    }

    private val client by lazy {
        SimpleOpenAI.builder()
            .apiKey(apiKey)
            .build()
    }

    /**
     * Generates a natural-language response.
     *
     * @param systemPrompt   Instructions describing the robot's role, scenario context,
     *                        and (if applicable) the EXACT failure content that MUST appear.
     * @param conversation   List of prior turns as (role, text) pairs, role = "user" or "assistant"
     * @param userText       The latest user utterance to respond to
     * @return generated text, or a fallback string if the API call fails
     */
    fun generateResponse(
        systemPrompt: String,
        conversation: List<Pair<String, String>>,
        userText: String,
        fallback: String
    ): String {
        if (apiKey.isBlank()) {
            println("[OpenAIClient] WARNING: no API key found, using fallback text.")
            return fallback
        }
        return try {
            val messages = mutableListOf<ChatMessage>()
            messages.add(ChatMessage.SystemMessage.of(systemPrompt))
            for ((role, text) in conversation) {
                if (role == "user") {
                    messages.add(ChatMessage.UserMessage.of(text))
                } else {
                    messages.add(ChatMessage.AssistantMessage.of(text))
                }
            }
            messages.add(ChatMessage.UserMessage.of(userText))

            val request = ChatRequest.builder()
                .model("gpt-4o-mini")
                .messages(messages)
                .temperature(0.8)
                .maxTokens(150)
                .build()

            // Bounded wait: a stalled network call must fall back, never hang the flow.
            val response = client.chatCompletions().create(request).get(12, TimeUnit.SECONDS)
            val text = response.firstContent()
            if (text.isNullOrBlank()) fallback else text.trim()
        } catch (e: TimeoutException) {
            println("[OpenAIClient] TIMEOUT calling OpenAI, using fallback text.")
            fallback
        } catch (e: Exception) {
            println("[OpenAIClient] ERROR calling OpenAI: ${e.message}")
            fallback
        }
    }
}
