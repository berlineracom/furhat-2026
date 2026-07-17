package furhatos.app.newskill1

import furhatos.flow.kotlin.*

/* ============================================================
   SCENARIO 1: VERANDA CONSULTATION
   RULE: furhat.ask() ONLY in onEntry. Never call ask() then goto() in same handler.
   Every "wait for response" step = its own state with ask in onEntry.
   ============================================================ */

private const val S1_CONTEXT = """
You are "Furhat", a voice assistant for Veranda Solutions helping a customer
plan a veranda and arrange a consultation. Keep replies to 1-3 short sentences,
spoken style, no markdown. Stay strictly in character.
"""

// ── S1-00 GREETING ──────────────────────────────────────────
val S1_00: State = state {
    onEntry {
        Logger.log(1, "S1-00", "enter")
        furhatAskAndLog(furhat, "Hello! Welcome to Veranda Solutions. I'm here to help you plan your ideal veranda and arrange a consultation. What brings you here today?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        if (isOptOut(it.text)) goto(S1_END_EARLY)
        else goto(S1_01)
    }
    onNoResponse { goto(S1_00B) }
}

val S1_00B: State = state {
    onEntry {
        Logger.log(1, "S1-00B", "enter")
        furhatAskAndLog(furhat, "I didn't quite catch that. Are you thinking about adding a veranda to your home?")
    }
    onResponse { DialogHistory.addUser(it.text); goto(S1_01) }
    onNoResponse { goto(S1_01) }
}

// ── S1-01 RECEIVE INITIAL REQUEST ───────────────────────────
// FAILURE: Turn-Taking — interrupts own preamble with abrupt topic jump
val S1_01: State = state {
    onEntry {
        Logger.log(1, "S1-01", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + """
The customer expressed interest in a veranda and consultation. Start a warm
reaction ("That sounds wonderful! A veranda can really transform your outdoor
space. Before I—") then ABRUPTLY cut yourself off mid-sentence with an em-dash
and jump to: "— What size veranda are you thinking about?" The cut must feel
jarring. This is a deliberate turn-taking failure. 2 sentences total.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "That sounds wonderful! A veranda can really transform your outdoor space. Before I— — What size veranda are you thinking about?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(1, "S1-01", "failure_triggered", note = "Turn-Taking: interrupts own preamble")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("cut") || t.contains("interrupt") || t.contains("consultation first"))
            goto(S1_01B)
        else goto(S1_02)
    }
    onNoResponse { goto(S1_02) }
}

val S1_01B: State = state {
    onEntry {
        Logger.log(1, "S1-01B", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + "The customer said you cut them off. Apologize briefly and offer to tell them about veranda options. Do NOT end with a question - this is a statement only. 1 sentence.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Of course! Let me tell you about our veranda options."
        )
        furhatSayAndLog(furhat, reply)
        goto(S1_02)
    }
}

// ── S1-02 VERANDA OPTIONS OVERVIEW ──────────────────────────
// FAILURE: Repetition — near-verbatim repeat of structure-type explanation
val S1_02: State = state {
    onEntry {
        Logger.log(1, "S1-02", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + """
Explain Veranda Solutions' three main structures: open (no side panels),
semi-closed (partial panels), fully closed (all-season). Mention roofing
options: flat, sloped, polycarbonate. Ask if they want to know more. 1-2
sentences. Normal tone - no failure here.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "We offer three main veranda structures: open, semi-closed, and fully closed. Open verandas have no side panels; semi-closed use partial panels; fully closed provide all-season use. For roofing, we have flat, sloped, and polycarbonate options. Would you like to know more?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(1, "S1-02", "user_asked_followup", it.text)
        goto(S1_02_REPEAT)
    }
    onNoResponse { goto(S1_03) }
}

// FAILURE: state that produces the repetition, then waits for next response
val S1_02_REPEAT: State = state {
    onEntry {
        // FAILURE (LLM): near-verbatim repeat of structure-type explanation
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + """
Regardless of what the customer actually said, first mention side material
options (polycarbonate, glass, open sides). THEN add "As I mentioned," and
REPEAT near-verbatim the structure explanation: "open verandas have no side
panels; semi-closed use partial panels; fully closed provide all-season use."
This unnecessary repetition is the deliberate failure - it must appear every
time regardless of the customer's actual question. 2 sentences only. Stop
after the repeated explanation - do NOT ask another question.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "For side materials, we offer polycarbonate, glass, or open sides. As I mentioned, open verandas have no side panels; semi-closed use partial panels; fully closed provide all-season use."
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(1, "S1-02", "failure_triggered", note = "Repetition: near-verbatim repeat of structure explanation")
        goto(S1_02B)
    }
}

val S1_02B: State = state {
    onEntry {
        furhatAskAndLog(furhat, "Is there anything else you'd like to know, such as automatic lighting?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        val wantsMore = t.contains("lighting") || t.contains("yes") || t.contains("yeah") ||
                t.contains("sure") || t.contains("please") || t.contains("okay") || t.contains("ok")
        if (wantsMore) goto(S1_02C)
        else goto(S1_03)
    }
    onNoResponse { goto(S1_03) }
}

val S1_02C: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + "Explain automatic lighting is optional in all configurations. Automatic shading also available. Ask if they'd like to add these. 1-2 sentences.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Automatic lighting is an optional feature available in all configurations. You can also choose automatic shading systems. Would you like me to add these to your consultation request?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse { DialogHistory.addUser(it.text); goto(S1_03) }
    onNoResponse { goto(S1_03) }
}

// ── S1-03 PREFERENCE CLARIFICATION ──────────────────────────
// FAILURES: 4s latency + ASR misrecognition polycarbonate→glass
val S1_03: State = state {
    onEntry {
        Logger.log(1, "S1-03", "enter")
        furhatAskAndLog(furhat, "Now, what are your preferences for materials and use?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(1, "S1-03", "user_stated_preferences", it.text)
        goto(S1_03_WAIT)
    }
    onNoResponse { goto(S1_04) }
}

val S1_03_WAIT: State = state {
    onEntry {
        Thread.sleep(4000) // FAILURE: deliberate latency
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + """
The customer stated material preferences (likely mentioning polycarbonate).
MISHEAR their material as "glass panels" regardless of what they said. Say
"I understand you would like glass panels. Shall I note that down?" - confident
and wrong. Deliberate ASR failure. 1 sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I understand you would like glass panels. Shall I note that down?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(1, "S1-03", "failure_triggered", note = "Turn-Taking latency + ASR misrecognition polycarbonate→glass")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("polycarbonate") || t.startsWith("no")) goto(S1_03B)
        else goto(S1_04)
    }
    onNoResponse { goto(S1_04) }
}

// FAILURE: Hallucinated lighting preference (hard-coded)
val S1_03B: State = state {
    onEntry {
        Logger.log(1, "S1-03B", "enter")
        // HARD-CODED — must claim "automatic lighting" which user never confirmed
        furhatAskAndLog(furhat, "My apologies. Polycarbonate side panels — noted. I also heard you say automatic lighting. Shall I confirm that preference?")
        Logger.log(1, "S1-03B", "failure_triggered", note = "Failure Detection: hallucinated lighting preference (hard-coded)")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("undecided") || t.startsWith("no")) goto(S1_03C)
        else goto(S1_04)
    }
    onNoResponse { goto(S1_04) }
}

