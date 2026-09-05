package dev.phosphor.mobil3.ui

internal fun readRemoteListing(generation: () -> Int, listing: () -> String): Pair<Int, String>? {
    val before = generation()
    val json = listing()
    return if (before == generation()) before to json else null
}

/** One browse request owns only a later listing from its selected relay. */
internal class RemoteBrowseRequest(
    val root: String,
    val path: String,
    val peer: Pair<String, Int>,
    val baselineGeneration: Int,
) {
    private var active = true

    fun retire() { active = false }

    fun accepts(root: String, path: String, generation: Int, peer: Pair<String, Int>?): Boolean =
        active && this.root.isNotBlank() && this.root == root && this.path == path && this.peer == peer &&
            generation - baselineGeneration > 0

    fun selectRoot(
        root: String,
        currentRequest: RemoteBrowseRequest?,
        currentPeer: Pair<String, Int>?,
        browsing: Boolean,
        browse: (String, String) -> Unit,
    ) {
        if (active && browsing && currentRequest === this && currentPeer == peer &&
            root.isNotBlank() && root != this.root
        ) browse(root, "")
    }
}

/** Retained callbacks cannot act after another browse or host selection. */
internal class RemoteFolderAction(
    val root: String,
    val path: String,
    private val generation: Int,
    private val request: RemoteBrowseRequest,
    private val currentRequest: () -> RemoteBrowseRequest?,
    private val currentPeer: () -> Pair<String, Int>?,
    private val browse: (String, String) -> Unit,
    private val play: (String, String) -> Unit,
    private val dismiss: () -> Unit,
) {
    val accepted: Boolean
        get() = currentRequest() === request && request.accepts(root, path, generation, currentPeer())

    fun playFolder() = playPath(path)

    fun directory(name: String) {
        if (accepted && validName(name)) browse(root, child(name))
    }

    fun file(name: String) {
        if (validName(name)) playPath(child(name))
    }

    fun up() {
        if (accepted && path.isNotEmpty()) browse(root, path.substringBeforeLast('/', ""))
    }

    private fun playPath(target: String) {
        if (!accepted) return
        request.retire()
        play(root, target)
        dismiss()
    }

    private fun child(name: String) = if (path.isEmpty()) name else "$path/$name"

    private fun validName(name: String) = name.isNotEmpty() && !name.startsWith('.') &&
        name.none { it == '/' || it == '\u0000' }
}
