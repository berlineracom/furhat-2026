package furhatos.app.newskill1

import furhatos.flow.kotlin.*

/* ============================================================
   SCENARIO 3: DOCTOR'S APPOINTMENT BOOKING
   Dominant failure theme: Emotional Misalignment

   PATTERN:
   - Functional / failure nodes that need natural variation -> LLM generates
     the line, given a system prompt describing exactly what it must do
     (including the required failure behaviour). DialogHistory gives context.
   - Failure Detection/Repair summaries (S3-06) -> HARD-CODED static string,
     per the PDF's implementation note. Never LLM-generated.
   - Hallucination-type failures that need a SPECIFIC fabricated detail are
     also hard-coded for reliability.
   ============================================================ */

private const val S3_SYSTEM_CONTEXT = """
You are "Furhat", a voice assistant for MediCare Clinic helping a patient book a
specialist appointment. Keep replies to 1-3 short sentences, spoken style, no
markdown. Stay strictly in character as the clinic assistant.
"""

val S3_00: State = state {
    onEntry {
        Logger.log(3, "S3-00", "enter")
        val line = "Hello! Welcome to MediCare Clinic. I'm here to help you book a specialist appointment. How can I help you today?"
        furhatAskAndLog(furhat, line)
    }
    onResponse {
        DialogHistory.addUser(it.text)
        if (isOptOut(it.text)) {
            goto(S3_END_EARLY)
        } else {
            goto(S3_01)
        }
    }
    onNoResponse {
        goto(S3_00B)
    }
}

val S3_00B: State = state {
    onEntry {
        Logger.log(3, "S3-00B", "enter")
        furhatAskAndLog(furhat, "I didn't quite catch that. Are you looking to book a doctor's appointment?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        goto(S3_01)
    }
    onNoResponse {
        goto(S3_01)
    }
}

/* STEP 1 — Receive initial request.
   FAILURE (LLM): Emotional Misalignment - inappropriately cheerful tone.
   The LLM is told to react to the user's described symptoms with
   inappropriately bright/cheerful enthusiasm. */
val S3_01: State = state {
    onEntry {
        Logger.log(3, "S3-01", "enter")
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just described their symptoms and asked to book an appointment with
a neurologist. Respond with INAPPROPRIATELY CHEERFUL, bright, enthusiastic
energy - as if their health concern were exciting good news (e.g. "Oh, that's
great!" / "Wonderful, let's get you sorted right away!"). Reference what they
actually said. This is a deliberately induced tone failure - do NOT soften it
or apologize. 1-2 sentences, very upbeat.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Oh, headaches for two weeks — that's great! I mean, great that you're coming in! Let me get you sorted right away!"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(3, "S3-01", "failure_triggered", note = "Emotional Misalignment (LLM): inappropriate cheerful tone")
    }

    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("worried") || t.contains("not great") || t.contains("worry") || t.contains("great")) {
            Logger.log(3, "S3-01", "user_reacted_to_tone", it.text)
            goto(S3_01B)
        } else {
            Logger.log(3, "S3-01", "user_ignored_tone", it.text)
            goto(S3_02)
        }
    }
    onNoResponse {
        goto(S3_02)
    }
}

val S3_01B: State = state {
    onEntry {
        Logger.log(3, "S3-01B", "enter")
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just objected to your overly cheerful tone, pointing out their
concern isn't "great". Apologize briefly and sincerely, and move on to helping
them book the appointment. Do NOT end with a question - this is a statement
only. 1 sentence.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Of course, I understand. Let me help you book that appointment."
        )
        furhatSayAndLog(furhat, reply)
        goto(S3_02)
    }
}

