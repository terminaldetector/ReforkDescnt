package com.drmd.lj2pdf

/**
 * Stand-in for the ML Kit / cloud translator: the harness exercises the
 * archiving path with translation off, so this only has to satisfy the calls.
 */
object Translator {
    data class Config(
        val on: Boolean, val target: String, val endpoint: String,
        val key: String, val engine: String
    ) {
        val active: Boolean get() = on
    }

    @Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER")
    suspend fun translate(cfg: Config, text: String): String = text
}

object Logx {
    val lines = ArrayList<String>()
    fun append(s: String) { lines.add(s) }
}
