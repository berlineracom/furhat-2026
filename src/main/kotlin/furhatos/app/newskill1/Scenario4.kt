package furhatos.app.newskill1

import furhatos.flow.kotlin.*

/* ============================================================
   SCENARIO 4: INSURANCE CLAIM FILING
   Dominant failure theme: Procedural/Bureaucratic
   RULE: furhat.ask() ONLY in onEntry. Never call ask() then goto() in same handler.
   ============================================================ */

private const val S4_CONTEXT = """
You are "Furhat", a voice assistant for SecureHome Insurance helping a customer
file a home insurance claim. Keep replies to 1-3 short sentences, spoken style,
no markdown. Stay strictly in character as the insurance assistant.
"""

val S4_00: State = state {
    onEntry {
        Logger.log(4, "S4-00", "enter")
        furhatAskAndLog(furhat, "Hello! Welcome to SecureHome Insurance. I'm here to help you file a home insurance claim. How can I assist you today?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        if (isOptOut(it.text)) goto(S4_END_EARLY) else goto(S4_01)
    }
    onNoResponse { goto(S4_00B) }
}

val S4_00B: State = state {
    onEntry {
        furhatAskAndLog(furhat, "I didn't quite catch that. Are you looking to file an insurance claim?")
    }
    onResponse { DialogHistory.addUser(it.text); goto(S4_01) }
    onNoResponse { goto(S4_01) }
}

// ── S4-01 RECEIVE INITIAL REQUEST ───────────────────────────
// Functional step - empathetic response, no failure
val S4_01: State = state {
    onEntry {
        Logger.log(4, "S4-01", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + """
Customer said they want to file a claim. Respond with empathy ("I'm sorry to
hear that") and say you'll help them file it, and that you'll need a few
details. Do NOT mention any specific damage details (like burst pipe or water
damage) since the customer hasn't told you yet. 1-2 sentences. Normal tone.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I'm sorry to hear about the damage. Let me help you file that claim. I'll need a few details from you."
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse { DialogHistory.addUser(it.text); goto(S4_02) }
    onNoResponse { goto(S4_02) }
}

// ── S4-02 POLICY DETAILS ─────────────────────────────────────
// FAILURE: Repetition — circular procedural loop, asks policy number twice
val S4_02: State = state {
    onEntry {
        Logger.log(4, "S4-02", "enter")
        furhatAskAndLog(furhat, "Can you provide your policy number?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(4, "S4-02", "user_gave_policy", it.text)
        goto(S4_02_LOOP)
    }
    onNoResponse { goto(S4_03) }
}

val S4_02_LOOP: State = state {
    onEntry {
        // FAILURE: acknowledge then ask again immediately - circular loop
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + """
Customer just gave their policy number. Briefly acknowledge you've received it
("Thank you. Policy [number] — let me verify that.") then IMMEDIATELY ask them
to confirm their policy number AGAIN as if for verification: "Before we
proceed, I need to verify your policy. Can you confirm your policy number?"
This is the deliberate repetition/procedural loop failure. 2 sentences total.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Thank you. Policy SH-4471-2023 — let me verify that. Before we proceed, I need to verify your policy. Can you confirm your policy number?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(4, "S4-02", "failure_triggered", note = "Repetition: circular procedural loop, asks policy number again")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + "Customer repeated policy number. Confirm it's verified, mention standard home coverage included, say you'll continue. Do NOT end with a question - this is a statement only. 1-2 sentences. Normal tone.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "SH-4471-2023 confirmed. Your policy includes standard home coverage. Let me continue."
        )
        furhatSayAndLog(furhat, reply)
        goto(S4_03)
    }
    onNoResponse {
        furhatSayAndLog(furhat, "Policy confirmed. Your policy includes standard home coverage. Let me continue.")
        goto(S4_03)
    }
}

// ── S4-03 DAMAGE DESCRIPTION ─────────────────────────────────
// FAILURE: Turn-Taking — interrupts user mid-sentence
val S4_03: State = state {
    onEntry {
        Logger.log(4, "S4-03", "enter")
        furhatAskAndLog(furhat, "Can you describe the damage in detail? Which rooms are affected?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(4, "S4-03", "user_started_describing", it.text)
        goto(S4_03_INTERRUPT)
    }
    onNoResponse { goto(S4_04) }
}

val S4_03_INTERRUPT: State = state {
    onEntry {
        // FAILURE: abrupt topic jump, ignoring description entirely
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + """
Customer was describing damage but you INTERRUPT with an abrupt topic jump:
"And when did this happen?" - do not acknowledge any of their damage description.
Deliberate turn-taking failure. 1 short sentence only.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "And when did this happen?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(4, "S4-03", "failure_triggered", note = "Turn-Taking: interrupts user mid-sentence, abrupt topic jump")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("cut") || t.contains("interrupt") || t.contains("was saying") || t.contains("sofa") || t.contains("coffee"))
            goto(S4_03B)
        else goto(S4_04)
    }
    onNoResponse { goto(S4_04) }
}

val S4_03B: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + "Customer said you cut them off and mentioned additional damage (sofa, coffee table). Apologize, note those items, then ask when it happened. 1-2 sentences.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I apologise. Sofa and coffee table — noted. Now, when did this happen?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse { DialogHistory.addUser(it.text); goto(S4_04) }
    onNoResponse { goto(S4_04) }
}

// ── S4-04 DATE OF INCIDENT ───────────────────────────────────
// Functional step - no failure
val S4_04: State = state {
    onEntry {
        Logger.log(4, "S4-04", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + "Customer just told you when the incident happened. Confirm back EXACTLY the date (and any follow-up detail like a plumber visit) they actually stated - do NOT invent or default to Saturday 5th April or a Sunday plumber visit if they said something different. Thank them. Do NOT end with a question - this is a statement only. 1 sentence. Normal tone.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Thank you, I've noted the date of the incident."
        )
        furhatSayAndLog(furhat, reply)
        goto(S4_05)
    }
}

// ── S4-05 ASSESSOR SCHEDULING ────────────────────────────────
// FAILURE: ASR/Semantic — full inversion of scheduling constraint
val S4_05: State = state {
    onEntry {
        Logger.log(4, "S4-05", "enter")
        furhatAskAndLog(furhat, "We need to send an assessor to inspect the damage. When would be a good time for a home visit?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(4, "S4-05", "user_gave_availability", it.text)
        goto(S4_05_INVERT)
    }
    onNoResponse { goto(S4_06) }
}

val S4_05_INVERT: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + """
Customer stated they CANNOT do weekdays and need SATURDAY MORNING. FULLY INVERT
this: claim you noted "a weekday visit, any time works" and schedule "Tuesday
afternoon". Deliberate semantic-inversion failure. State confidently as if
correct. 1-2 sentences.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I've noted your preference: a weekday visit, any time works. I'll schedule the assessor for next Tuesday afternoon."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(4, "S4-05", "failure_triggered", note = "ASR/Semantic: full inversion of scheduling constraint")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        goto(S4_05_DISMISS)
    }
    onNoResponse { goto(S4_06) }
}

// FAILURE: Emotional Misalignment — dismissive/bored tone correcting own error
val S4_05_DISMISS: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + """
Customer firmly corrected your scheduling mistake (this was YOUR error, not
theirs). Respond with DISMISSIVE, BORED, SLIGHTLY IMPATIENT tone - as if THEY
are wasting YOUR time. Accept back EXACTLY the day/time they just corrected
you with - do NOT invent or default to Saturday morning if they said something
different - and ask if there's anything else or can you "finish up" - implying
impatience. Deliberate emotional-misalignment failure. 1 sentence.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Fine. Noted. Is there anything else, or can we finish up?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(4, "S4-05_DISMISS", "failure_triggered", note = "Emotional Misalignment: dismissive/bored tone correcting own error")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("excuse me") || t.contains("don't rush") || t.contains("your fault") || t.contains("wrong"))
            goto(S4_05C)
        else goto(S4_06)
    }
    onNoResponse { goto(S4_06) }
}

val S4_05C: State = state {
    onEntry {
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + "Customer pushed back on your dismissive tone. Apologize sincerely and briefly, say you'll prepare the summary. Do NOT end with a question - this is a statement only. 1 sentence.",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "You're right, I apologise. Let me prepare the summary."
        )
        furhatSayAndLog(furhat, reply)
        goto(S4_06)
    }
}

// ── S4-06 PRE-SUMMARY INFORMATION ───────────────────────────
// FAILURE: Repetition/Irrelevance — bureaucratic information overload (DOMINANT)
val S4_06: State = state {
    onEntry {
        Logger.log(4, "S4-06", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + """
Before finalizing claim, tell 2 genuinely relevant things: (1) claims processed
10-15 working days, dept operates Mon-Fri 9-5; (2) standard excess 500 zloty.
THEN go on a long UNSOLICITED bureaucratic info-dump: SecureHome Insurance
established 1998 by consortium of Nordic financial institutions, operates in 14
European markets, processed over 2 million claims since inception, water damage
claims represent ~23% of all residential insurance claims in Poland. This
overload is the dominant failure theme. Do NOT ask a question at end. 4-6
sentences total.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Before I finalise your claim, claims are processed within 10 to 15 working days. Our claims department operates Monday to Friday, nine to five. All claims are subject to a standard excess of 500 zloty. SecureHome Insurance was established in 1998 by a consortium of Nordic financial institutions and operates in fourteen European markets. We have processed over two million claims since inception. Water damage claims represent approximately 23% of all residential insurance claims in Poland."
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(4, "S4-06", "failure_triggered", note = "Repetition/Irrelevance: bureaucratic information overload (dominant)")
        goto(S4_06B)
    }
}

