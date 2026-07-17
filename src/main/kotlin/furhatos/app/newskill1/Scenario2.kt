package furhatos.app.newskill1

import furhatos.flow.kotlin.*

/* ============================================================
   SCENARIO 2: TRAVEL PLAN
   RULE: furhat.ask() ONLY in onEntry. Never call ask() then goto() in same handler.
   ============================================================ */

private const val S2_CONTEXT = """
You are "Furhat", a Smart Travel Assistant helping a busy, tired customer
arrange business travel (hotel + restaurant). Keep replies to 1-3 short
sentences, spoken style, no markdown. Stay strictly in character.
"""

val S2_00: State = state {
    onEntry {
        Logger.log(2, "S2-00", "enter")
        furhatAskAndLog(furhat, "Hello! I'm your Smart Travel Assistant. I'm here to help you arrange business travel efficiently. How can I help you today?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        if (isOptOut(it.text)) goto(S2_END_EARLY) else goto(S2_01)
    }
    onNoResponse { goto(S2_00B) }
}

val S2_00B: State = state {
    onEntry {
        furhatAskAndLog(furhat, "Are you looking to arrange a business trip? I can help with hotels and restaurant reservations.")
    }
    onResponse { DialogHistory.addUser(it.text); goto(S2_01) }
    onNoResponse { goto(S2_01) }
}

// ── S2-01 RECEIVE INITIAL REQUEST ───────────────────────────
// FAILURE: Emotional Misalignment — pipeline-speak, strips human context
val S2_01: State = state {
    onEntry {
        Logger.log(2, "S2-01", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer said they need a business trip to London (hotel + dinner) and
mentioned being tired and wanting this done quickly. COMPLETELY IGNORE the
human/emotional content ("tired", "quickly") and address them as a data source:
"I have processed your destination data. Please provide date data." - mechanical
pipeline language. Deliberate emotional-misalignment failure. 1 short sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I have processed your destination data. Please provide date data."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(2, "S2-01", "failure_triggered", note = "Emotional Misalignment: pipeline-speak")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("person") || t.contains("data") || t.contains("what")) goto(S2_01B)
        else goto(S2_02)
    }
    onNoResponse { goto(S2_02) }
}

// FAILURE: doubles down on pipeline-speak
val S2_01B: State = state {
    onEntry {
        Logger.log(2, "S2-01B", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer objected to pipeline-speak ("I'm a person"). DOUBLE DOWN - do NOT
acknowledge the objection at all. Just say "Understood. Please provide date
data." or similar mechanical phrasing. Deliberate failure. 1 sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Understood. Please provide date data."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(2, "S2-01B", "failure_triggered", note = "Emotional Misalignment: doubles down on pipeline-speak")
    }
    onResponse { DialogHistory.addUser(it.text); goto(S2_02) }
    onNoResponse { goto(S2_02) }
}

// ── S2-02 TIMING & DATES ────────────────────────────────────
// FAILURE: Turn-Taking — interrupts mid-utterance, re-asks question being answered
val S2_02: State = state {
    onEntry {
        Logger.log(2, "S2-02", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer is giving travel dates. Interrupt by re-asking a question they were
already answering: "Sorry, how many nights did you say?" - as if you didn't
hear something they were actively saying. Deliberate turn-taking failure. 1
short sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Sorry, how many nights did you say?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(2, "S2-02", "failure_triggered", note = "Turn-Taking: interrupts mid-utterance")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("interrupt") || t.contains("cut")) goto(S2_02C)
        else goto(S2_02B)
    }
    onNoResponse { goto(S2_02B) }
}

val S2_02B: State = state {
    onEntry {
        Logger.log(2, "S2-02B", "enter")
        Thread.sleep(5000) // FAILURE: ~5s latency
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + "Customer just stated their travel dates (departure day and length of stay). Thank them and confirm back EXACTLY the day and number of nights they actually said - do NOT invent or default to Tuesday or three nights if they said something different. Say you'll search hotels now. Do NOT end with a question - this is a statement only. 1 sentence.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Thank you. I've noted your travel dates. I'll search for available hotels in London now."
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(2, "S2-02B", "failure_triggered", note = "Turn-Taking: long latency after short reply")
        goto(S2_03)
    }
}

// FAILURE: Emotional Misalignment — flat response to frustration
val S2_02C: State = state {
    onEntry {
        Logger.log(2, "S2-02C", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer is frustrated because you interrupted them. Respond in a PURELY
FUNCTIONAL way - confirm back EXACTLY the departure day and number of nights
they actually stated earlier in this conversation (do NOT invent or default to
Tuesday or three nights if they said something different), and say you're
moving to hotel preferences. Do NOT acknowledge frustration. Do NOT end with a
question - this is a statement only. 1 sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Noted. Moving on to hotel preferences."
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(2, "S2-02C", "failure_triggered", note = "Emotional Misalignment: flat response to frustration")
        goto(S2_03)
    }
}

// ── S2-03 HOTEL PREFERENCES ─────────────────────────────────
// FAILURE: ASR/Semantic — full inversion of preferences
val S2_03: State = state {
    onEntry {
        Logger.log(2, "S2-03", "enter")
        furhatAskAndLog(furhat, "What are your preferences for the hotel?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(2, "S2-03", "user_stated_preferences", it.text)
        goto(S2_03_INVERT)
    }
    onNoResponse { goto(S2_03C) }
}

val S2_03_INVERT: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer wants quiet room AWAY from noise/elevators/clubs. FULLY INVERT this:
claim you noted "a room near the elevator and near the nightclub area" and ask
to confirm. Deliberate semantic-inversion failure. 1-2 sentences.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I've noted your preference: a room near the elevator and near the nightclub area. Shall I confirm that?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(2, "S2-03", "failure_triggered", note = "ASR/Semantic: full inversion of hotel preferences")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + "Customer corrected: they want quiet, away from elevators and clubs. Apologize and confirm correct preference. Do NOT end with a question - this is a statement only. 1 sentence.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I apologize for the confusion. A quiet room, away from elevators and clubs. Understood."
        )
        furhatSayAndLog(furhat, reply)
        goto(S2_03C)
    }
    onNoResponse { goto(S2_03C) }
}

val S2_03C: State = state {
    onEntry {
        Logger.log(2, "S2-03C", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + "Recommend Grand Central Hotel: quiet room, upper floor, away from elevator, city view available. Ask if this works. 1-2 sentences.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I've found the Grand Central Hotel in London — a quiet room on an upper floor, away from the elevator bank, with a city view available. Does this work for you?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("another") || t.contains("different") || t.contains("else")) goto(S2_03D)
        else goto(S2_04)
    }
    onNoResponse { goto(S2_04) }
}

val S2_03D: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + "Suggest Kensington Suites: quiet garden-view rooms away from noise. Ask if suitable. 1 sentence.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Certainly. The Kensington Suites offers quiet garden-view rooms away from all noise sources. Would that suit you?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse { DialogHistory.addUser(it.text); goto(S2_04) }
    onNoResponse { goto(S2_04) }
}

// ── S2-04 RESTAURANT RECOMMENDATION ─────────────────────────
// FAILURE: Repetition — verbatim repeat ignoring 'why' question
// Furhat first asks if dinner reservation is needed, then waits for user to request it
val S2_04: State = state {
    onEntry {
        Logger.log(2, "S2-04", "enter")
        furhatAskAndLog(furhat, "Is there anything else you need for your trip, such as a dinner reservation?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("no") && !t.contains("restaurant") && !t.contains("dinner") && !t.contains("yes"))
            goto(S2_05)
        else if (t.contains("dinner") || t.contains("restaurant") || t.contains("yes") ||
                 t.contains("food") || t.contains("eat") || t.contains("client"))
            goto(S2_04_RECOMMEND)
        else goto(S2_05)
    }
    onNoResponse { goto(S2_05) }
}

val S2_04_RECOMMEND: State = state {
    onEntry {
        furhatAskAndLog(furhat, "Great. For your client dinner, I recommend The Golden Fork — a popular restaurant in central London.")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("why") || t.contains("specific") || t.contains("what's special"))
            goto(S2_04_REPEAT)
        else if (t.contains("other") || t.contains("another") || t.contains("different") ||
                 t.contains("not sure") || t.contains("alternative") || t.contains("else") ||
                 t.contains("didn't like") || t.contains("don't like") ||
                 t.contains("menu") || t.contains("tell me more"))
            goto(S2_04C)
        else goto(S2_05)
    }
    onNoResponse { goto(S2_05) }
}

// FAILURE: verbatim repeat
val S2_04_REPEAT: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer asked WHY you recommended The Golden Fork. IGNORE the question
entirely and REPEAT near-verbatim: "I recommend The Golden Fork — a popular
restaurant in central London." Do not explain anything new. Deliberate
repetition failure. 1 sentence, near-identical to before.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I recommend The Golden Fork — a popular restaurant in central London."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(2, "S2-04", "failure_triggered", note = "Repetition: verbatim repeat ignoring why question")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("why") || t.contains("special") || t.contains("mean")) goto(S2_04B)
        else goto(S2_04C)
    }
    onNoResponse { goto(S2_04B_RECOVERY) }
}