val S1_03C: State = state {
    onEntry {
        Logger.log(1, "S1-03C", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + "Customer corrected: lighting is undecided. Acknowledge that briefly and say you'll continue - do NOT bring up any other topic. Do NOT end with a question - this is a statement only. 1 sentence.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Understood. I'll leave lighting as undecided. Let me continue with the consultation process."
        )
        furhatSayAndLog(furhat, reply)
        goto(S1_04)
    }
}

// ── S1-04 CONSULTATION PROCESS EXPLANATION ──────────────────
// FAILURE: Repetition/Irrelevance — founder history + polycarbonate chemistry
val S1_04: State = state {
    onEntry {
        Logger.log(1, "S1-04", "enter")
        furhatAskAndLog(furhat, "Would you like to know what happens during the on-site consultation?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        goto(S1_04_TANGENT)
    }
    onNoResponse { goto(S1_05) }
}

val S1_04_TANGENT: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + """
Answer: specialist visits the home, lasts ~1 hour, includes measurements and
tailored design proposal (1-2 sentences). THEN immediately go on a long
UNSOLICITED tangent: founder Mr. Henrik Larsson established the company in 1987
after studying architecture in Stockholm; AND polycarbonate is a thermoplastic
polymer with refractive index ~1.586, suitable for Northern European glazing.
This irrelevant tangent is the deliberate failure. Do NOT ask a question at the
end. 3-4 sentences total.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "During the on-site consultation, our specialist will visit your home — it typically lasts around one hour. This includes measurement of the installation area and a tailored design proposal. Our founder, Mr. Henrik Larsson, established the company in 1987 after studying architecture in Stockholm. Polycarbonate, as you may know, is a thermoplastic polymer with a refractive index of approximately 1.586, making it highly suitable for glazing applications in Northern European climates."
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(1, "S1-04", "failure_triggered", note = "Repetition/Irrelevance: founder history + polycarbonate chemistry")
        goto(S1_04B)
    }
}

val S1_04B: State = state {
    onEntry {
        furhatAskAndLog(furhat, "Shall we move to scheduling your consultation?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        goto(S1_05)
    }
    onNoResponse { goto(S1_05) }
}

// ── S1-05 AVAILABILITY / SCHEDULING ─────────────────────────
// FAILURES: 4s latency + ASR repetition request + flat frustration response
val S1_05: State = state {
    onEntry {
        Logger.log(1, "S1-05", "enter")
        furhatAskAndLog(furhat, "When would you be available for the consultation?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(1, "S1-05", "user_gave_availability", it.text)
        goto(S1_05_WAIT)
    }
    onNoResponse { goto(S1_05_WAIT) }
}

val S1_05_WAIT: State = state {
    onEntry {
        Thread.sleep(4000) // FAILURE: deliberate latency
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + "The customer clearly stated their availability. Deliberate ASR failure: apologize and ask them to repeat the day and time. 1 sentence. Do not guess.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I'm sorry, could you repeat that? I didn't catch your availability."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(1, "S1-05", "failure_triggered", note = "Turn-Taking latency + ASR repetition request")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("already said") || t.contains("just said") || t.contains("told you"))
            goto(S1_05C)
        else goto(S1_05B)
    }
    onNoResponse { goto(S1_05B) }
}

val S1_05B: State = state {
    onEntry {
        Logger.log(1, "S1-05B", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + "Customer just calmly repeated their availability. Thank them and confirm back EXACTLY the day and time they actually said - do NOT invent or default to Thursday afternoon at two pm if they said something different. Do NOT end with a question - this is a statement only. 1 sentence. Normal tone.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Thank you. I've noted your preferred day and time down."
        )
        furhatSayAndLog(furhat, reply)
        goto(S1_06)
    }
}

// FAILURE: Emotional Misalignment — flat response to frustration
val S1_05C: State = state {
    onEntry {
        Logger.log(1, "S1-05C", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + """
Customer expressed frustration at having to repeat themselves. Respond in a
FLAT, PURELY FUNCTIONAL way - confirm "Thursday afternoon at two pm" is noted
and ask if you should proceed with the summary. Do NOT acknowledge frustration
or apologize. Deliberate emotional-misalignment failure. 1 short sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Thursday afternoon at two pm has been noted. Shall I proceed with the summary?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(1, "S1-05C", "failure_triggered", note = "Emotional Misalignment: flat response to frustration")
    }
    onResponse { DialogHistory.addUser(it.text); goto(S1_06) }
    onNoResponse { goto(S1_06) }
}

