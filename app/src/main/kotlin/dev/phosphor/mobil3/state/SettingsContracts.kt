package dev.phosphor.mobil3.state

enum class SettingsGroup(val wireName: String) {
    SCOPE_AND_VIEW("scope_and_view"),
    LIGHT_GRID_AND_BEAM("light_grid_and_beam"),
    ROOMS_AND_THEME_PACKS("rooms_and_theme_packs"),
    SOURCES_AND_CAPTURE("sources_and_capture"),
    REMOTE_AND_NEXIDEX("remote_and_nexidex"),
    PIP_OVERLAY_AND_HUD("pip_overlay_and_hud"),
    MOTION_SOUND_AND_ACCESSIBILITY("motion_sound_and_accessibility"),
    PERMISSIONS_AND_FORTRESS("permissions_and_fortress"),
    TRIAL_PRO_DIAGNOSTICS_AND_ABOUT("trial_pro_diagnostics_and_about"),
}

data class SettingsRowSchema(
    val field: StateField,
    val group: SettingsGroup,
    val directAction: ActionType,
) {
    init {
        require(field in directAction.affectedFields) {
            "settings row action must affect its field"
        }
    }
}

data class SettingsRowProjection(
    val schema: SettingsRowSchema,
    val desired: StateValue,
    val effective: StateValue,
    val availability: Availability,
    val authority: Authority,
    val provenance: ProvenanceStamp? = null,
    val visibleLastWriter: ProvenanceStamp? = null,
    val fix: Fix? = null,
) {
    init {
        require(desired.kind == schema.field.valueKind && effective.kind == schema.field.valueKind) {
            "settings values must match ${schema.field.wireName}'s ${schema.field.valueKind} contract"
        }
        val expectedFix = availability.fix ?: authority.fix
        require(fix == expectedFix) {
            "settings repair must exactly project availability/authority for ${schema.field.wireName}"
        }
        if (provenance?.writer?.kind in setOf(PrincipalKind.NEXUS, PrincipalKind.SYSTEM)) {
            require(visibleLastWriter === provenance) {
                "Nexus/system settings provenance must be the exact visible last-writer stamp"
            }
        } else {
            require(visibleLastWriter == null || visibleLastWriter === provenance) {
                "visible last writer must never diverge from row provenance"
            }
        }
    }
}