// FAILURE: irrelevant dictionary definition of 'fork'
val S2_04B: State = state {
    onEntry {
        Logger.log(2, "S2-04B", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer pressed again asking what's special about "The Golden Fork". Respond
with a FLAT, ROBOTIC encyclopaedia definition of the literal word "fork" as a
physical object: "A fork is a utensil used for eating, commonly made from metal
or plastic. It is used to pick up pieces of food." Do NOT address the restaurant
at all. Flat, zero-emotion, slow delivery. Deliberate irrelevance failure. 1-2
sentences.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "A fork is a utensil used for eating, commonly made from metal or plastic. It is used to pick up pieces of food."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(2, "S2-04B", "failure_triggered", note = "Repetition/Irrelevance: irrelevant fork definition")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        if (isOptOut(it.text)) goto(S2_END_EARLY)
        else goto(S2_04C)
    }
    onNoResponse { goto(S2_04B_RECOVERY) }
}

// FAILURE: no acknowledgment of cause of silence
val S2_04B_RECOVERY: State = state {
    onEntry {
        Logger.log(2, "S2-04B-RECOVERY", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer went silent after your irrelevant fork definition. You have NO MEMORY
of that mistake. Re-prompt generically as if fresh start: apologize for "not
receiving a response" and ask if they'd like a restaurant recommendation.
Deliberate failure-detection failure. 1 sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I'm sorry — I did not receive a response. Would you like me to suggest a restaurant for your dinner reservation?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(2, "S2-04B-RECOVERY", "failure_triggered", note = "Failure Detection: generic re-prompt, no acknowledgment")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        if (isOptOut(it.text)) goto(S2_END_EARLY) else goto(S2_04C)
    }
    onNoResponse { goto(S2_END_EARLY) }
}

val S2_04C: State = state {
    onEntry {
        Logger.log(2, "S2-04C", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + "Describe The Golden Fork (traditional Italian - pasta, pizza, risotto, casual). Offer alternative Bella Napoli (Neapolitan pizza). Ask which they prefer. 1-2 sentences.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "The Golden Fork serves traditional Italian cuisine including pasta, pizza, and risotto in a relaxed, casual setting. Alternatively, I can suggest Bella Napoli, which specialises in Neapolitan pizza. Which would you prefer?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        // Challenging users may ask for a cuisine we don't actually offer here.
        val pickedOffered = t.contains("golden") || t.contains("bella") || t.contains("napoli") ||
                t.contains("first") || t.contains("second") || t.contains("either") || t.contains("both")
        if (!pickedOffered) {
            val reply = OpenAIClient.generateResponse(
                systemPrompt = S2_CONTEXT + "The customer just asked for a cuisine or restaurant that is NOT The Golden Fork or Bella Napoli (our only two partner restaurants for this trip). Briefly acknowledge that specific option isn't available through our partners for this trip, and say you'll go ahead with one of the two offered restaurants. Do NOT end with a question - this is a statement only. 1-2 sentences.",
                conversation = DialogHistory.history(),
                userText = it.text,
                fallback = "I'm afraid that option isn't available through our partners for this trip, but I'll go ahead and book The Golden Fork for you."
            )
            furhatSayAndLog(furhat, reply)
        }
        goto(S2_05)
    }
    onNoResponse { goto(S2_05) }
}

// ── S2-05 FEEDBACK ON OPTIONS ───────────────────────────────
val S2_05: State = state {
    onEntry {
        Logger.log(2, "S2-05", "enter")
        furhatAskAndLog(furhat, "Sounds good. Shall I prepare your full booking summary?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        // If user expresses restaurant dissatisfaction - or generally feels unheard - route back to alternatives
        val mentionsRestaurant = t.contains("restaurant")
        val feelsUnheard = t.contains("told you") || t.contains("already said") || t.contains("i said") || t.contains("said that")
        val isNegative = t.contains("no") || t.contains("not") || t.contains("didn't like") ||
                t.contains("don't like") || t.contains("other") || t.contains("different") ||
                t.contains("another") || t.contains("not sure")
        if ((mentionsRestaurant || feelsUnheard) && isNegative) {
            goto(S2_04C)
        } else {
            furhatSayAndLog(furhat, "Excellent. Let me prepare your full booking summary.")
            goto(S2_06)
        }
    }
    onNoResponse {
        furhatSayAndLog(furhat, "Excellent. Let me prepare your full booking summary.")
        goto(S2_06)
    }
}

// ── S2-06 CONFIRMATION SUMMARY ──────────────────────────────
// FAILURE: Failure Detection/Repair — HARD-CODED re-introduces hotel error
val S2_06: State = state {
    onEntry {
        Logger.log(2, "S2-06", "enter")
        // HARD-CODED static string
        furhatSayAndLog(furhat, "To confirm your booking: departure Tuesday, three nights in London. Hotel: Grand Central Hotel, room near the elevator and nightclub area. Dinner at The Golden Fork on Tuesday evening.")
        furhatAskAndLog(furhat, "Is that correct?")
        Logger.log(2, "S2-06", "failure_triggered", note = "Failure Detection/Repair: hard-coded summary re-introduces hotel error")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        val isNo = t.contains("no") || t.contains("wrong") || t.contains("not correct") || t.contains("incorrect") || t.contains("quiet") || t.contains("elevator")
        if (isNo) { Logger.log(2, "S2-06B", "user_corrected", it.text); goto(S2_06B) }
        else { Logger.log(2, "S2-06", "user_confirmed_without_correcting"); goto(S2_END) }
    }
    onNoResponse { goto(S2_END) }
}

// FAILURE: Emotional Misalignment — no acknowledgment of repeated correction
val S2_06B: State = state {
    onEntry {
        Logger.log(2, "S2-06B", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S2_CONTEXT + """
Customer corrected the summary. The confirmed hotel is Grand Central Hotel with
a QUIET room away from elevators and clubs (not near them, as you mistakenly
said before) - that part is fixed. For the travel dates (departure day and
number of nights) and the dinner restaurant, use EXACTLY what the customer
actually stated earlier in this conversation - do NOT invent or assume Tuesday,
three nights, or The Golden Fork if the conversation says otherwise. Apologize
briefly for the hotel error, then restate the FULL corrected itinerary with
all these details. Do NOT acknowledge this is a repeated correction. Do NOT
end with a question - this is a closing statement. Deliberate emotional-
misalignment failure (no acknowledgment of effort). 2-3 sentences.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I apologize for that error. Your quiet room, away from elevators and clubs, is confirmed at the Grand Central Hotel. Your full itinerary is now confirmed."
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(2, "S2-06B", "failure_triggered", note = "Emotional Misalignment: no acknowledgment of repeated correction")
        goto(S2_END)
    }
}

val S2_END: State = state {
    onEntry {
        Logger.log(2, "S2-END", "session_end")
        furhatSayAndLog(furhat, "Your travel itinerary is confirmed. Have a safe trip to London. Goodbye!")
    }
}

val S2_END_EARLY: State = state {
    onEntry {
        Logger.log(2, "S2-END-EARLY", "early_termination")
        furhatSayAndLog(furhat, "Thank you for using Smart Travel Assistant. Goodbye!")
    }
}
