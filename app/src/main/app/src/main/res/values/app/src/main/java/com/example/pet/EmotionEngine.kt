package com.example.pet

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

enum class Mood { HAPPY, CALM, SAD, ANGRY, SHY }

enum class PetEvent { PET, DRAG, CHAT, PRAISE, SCOLD, IGNORE }

data class PetState(
    var mood: Mood = Mood.CALM,
    var energy: Int = 80,
    var affection: Int = 20,
    var trust: Int = 10,
    var attachment: Int = 10,
    var lastInteraction: Long = System.currentTimeMillis(),
    var lastTick: Long = System.currentTimeMillis()
)

class EmotionEngine(context: Context) {

    private val prefs = context.getSharedPreferences("pet_state", Context.MODE_PRIVATE)
    var state: PetState = load()
        private set

    fun handle(event: PetEvent): String {
        val now = System.currentTimeMillis()

        when (event) {
            PetEvent.PET -> {
                state.affection += 2
                state.trust += 1
                state.energy -= 1
                state.mood = if (state.affection > 60) Mood.SHY else Mood.HAPPY
            }
            PetEvent.DRAG -> {
                state.mood = Mood.ANGRY
                state.energy -= 2
            }
            PetEvent.CHAT -> {
                state.affection += 1
                state.mood = Mood.HAPPY
            }
            PetEvent.PRAISE -> {
                state.trust += 3
                state.affection += 2
                state.mood = Mood.HAPPY
            }
            PetEvent.SCOLD -> {
                state.trust -= 1
                state.mood = Mood.SAD
            }
            PetEvent.IGNORE -> {
                state.attachment += 1
                state.mood = Mood.SAD
            }
        }

        state.lastInteraction = now
        clamp()
        save()
        return lineFor(event)
    }

    fun tick() {
        val now = System.currentTimeMillis()
        if (now - state.lastTick < 60_000) return

        state.lastTick = now
        if (now - state.lastInteraction > 5 * 60_000) {
            if (state.energy < 100) state.energy += 1
            if (state.mood == Mood.HAPPY && state.affection < 30) {
                state.mood = Mood.CALM
            }
        }
        save()
    }

    fun maybeProactive(): String? {
        val now = System.currentTimeMillis()
        if (now - state.lastInteraction < 10 * 60_000) return null

        val chance = 20 + state.attachment / 2
        if (Random.nextInt(100) > chance) return null

        state.lastInteraction = now
        state.mood = if (state.attachment > 50) Mood.SHY else Mood.SAD
        save()

        return when {
            state.attachment > 50 -> "你终于看我了…"
            state.energy < 30 -> "有点没精神…"
            else -> "在忙吗？我等你很久了。"
        }
    }

    private fun lineFor(event: PetEvent): String {
        return when (state.mood) {
            Mood.HAPPY -> when (event) {
                PetEvent.PET -> "嘿嘿，再摸一下～"
                PetEvent.DRAG -> "放我下来啦！"
                else -> "今天心情不错！"
            }
            Mood.SHY -> "别、别一直摸…"
            Mood.SAD -> "呜…"
            Mood.ANGRY -> "哼，晕了！"
            Mood.CALM -> when (event) {
                PetEvent.PET -> "嗯？"
                PetEvent.CHAT -> "我在听。"
                else -> "我在这儿。"
            }
        }
    }

    private fun clamp() {
        state.energy = state.energy.coerceIn(0, 100)
        state.affection = state.affection.coerceIn(0, 100)
        state.trust = state.trust.coerceIn(0, 100)
        state.attachment = state.attachment.coerceIn(0, 100)
    }

    private fun save() {
        val json = JSONObject().apply {
            put("mood", state.mood.name)
            put("energy", state.energy)
            put("affection", state.affection)
            put("trust", state.trust)
            put("attachment", state.attachment)
            put("lastInteraction", state.lastInteraction)
            put("lastTick", state.lastTick)
        }.toString()

        prefs.edit().putString("state_json", json).apply()
    }

    private fun load(): PetState {
        val raw = prefs.getString("state_json", null) ?: return PetState()
        return try {
            val o = JSONObject(raw)
            PetState(
                mood = Mood.valueOf(o.optString("mood", Mood.CALM.name)),
                energy = o.optInt("energy", 80),
                affection = o.optInt("affection", 20),
                trust = o.optInt("trust", 10),
                attachment = o.optInt("attachment", 10),
                lastInteraction = o.optLong("lastInteraction", System.currentTimeMillis()),
                lastTick = o.optLong("lastTick", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            PetState()
        }
    }
}