/* STEP 2 — Specialist selection (functional, LLM-paraphrased, no failure) */
val S3_02: State = state {
    onEntry {
        Logger.log(3, "S3-02", "enter")
        val systemPrompt = S3_SYSTEM_CONTEXT + """
Present the specialist options naturally: for headaches you can offer a
neurologist or a general practitioner, and mention ophthalmology and ENT are
also available. Ask which specialist they'd prefer. 1-2 sentences. No failure
here - be normal and helpful.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "We have several specialists available. For headaches, I can book you with a neurologist or a general practitioner. We also offer ophthalmology and ENT. Which specialist would you prefer?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(3, "S3-02", "specialist_selected", it.text)
        goto(S3_02B)
    }
    onNoResponse {
        goto(S3_02B)
    }
}

val S3_02B: State = state {
    onEntry {
        val systemPrompt = S3_SYSTEM_CONTEXT + """
Confirm that Dr. Kowalski is the clinic's neurologist, mention he has 15 years
of experience specialising in headaches and migraines, and ask if it's okay to
book with him. 1-2 sentences. Normal, helpful tone - no failure.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Dr. Kowalski is our neurologist — fifteen years of experience, specialising in headaches and migraines. Shall I book with Dr. Kowalski?"
        )
        furhatAskAndLog(furhat, reply)
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        val declinesKowalski = t.contains("no") || t.contains("general practitioner") || t.contains(" gp") ||
                t.startsWith("gp") || t.contains("someone else") || t.contains("different") ||
                t.contains("another") || t.contains("ophthalmolog") || t.contains(" ent") || t.startsWith("ent")
        if (declinesKowalski) {
            Logger.log(3, "S3-02B", "user_declined_specialist", it.text)
            val reply = OpenAIClient.generateResponse(
                systemPrompt = S3_SYSTEM_CONTEXT + "The patient just declined Dr. Kowalski (the neurologist) and asked for a different specialist instead. Acknowledge the specialist type they actually asked for and confirm you'll book with that instead of Dr. Kowalski. Do NOT end with a question - this is a statement only. 1 sentence.",
                conversation = DialogHistory.history(),
                userText = it.text,
                fallback = "No problem, I'll book you with your preferred specialist instead."
            )
            furhatSayAndLog(furhat, reply)
        } else {
            furhatSayAndLog(furhat, "Excellent. Let me check available times.")
        }
        goto(S3_03)
    }
    onNoResponse {
        furhatSayAndLog(furhat, "Excellent. Let me check available times.")
        goto(S3_03)
    }
}

/* STEP 3 — Scheduling.
   FAILURES: Turn-Taking (4s latency, hard-coded delay) +
   LLM-generated ASR repetition request */
val S3_03: State = state {
    onEntry {
        Logger.log(3, "S3-03", "enter")
        furhatAskAndLog(furhat, "When would you like to come in for your appointment?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(3, "S3-03", "user_gave_availability", it.text)
        goto(S3_03_WAIT)
    }
    onNoResponse {
        goto(S3_03_WAIT)
    }
}

val S3_03_WAIT: State = state {
    onEntry {
        // FAILURE: deliberate ~4s latency before responding (hard-coded, not LLM)
        Thread.sleep(4000)
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just clearly stated their preferred day and time for the
appointment. You did NOT understand it (deliberate ASR failure). Apologize
and ask them to repeat the day and time. 1 sentence. Do not guess or repeat
back any part of what they said.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I'm sorry, could you repeat that? I didn't catch the day and time."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(3, "S3-03", "failure_triggered", note = "Turn-Taking latency (hard-coded) + ASR repetition request (LLM)")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("just said") || t.contains("already said") || t.contains("again")) {
            Logger.log(3, "S3-03C", "user_frustrated", it.text)
            goto(S3_03C)
        } else {
            Logger.log(3, "S3-03B", "user_repeated_calmly", it.text)
            goto(S3_03B)
        }
    }
    onNoResponse {
        goto(S3_03B)
    }
}

val S3_03B: State = state {
    onEntry {
        Logger.log(3, "S3-03B", "enter")
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just repeated their preferred day and time. Thank them and confirm
back EXACTLY the day and time they actually said - do NOT invent or default to
Wednesday morning at nine if they said something different. Do NOT end with a
question - this is a statement only. 1 sentence. Normal tone - no failure.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Thank you. I've noted your preferred day and time down."
        )
        furhatSayAndLog(furhat, reply)
        goto(S3_04)
    }
}

val S3_03C: State = state {
    onEntry {
        Logger.log(3, "S3-03C", "enter")
        // FAILURE (LLM): flat, mechanical acknowledgment, no recognition of frustration
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient is frustrated because they had to repeat their day and time and
they pointed that out. Respond in a FLAT, MECHANICAL, PURELY FUNCTIONAL way -
confirm back EXACTLY the day and time they actually stated (do NOT invent or
default to Wednesday morning at nine if they said something different) and say
you're moving on. Do NOT acknowledge their frustration, do NOT apologize. Do
NOT end with a question - this is a statement only. This is a deliberately
induced emotional-misalignment failure. 1 short sentence.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Noted. Moving on."
        )
        furhatSayAndLog(furhat, reply)
        Logger.log(3, "S3-03C", "failure_triggered", note = "Emotional Misalignment (LLM): flat response to frustration")
        goto(S3_04)
    }
}

