package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.RemoteHost

/**
 * The relay-host editor.
 *
 * Phosphor had no text input anywhere before this: every control was a key, a row, or a
 * drag. So the field is built from the same parts as the rest of the instrument (a
 * hairline box, mono type, the room's own palette) rather than importing Material's
 * rounded, filled TextField, which would read as a foreign object on the panel.
 *
 * Why this screen exists at all: the relay hosts used to be a build-time fact compiled
 * into the app, which meant the Play distribution shipped an empty list and no way to
 * fill it. The best feature in the app was unreachable for everyone who was not Ben.
 */

/** A single-line hairline field. Sharp corners, no fill, cursor in the room's accent. */
@Composable
fun HairlineField(
    value: String,
    hint: String,
    p: Palette,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onValueChange: (String) -> Unit,
    onImeAction: () -> Unit = {},
) {
    Box(
        modifier
            .height(Dim.flatKey)
            .border(Dim.hairline, p.line)
            .padding(horizontal = Dim.gap),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The hint is drawn beneath rather than as a decoration box, so an empty field
        // still shows the same glyph metrics as a filled one and nothing shifts on focus.
        if (value.isEmpty()) {
            Mono(hint, p.muted, Type.data)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = p.ink,
                fontSize = Type.data,
                fontFamily = MonoFace,
            ),
            cursorBrush = SolidColor(if (p.accent.alpha > 0f) p.accent else p.ink),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Uri,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Add or edit one relay endpoint.
 *
 * [existing] non-null means edit, which also exposes REMOVE. The caller owns persistence
 * and hands back a refusal string when the store rejects the values, so the same message
 * the store would give an agent is the message the human reads.
 */
@Composable
fun RemoteHostEditor(
    p: Palette,
    existing: RemoteHost?,
    refusal: String?,
    onSubmit: (label: String, host: String, port: String) -> Unit,
    onRemove: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
    var host by remember(existing) { mutableStateOf(existing?.host ?: "") }
    var port by remember(existing) {
        // 45777 is the relay's port. Prefilling it means the common case is two fields,
        // not three, and a user who has never read the protocol doc still succeeds.
        mutableStateOf(existing?.port?.toString() ?: "45777")
    }

    SheetSectionLabel(if (existing == null) "ADD RELAY" else "EDIT RELAY", p)

    HairlineField(
        value = label,
        hint = "label (e.g. studio pc)",
        p = p,
        modifier = Modifier.fillMaxWidth(),
        onValueChange = { label = it },
    )
    Spacer(Modifier.height(Dim.gap))

    HairlineField(
        value = host,
        hint = "host or tailnet name",
        p = p,
        modifier = Modifier.fillMaxWidth(),
        onValueChange = { host = it },
    )
    Spacer(Modifier.height(Dim.gap))

    HairlineField(
        value = port,
        hint = "port",
        p = p,
        modifier = Modifier.fillMaxWidth(),
        numeric = true,
        imeAction = ImeAction.Done,
        onValueChange = { port = it },
        onImeAction = { onSubmit(label, host, port) },
    )

    // A refusal is the store's own fix-bearing message. It appears under the fields it
    // concerns and stays until the input changes, so nothing is dismissed before it is read.
    refusal?.let {
        Spacer(Modifier.height(Dim.gap))
        Box(
            Modifier
                .fillMaxWidth()
                .border(Dim.hairline, p.accent)
                .background(p.surface2)
                .padding(Dim.gap),
        ) {
            Mono(it, p.ink, Type.data, maxLines = 6)
        }
    }

    Spacer(Modifier.height(Dim.gap))
    Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
        FlatKey("SAVE", p, active = true, modifier = Modifier.weight(1f)) {
            onSubmit(label, host, port)
        }
        FlatKey("CANCEL", p, modifier = Modifier.weight(1f), onClick = onCancel)
    }
    onRemove?.let {
        Spacer(Modifier.height(Dim.gap))
        FlatKey("REMOVE THIS RELAY", p, modifier = Modifier.fillMaxWidth(), onClick = it)
    }
    Spacer(Modifier.height(Dim.gapLg))
}

/**
 * What REMOTE says when no relay is saved.
 *
 * This is the first thing a Play user sees, so it has to be honest: Phosphor does not
 * host anything. It explains the one real precondition (a desktop running the relay,
 * reachable on your own network) without pretending a service exists.
 */
@Composable
fun RemoteEmptyState(p: Palette) {
    Box(
        Modifier
            .fillMaxWidth()
            .border(Dim.hairline, p.line)
            .padding(Dim.rowPad),
    ) {
        Mono(
            "No relay saved.\n\n" +
                "Phosphor can scope a desktop's audio over your own network. " +
                "Run phosphor-relay on that machine, reach it over a VPN such as " +
                "Tailscale or your LAN, then add its address below.\n\n" +
                "Nothing is hosted by Phosphor and no audio leaves your network.",
            p.muted,
            Type.data,
            maxLines = 12,
        )
    }
    Spacer(Modifier.height(Dim.gap))
}

@Composable
private fun SheetSectionLabel(text: String, p: Palette) {
    Mono(text, p.muted, Type.dataSm)
    Spacer(Modifier.height(Dim.gap))
}