object SettingsSchema {
    val rows: FrozenList<SettingsRowSchema> = FrozenList.copyOf(listOf(
        row(StateField.PLAYBACK_ACTIVE, SettingsGroup.SOURCES_AND_CAPTURE, ActionType.TOGGLE_PLAYBACK),
        row(StateField.POSITION_MILLIS, SettingsGroup.SOURCES_AND_CAPTURE, ActionType.SEEK),
        row(StateField.QUEUE_INDEX, SettingsGroup.SOURCES_AND_CAPTURE, ActionType.JUMP_TO_QUEUE),
        row(StateField.VOLUME_FRACTION, SettingsGroup.MOTION_SOUND_AND_ACCESSIBILITY, ActionType.SET_VOLUME),
        row(StateField.SOURCE_KIND, SettingsGroup.SOURCES_AND_CAPTURE, ActionType.REQUEST_OPEN_FILE),
        row(StateField.REMOTE_HOST_ID, SettingsGroup.REMOTE_AND_NEXIDEX, ActionType.START_REMOTE_HOST),
        row(StateField.REMOTE_AUDIO_ENABLED, SettingsGroup.REMOTE_AND_NEXIDEX, ActionType.SET_REMOTE_STREAMS),
        row(StateField.REMOTE_GEOMETRY_ENABLED, SettingsGroup.REMOTE_AND_NEXIDEX, ActionType.SET_REMOTE_STREAMS),
        row(StateField.SCOPE_MODE, SettingsGroup.SCOPE_AND_VIEW, ActionType.SET_SCOPE_MODE),
        row(StateField.BEAM_PRESET, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_BEAM_PRESET),
        row(StateField.FRAME_RATE, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_FRAME_RATE),
        row(StateField.OVERSAMPLE, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_OVERSAMPLE),
        row(StateField.ACTIVE_THEME_ID, SettingsGroup.ROOMS_AND_THEME_PACKS, ActionType.SET_ACTIVE_THEME),
        row(StateField.FOCUS, SettingsGroup.SCOPE_AND_VIEW, ActionType.SET_FOCUS),
        row(StateField.CUSTOM_BEAM, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_CUSTOM_BEAM),
        row(StateField.BEAM_CYCLE_SECONDS, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_BEAM_CYCLE),
        row(StateField.BEAM_CYCLE_PER_TRACK, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_BEAM_CYCLE),
        row(StateField.BEAM_ENERGY, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_BEAM_ENERGY),
        row(StateField.GLOW, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_GLOW),
        row(StateField.BEAM_RANDOM_GENERATION, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.RANDOMIZE_BEAM),
        row(StateField.BEAM_RANDOM_RANGE, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_BEAM_RANDOM_RANGE),
        row(StateField.GLOW_RANDOM_GENERATION, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.RANDOMIZE_GLOW),
        row(StateField.GLOW_RANDOM_RANGE, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_GLOW_RANDOM_RANGE),
        row(StateField.GEOMETRY_EFFECT, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_GEOMETRY_EFFECT),
        row(StateField.GEOMETRY_AMOUNT, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_GEOMETRY_AMOUNT),
        row(StateField.GRID_ENABLED, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_GRID),
        row(StateField.AUTO_GAIN_ENABLED, SettingsGroup.LIGHT_GRID_AND_BEAM, ActionType.SET_AUTO_GAIN),
        row(StateField.SCOPE_GAIN, SettingsGroup.SCOPE_AND_VIEW, ActionType.SET_SCOPE_GAIN),
        row(StateField.VIEW_LOCKED, SettingsGroup.SCOPE_AND_VIEW, ActionType.SET_VIEW_LOCK),
        row(StateField.ORBIT_YAW, SettingsGroup.SCOPE_AND_VIEW, ActionType.ORBIT_SCOPE),
        row(StateField.ORBIT_PITCH, SettingsGroup.SCOPE_AND_VIEW, ActionType.ORBIT_SCOPE),
        row(StateField.DOLLY, SettingsGroup.SCOPE_AND_VIEW, ActionType.DOLLY_SCOPE),
        row(StateField.DISPLAY_HUD, SettingsGroup.PIP_OVERLAY_AND_HUD, ActionType.SET_DISPLAY_HUD),
        row(StateField.FULLSCREEN, SettingsGroup.PIP_OVERLAY_AND_HUD, ActionType.SET_FULLSCREEN),
        row(StateField.SCOPE_ROTATION_LOCKED, SettingsGroup.PERMISSIONS_AND_FORTRESS, ActionType.SET_SCOPE_ROTATION_LOCK),
        row(StateField.UI_PLACEMENT_LOCKED, SettingsGroup.PERMISSIONS_AND_FORTRESS, ActionType.SET_UI_PLACEMENT_LOCK),
        row(StateField.REMOTE_LATENCY_MODE, SettingsGroup.REMOTE_AND_NEXIDEX, ActionType.SET_REMOTE_LATENCY_MODE),
        row(StateField.REMOTE_NETWORK_MODE, SettingsGroup.REMOTE_AND_NEXIDEX, ActionType.SET_REMOTE_NETWORK_MODE),
        row(
            StateField.BESTIARY_FOUND,
            SettingsGroup.TRIAL_PRO_DIAGNOSTICS_AND_ABOUT,
            ActionType.MARK_BESTIARY_FOUND,
        ),
        row(
            StateField.EPILEPSY_ACKNOWLEDGED,
            SettingsGroup.MOTION_SOUND_AND_ACCESSIBILITY,
            ActionType.CONFIRM_EPILEPSY_SAFETY,
        ),
    ))

    fun validate(): List<String> = buildList {
        rows.groupingBy { it.field }.eachCount().filterValues { it != 1 }.keys.forEach {
            add("duplicate settings row ${it.wireName}")
        }
        (StateField.entries.toSet() - rows.map { it.field }.toSet()).forEach {
            add("missing settings row ${it.wireName}")
        }
        (SettingsGroup.entries.toSet() - rows.map { it.group }.toSet()).forEach {
            add("empty settings group ${it.wireName}")
        }
    }

    private fun row(
        field: StateField,
        group: SettingsGroup,
        action: ActionType,
    ): SettingsRowSchema = SettingsRowSchema(field, group, action)
}