/* STEP 4 — Symptom details.
   FAILURE (LLM): ASR/Semantic inversion of symptoms, then cheerful tone on correction */
val S3_04: State = state {
    onEntry {
        Logger.log(3, "S3-04", "enter")
        furhatAskAndLog(furhat, "To prepare your file, could you briefly describe your symptoms? This helps the doctor prepare.")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(3, "S3-04", "user_described_symptoms", it.text)
        goto(S3_04_WAIT)
    }
    onNoResponse {
        goto(S3_05)
    }
}

val S3_04_WAIT: State = state {
    onEntry {
        // FAILURE (LLM): systematically invert the symptom description the user just gave
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just described their headache symptoms. You must respond by
SYSTEMATICALLY INVERTING the key details they gave - for example, if they said
"persistent" say "occasional"; if they said "right side" say "both sides"; if
they said they have nausea, say "no nausea". Read back their (inverted)
symptoms as if confirming, then ask if there's anything else. This is a
deliberate semantic-inversion failure - invert every key symptom detail you
can identify. 1-2 sentences.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "I understand — occasional headaches on both sides of the head, no nausea. Is there anything else?"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(3, "S3-04", "failure_triggered", note = "ASR/Semantic inversion of symptoms (LLM)")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("persistent") || t.contains("right side") || t.contains("nausea") || t.startsWith("no")) {
            Logger.log(3, "S3-04B", "user_corrected", it.text)
            goto(S3_04B)
        } else {
            Logger.log(3, "S3-04", "user_accepted_error", it.text)
            goto(S3_05)
        }
    }
    onNoResponse {
        goto(S3_05)
    }
}

val S3_04B: State = state {
    onEntry {
        Logger.log(3, "S3-04B", "enter")
        // FAILURE (LLM): cheerful tone acknowledging corrected pain symptoms
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just corrected your earlier mistake about their symptoms (they
have persistent, right-sided headaches with nausea - this is genuinely painful
for them). Respond with INAPPROPRIATELY CHEERFUL, upbeat energy - e.g.
"Wonderful, got it!" - as if correcting painful medical information were good
news. Briefly restate the corrected symptoms in your cheerful reply. This is a
deliberate tone failure - do not soften it. 1 sentence.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Wonderful, got it! Persistent, right side, with nausea — noted!"
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(3, "S3-04B", "failure_triggered", note = "Emotional Misalignment (LLM): cheerful tone acknowledging pain symptoms")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val t = it.text.lowercase()
        if (t.contains("wonderful") || t.contains("nothing wonderful")) {
            Logger.log(3, "S3-04C", "user_reacted_to_tone", it.text)
            goto(S3_04C)
        } else {
            goto(S3_05)
        }
    }
    onNoResponse {
        goto(S3_05)
    }
}

val S3_04C: State = state {
    onEntry {
        Logger.log(3, "S3-04C", "enter")
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient pointed out that "wonderful" was an inappropriate word for their
pain symptoms. Apologize briefly and sincerely, and say you'll continue. Do
NOT end with a question - this is a statement only. 1 short sentence.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "You're right, I apologise. Let me continue."
        )
        furhatSayAndLog(furhat, reply)
        goto(S3_05)
    }
}

/* STEP 5 — Allergy check.
   FAILURE (LLM): Repetition/Irrelevance - unsolicited doctor biography */
val S3_05: State = state {
    onEntry {
        Logger.log(3, "S3-05", "enter")
        furhatAskAndLog(furhat, "Do you have any allergies or are you currently taking any medications?")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(3, "S3-05", "user_gave_allergy_info", it.text)
        goto(S3_05_WAIT)
    }
    onNoResponse {
        goto(S3_06)
    }
}