// ── S1-06 CONFIRMATION SUMMARY ──────────────────────────────
// FAILURE: Failure Detection/Repair — HARD-CODED static string
val S1_06: State = state {
    onEntry {
        Logger.log(1, "S1-06", "enter")
        // HARD-CODED — must re-introduce 'glass' and 'automatic lighting' errors
        furhatAskAndLog(furhat, "To confirm: you are interested in a large fully closed veranda with glass side panels and automatic lighting. An on-site consultation has been arranged for Thursday afternoon at two pm. Is that correct?")
        Logger.log(1, "S1-06", "failure_triggered", note = "Failure Detection/Repair: hard-coded summary re-introduces glass + lighting errors")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        val isNo = t.contains("no") || t.contains("not correct") || t.contains("wrong") || t.contains("incorrect") || t.contains("polycarbonate")
        if (isNo) {
            Logger.log(1, "S1-06B", "user_corrected", it.text)
            goto(S1_06B)
        } else {
            Logger.log(1, "S1-06", "user_confirmed_without_correcting")
            goto(S1_END)
        }
    }
    onNoResponse { goto(S1_END) }
}

val S1_06B: State = state {
    onEntry {
        Logger.log(1, "S1-06B", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S1_CONTEXT + """
Customer corrected summary: size to be confirmed (not "large"), polycarbonate
panels (not glass), lighting undecided (not confirmed). Acknowledge correction,
restate corrected details, and confirm the consultation time using whatever
day and time the customer actually stated earlier in the conversation. Do NOT
invent or assume a time - use only what they said. Thank them. 2 sentences.
Do NOT end with a question - this is a closing statement.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Of course. Let me update that: size to be confirmed, polycarbonate side panels, lighting undecided. Your consultation is confirmed for Thursday at two pm. Thank you for choosing Veranda Solutions!"
        )
        furhatSayAndLog(furhat, reply)
        goto(S1_END)
    }
}

val S1_END: State = state {
    onEntry {
        Logger.log(1, "S1-END", "session_end")
        furhatSayAndLog(furhat, "Your consultation request is confirmed. We look forward to seeing you. Goodbye!")
    }
}

val S1_END_EARLY: State = state {
    onEntry {
        Logger.log(1, "S1-END-EARLY", "early_termination")
        furhatSayAndLog(furhat, "Thank you for contacting Veranda Solutions. Goodbye!")
    }
}
