package dev.phosphor.mobil3.settings.appearance

import dev.phosphor.mobil3.settings.SettingsWriteOwner

/** One-key transactions preserve the original raw preference, including absence and corrupt strings. */
internal class AppearancePreferences(
    private val owner: SettingsWriteOwner,
    val read: () -> Map<String, *>,
    private val commit: (String) -> Boolean,
    private val rollback: (Map<String, *>) -> Boolean,
) {
    fun save(document: AppearanceDocument): SettingsWriteOwner.Failure? {
        val encoded = AppearanceDocumentCodec.encode(document)
        return owner.write {
            val before = try { read().filterKeys { it == KEY }.toMap() } catch (_: Exception) {
                return@write SettingsWriteOwner.Failure("Reading appearance settings", true)
            }
            owner.commit({ commit(encoded) }, { true }, { rollback(before) })
        }
    }

    companion object { const val KEY = "appearance_state" }
}