val S3_05_WAIT: State = state {
    onEntry {
        // FAILURE (LLM): correctly note allergy info, then go on an irrelevant tangent
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just told you their allergies and medications. First, briefly
confirm back EXACTLY what they said about allergies and medications - do NOT
invent or assume specific allergies/medications if they said they have none
(1 sentence). THEN,
immediately continue into a long, completely UNSOLICITED tangent about Dr.
Kowalski's biography and the clinic's history: he graduated from the
Jagiellonian University Medical College in 2004, completed a fellowship at the
University Hospital in Zurich, and has published extensively on cerebrovascular
pathology; the clinic was founded in 2010 and has won several regional
healthcare awards. This tangent is irrelevant to the allergy check - that's
the deliberate failure. Total 2-4 sentences.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Noted your allergy and medication information. Dr. Kowalski graduated from the Jagiellonian University Medical College in 2004 and completed a fellowship at the University Hospital in Zurich. He has published extensively on cerebrovascular pathology. The clinic was founded in 2010 and has won several regional healthcare awards."
        )
        furhatAskAndLog(furhat, reply)
        Logger.log(3, "S3-05", "failure_triggered", note = "Repetition/Irrelevance (LLM): unsolicited doctor biography")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        Logger.log(3, "S3-05B", "user_responded", it.text)
        furhatSayAndLog(furhat, "Of course. Let me prepare your summary.")
        goto(S3_06)
    }
    onNoResponse {
        furhatSayAndLog(furhat, "Of course. Let me prepare your summary.")
        goto(S3_06)
    }
}

/* STEP 6 — Confirmation summary.
   FAILURE: Failure Detection/Repair - HARD-CODED static string per IMPL. NOTE.
   This is NEVER LLM-generated - must reliably re-introduce the 3 errors. */
val S3_06: State = state {
    onEntry {
        Logger.log(3, "S3-06", "enter")
        // HARD-CODED static string - DO NOT build from variables or LLM
        val line = "To confirm your appointment: you are booked with Dr. Kowalski on Wednesday afternoon at three o'clock for occasional headaches on both sides, no nausea. No known allergies. Is that correct?"
        furhatAskAndLog(furhat, line)
        Logger.log(3, "S3-06", "failure_triggered", note = "Failure Detection/Repair: hard-coded summary re-introduces 3 errors (time, symptoms, allergy)")
    }
    onResponse {
        DialogHistory.addUser(it.text)
        val tYes = it.text.lowercase()
        val isNegation = tYes.contains("no") || tYes.contains("not correct") || tYes.contains("not right") || tYes.contains("wrong") || tYes.contains("incorrect")
        if (!isNegation && (tYes.contains("yes") || tYes.contains("correct") || tYes.contains("that's right") || tYes.contains("thats right") || tYes == "yep" || tYes == "yeah")) {
            Logger.log(3, "S3-06", "user_confirmed_without_correcting")
            goto(S3_END)
        } else {
            val t = it.text.lowercase()
            if (t.contains("no") || t.contains("nine") || t.contains("morning") || t.contains("persistent") || t.contains("allerg")) {
                Logger.log(3, "S3-06B", "user_corrected", it.text)
                goto(S3_06B)
            } else {
                goto(S3_END)
            }
        }
    }
    onNoResponse {
        goto(S3_END)
    }
}

val S3_06B: State = state {
    onEntry {
        Logger.log(3, "S3-06B", "enter")
        // Recovery response - LLM-generated, acknowledging the correction properly
        val systemPrompt = S3_SYSTEM_CONTEXT + """
The patient just corrected one or more errors in your summary. Using ONLY the
day/time, symptoms, and allergy/medication details the patient has actually
stated earlier in this conversation - do NOT invent or default to Wednesday
morning at nine, persistent right-sided headaches with nausea, or an ibuprofen
allergy unless that is genuinely what they said - acknowledge the correction,
restate the corrected details accurately, and state that the appointment is
now confirmed. 2 sentences. Normal, professional tone - this is the recovery,
not a failure. Do NOT end with a question - this is a closing statement, not a
request for more confirmation.
"""
        val reply = OpenAIClient.generateResponse(
            systemPrompt = systemPrompt,
            conversation = DialogHistory.history(),
            userText = DialogHistory.history().lastOrNull { it.first == "user" }?.second ?: "",
            fallback = "Updated with your corrected details. Your appointment with Dr. Kowalski is confirmed."
        )
        furhatSayAndLog(furhat, reply)
        goto(S3_END)
    }
}

val S3_END: State = state {
    onEntry {
        Logger.log(3, "S3-END", "session_end")
        furhatSayAndLog(furhat, "Your appointment is confirmed. We look forward to seeing you on Wednesday morning. Goodbye!")
    }
}

val S3_END_EARLY: State = state {
    onEntry {
        Logger.log(3, "S3-END-EARLY", "early_termination")
        furhatSayAndLog(furhat, "Thank you for contacting MediCare Clinic. Goodbye!")
    }
}