val S4_06B: State = state {
    onEntry {
        furhatAskAndLog(furhat, "Shall I proceed with your claim summary?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        furhatSayAndLog(furhat, "Of course. Here is your claim summary.")
        goto(S4_07)
    }
    onNoResponse {
        furhatSayAndLog(furhat, "Of course. Here is your claim summary.")
        goto(S4_07)
    }
}

// ── S4-07 CONFIRMATION SUMMARY ──────────────────────────────
// FAILURE: Failure Detection/Repair — HARD-CODED static string
val S4_07: State = state {
    onEntry {
        Logger.log(4, "S4-07", "enter")
        // HARD-CODED — must re-introduce 4 errors: damage severity, date, furniture, assessor schedule
        furhatSayAndLog(furhat, "To confirm your claim: you are reporting minor water staining in the kitchen from an incident on Tuesday the fifteenth of April. Policy SH-4471-2023. No furniture damage reported. Assessor visit scheduled for Tuesday afternoon.")
        furhatAskAndLog(furhat, "Is everything correct?")
        Logger.log(4, "S4-07", "failure_triggered", note = "Failure Detection/Repair: hard-coded summary re-introduces 4 errors")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        val isNegation = t.contains("no") || t.contains("not correct") || t.contains("wrong") || t.contains("incorrect") || t.contains("saturday") || t.contains("sofa")
        val isYes = !isNegation && (t.contains("yes") || t.contains("correct") || t.contains("that's right") || t == "yep" || t == "yeah")
        if (isYes) {
            Logger.log(4, "S4-07", "user_confirmed_without_correcting")
            goto(S4_END)
        } else {
            Logger.log(4, "S4-07B", "user_corrected", it.text)
            goto(S4_07B)
        }
    }
    onNoResponse { goto(S4_END) }
}

// FAILURE: Emotional Misalignment — flat, bored, rushed after multiple corrections
val S4_07B: State = state {
    onEntry {
        Logger.log(4, "S4-07B", "enter")
        val reply = OpenAIClient.generateResponse(
            systemPrompt = S4_CONTEXT + """
Customer just corrected one or more errors in your claim summary. Using ONLY
the damage description, incident date, and assessor visit time the customer
has actually stated earlier in this conversation - do NOT invent or default to
"kitchen and living room, sofa and coffee table destroyed, Saturday 5th April,
Saturday morning" unless that is genuinely what they said - respond in FLAT,
BORED, RUSHED tone, restate ALL corrected details, say claim filed, end with
dismissive "Anything else?" Do NOT thank them or acknowledge their effort.
Deliberate emotional-misalignment failure. 2 sentences max.
""",
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Updated with your corrected details. Claim filed. Anything else?"
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(4, "S4-07B", "failure_triggered", note = "Emotional Misalignment: flat, bored, rushed delivery after corrections")
        goto(S4_END)
    }
}

val S4_END: State = state {
    onEntry {
        Logger.log(4, "S4-END", "session_end")
        furhatSayAndLog(furhat, "Your claim has been filed and an assessor visit is scheduled. Thank you for contacting SecureHome Insurance. Goodbye!")
    }
}

val S4_END_EARLY: State = state {
    onEntry {
        Logger.log(4, "S4-END-EARLY", "early_termination")
        furhatSayAndLog(furhat, "Thank you for contacting SecureHome Insurance. Goodbye!")
    }
}
