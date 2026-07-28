package dev.phosphor.mobil3.state

import dev.phosphor.mobil3.ui.ScopeActions
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

private data class HarnessResult(
    val acknowledgement: ActionAcknowledgement? = null,
    val acceptance: AcceptedActionRecord? = null,
    val refusal: Refusal? = null,
    val state: PhosphorStateSnapshot,
    val audit: List<AuditRecord>,
)

/** Test oracle only. Phase 03 production code has no store, reducer, dispatcher, or effects. */
private class ReferenceHarness(
    initialState: PhosphorStateSnapshot,
    private val auditLimit: Int = 8,
    private val idempotencyLimit: Int = 8,
    initialReceipt: Long = 1L,
    initialOrdinal: Long = 1L,
) {
    var state: PhosphorStateSnapshot = initialState
        private set

    private var receipt = initialReceipt
    private var ordinal = initialOrdinal
    private var lastAcceptedMonotonicMillis = 0L
    private var lastAcceptedWallTimeMillis = initialState.wallTimeMillis
    private var currentDispatchWallTimeMillis = initialState.wallTimeMillis
    private val audit = ArrayDeque<AuditRecord>()
    private val idempotency = linkedMapOf<Pair<PrincipalId, String>, IdempotencyRecord>()

    val auditSize: Int get() = audit.size
    val idempotencySize: Int get() = idempotency.size

    fun dispatch(
        action: PhosphorAction,
        request: ActionRequest,
        monotonicMillis: Long,
        wallTimeMillis: Long,
    ): HarnessResult {
        require(monotonicMillis >= 0L) { "test monotonic time must not be negative" }
        require(wallTimeMillis >= 0L) { "test wall time must not be negative" }
        require(monotonicMillis >= lastAcceptedMonotonicMillis) {
            "test monotonic time must not move behind the last accepted action"
        }
        require(wallTimeMillis >= lastAcceptedWallTimeMillis) {
            "test wall time must not move behind the last accepted action"
        }
        currentDispatchWallTimeMillis = wallTimeMillis
        evictExpired(wallTimeMillis)
        val key = request.principal to request.idempotencyKey
        val payload = action.canonicalPayload()

        idempotency[key]?.let { previous ->
            if (previous.canonicalPayload == payload) {
                append(
                    AuditRecord(
                        receiptId = previous.acknowledgement.receiptId,
                        kind = AuditKind.IDEMPOTENT_REPLAY,
                        wallTimeMillis = wallTimeMillis,
                        actionType = action.type,
                        fields = action.type.affectedFields,
                        provenance = previous.acknowledgement.provenance,
                    ),
                )
                return result(previous.acknowledgement)
            }
            return refuse(
                action,
                Refusal(
                    code = RefusalCode.IDEMPOTENCY_CONFLICT,
                    fix = "Generate a new key or replay the original payload.",
                    currentRevision = state.revision,
                    originalReceiptId = previous.acknowledgement.receiptId,
                ),
                AuditKind.IDEMPOTENCY_CONFLICT,
            )
        }

        action.validate()?.let { return refuse(action, it.copy(currentRevision = state.revision)) }
        if (action.type.principalPolicy == PrincipalPolicy.HUMAN_ONLY &&
            request.principal.kind != PrincipalKind.HUMAN
        ) {
            return refuse(
                action,
                Refusal(
                    RefusalCode.PERMISSION_REQUIRES_HUMAN,
                    "Open the human safety-confirmation flow.",
                    state.revision,
                ),
            )
        }
        if (request.requestedCapability != action.type.requiredCapability) {
            return refuse(
                action,
                Refusal(
                    RefusalCode.INVALID_REQUEST,
                    "Request ${action.type.requiredCapability.wireName} for ${action.type.wireName}.",
                    state.revision,
                ),
            )
        }
        if (request.expectedRevision != state.revision) {
            return refuse(
                action,
                Refusal(
                    RefusalCode.REVISION_CONFLICT,
                    "Refresh state and retry against revision ${state.revision}.",
                    state.revision,
                ),
            )
        }
        if (request.principal.kind == PrincipalKind.NEXUS && !state.liveness.canDrive(request.sessionId)) {
            return refuse(
                action,
                Refusal(
                    RefusalCode.SESSION_UNAVAILABLE,
                    "Re-establish a driving session before mutating state.",
                    state.revision,
                ),
            )
        }
        if (action.type.requiredCapability !in state.capabilities[request.principal].orEmpty()) {
            return refuse(
                action,
                Refusal(
                    RefusalCode.CAPABILITY_NOT_GRANTED,
                    "Ask the user to grant ${action.type.requiredCapability.wireName}.",
                    state.revision,
                ),
            )
        }

        action.type.affectedFields.forEach { field ->
            state.availability.getValue(field).takeIf { it.state != AvailabilityState.AVAILABLE }?.let {
                return refuse(action, Refusal(codeFor(it.state), it.fix!!.message, state.revision))
            }
            state.authority.getValue(field).takeIf { it.state != AuthorityState.WRITABLE }?.let {
                return refuse(action, Refusal(codeFor(it.state), it.fix!!.message, state.revision))
            }
        }

        if (idempotency.size >= idempotencyLimit) {
            return refuse(
                action,
                Refusal(
                    RefusalCode.IDEMPOTENCY_INDEX_FULL,
                    "Wait for the oldest protected idempotency key to expire, then retry.",
                    state.revision,
                ),
            )
        }
        val expiresWallTimeMillis = Math.addExact(wallTimeMillis, IDEMPOTENCY_TTL_MILLIS)
        val receiptNumber = receipt
        val nextReceiptNumber = Math.addExact(receipt, 1L)
        val nextOrdinal = Math.addExact(ordinal, 1L)

        val field = singleReferenceField(action)
        val oldValue = value(field)
        val newValue = requestedValue(action)
        val stamp = ProvenanceStamp(
            receiptId = "r$receiptNumber",
            writer = request.principal,
            reason = request.reason,
            transport = request.transport,
            sessionId = request.sessionId,
            monotonicMillis = monotonicMillis,
            wallTimeMillis = wallTimeMillis,
        )

        if (oldValue == newValue) {
            val acknowledgement = ActionAcknowledgement(
                receiptId = stamp.receiptId,
                actionType = action.type,
                changed = false,
                revision = state.revision,
                sequence = state.sequence,
                effectiveValue = oldValue,
                provenance = stamp,
                delta = null,
                effects = frozenListOf(),
            )
            val auditRecord = AuditRecord(
                receiptId = stamp.receiptId,
                kind = AuditKind.NO_OP,
                wallTimeMillis = stamp.wallTimeMillis,
                actionType = action.type,
                fields = frozenSetOf(field),
                provenance = stamp,
            )
            val acceptance = AcceptedActionRecord(
                request = request,
                action = action,
                canonicalPayload = payload,
                acknowledgement = acknowledgement,
                previousSnapshot = state,
                snapshot = state,
                audit = auditRecord,
                settingsProjections = frozenListOf(),
            )
            val idempotencyRecord = IdempotencyRecord(
                principal = request.principal,
                key = request.idempotencyKey,
                action = action,
                canonicalPayload = payload,
                acknowledgement = acknowledgement,
                acceptedWallTimeMillis = wallTimeMillis,
                expiresWallTimeMillis = expiresWallTimeMillis,
                ordinal = ordinal,
            )
            receipt = nextReceiptNumber
            ordinal = nextOrdinal
            lastAcceptedMonotonicMillis = monotonicMillis
            lastAcceptedWallTimeMillis = wallTimeMillis
            append(auditRecord)
            idempotency[key] = idempotencyRecord
            return result(acknowledgement, acceptance)
        }

        val contested = audit.lastOrNull { record ->
            val prior = record.provenance
            field in record.fields &&
                prior != null &&
                prior.writer != request.principal &&
                wallTimeMillis >= prior.wallTimeMillis &&
                wallTimeMillis - prior.wallTimeMillis <= 3_000L
        } != null
        val revision = Math.addExact(state.revision, 1L)
        val sequence = Math.addExact(state.sequence, 1L)
        val change = StateChange(field, oldValue, newValue, stamp)
        val delta = StateDelta(
            revision = revision,
            sequence = sequence,
            changes = frozenListOf(change),
            provenance = stamp,
        )
        val effects = frozenListOf(
            InertEffectDescription(EffectKind.PERSIST_STATE, "persist accepted state", stamp),
            InertEffectDescription(EffectKind.HUD_PROVENANCE, "show accepted writer", stamp),
        )
        val nextState = mutate(field, newValue, revision, sequence, wallTimeMillis, stamp, contested)
        val acknowledgement = ActionAcknowledgement(
            receiptId = stamp.receiptId,
            actionType = action.type,
            changed = true,
            revision = revision,
            sequence = sequence,
            effectiveValue = newValue,
            provenance = stamp,
            delta = delta,
            effects = effects,
        )
        val auditRecord = AuditRecord(
            receiptId = stamp.receiptId,
            kind = AuditKind.ACTION,
            wallTimeMillis = stamp.wallTimeMillis,
            actionType = action.type,
            fields = frozenSetOf(field),
            provenance = stamp,
        )
        val projection = SettingsRowProjection(
            schema = SettingsSchema.rows.single { it.field == field },
            desired = newValue,
            effective = newValue,
            availability = nextState.availability.getValue(field),
            authority = nextState.authority.getValue(field),
            provenance = stamp,
            visibleLastWriter = stamp,
            fix = nextState.fixes[field],
        )
        val acceptance = AcceptedActionRecord(
            request = request,
            action = action,
            canonicalPayload = payload,
            acknowledgement = acknowledgement,
            previousSnapshot = state,
            snapshot = nextState,
            audit = auditRecord,
            settingsProjections = frozenListOf(projection),
        )
        val idempotencyRecord = IdempotencyRecord(
            principal = request.principal,
            key = request.idempotencyKey,
            action = action,
            canonicalPayload = payload,
            acknowledgement = acknowledgement,
            acceptedWallTimeMillis = wallTimeMillis,
            expiresWallTimeMillis = expiresWallTimeMillis,
            ordinal = ordinal,
        )
        receipt = nextReceiptNumber
        ordinal = nextOrdinal
        lastAcceptedMonotonicMillis = monotonicMillis
        lastAcceptedWallTimeMillis = wallTimeMillis
        state = nextState
        append(auditRecord)
        idempotency[key] = idempotencyRecord
        return result(acknowledgement, acceptance)
    }

    private fun singleReferenceField(action: PhosphorAction): StateField = when (action) {
        is SetDisplayHud -> StateField.DISPLAY_HUD
        is SetActiveThemeId -> StateField.ACTIVE_THEME_ID
        is SetScopeGain -> StateField.SCOPE_GAIN
        is SetGridEnabled -> StateField.GRID_ENABLED
        is SetViewLock -> StateField.VIEW_LOCKED
        ConfirmEpilepsySafety -> StateField.EPILEPSY_ACKNOWLEDGED
        else -> error("ReferenceHarness intentionally supports only Phase 03 proof slices")
    }

    private fun value(field: StateField): StateValue = when (field) {
        StateField.DISPLAY_HUD -> StringStateValue(state.desired.displayHud)
        StateField.ACTIVE_THEME_ID -> StringStateValue(state.desired.activeThemeId)
        StateField.SCOPE_GAIN -> FloatStateValue(state.desired.scopeGain)
        StateField.GRID_ENABLED -> BooleanStateValue(state.desired.gridEnabled)
        StateField.VIEW_LOCKED -> BooleanStateValue(state.desired.viewLocked)
        StateField.EPILEPSY_ACKNOWLEDGED -> BooleanStateValue(state.desired.epilepsyAcknowledged)
        else -> error("unsupported reference field $field")
    }

    private fun requestedValue(action: PhosphorAction): StateValue = when (action) {
        is SetDisplayHud -> StringStateValue(action.mode)
        is SetActiveThemeId -> StringStateValue(action.themeId)
        is SetScopeGain -> FloatStateValue(action.gain)
        is SetGridEnabled -> BooleanStateValue(action.enabled)
        is SetViewLock -> BooleanStateValue(action.locked)
        ConfirmEpilepsySafety -> BooleanStateValue(true)
        else -> error("unsupported reference action ${action.type}")
    }

    private fun mutate(
        field: StateField,
        newValue: StateValue,
        revision: Long,
        sequence: Long,
        wallTimeMillis: Long,
        stamp: ProvenanceStamp,
        contested: Boolean,
    ): PhosphorStateSnapshot {
        val desired = updateSettings(state.desired, field, newValue)
        val effective = updateSettings(state.effective, field, newValue)
        return state.copy(
            revision = revision,
            sequence = sequence,
            wallTimeMillis = wallTimeMillis,
            desired = desired,
            effective = effective,
            provenance = FrozenMap.copyOf(state.provenance + (field to stamp)),
            contestedFields = FrozenSet.copyOf(
                if (contested) state.contestedFields + field else state.contestedFields - field,
            ),
        )
    }

    private fun updateSettings(
        settings: InstrumentSettings,
        field: StateField,
        newValue: StateValue,
    ): InstrumentSettings =
        when (field) {
            StateField.DISPLAY_HUD -> settings.copy(displayHud = (newValue as StringStateValue).value)
            StateField.ACTIVE_THEME_ID -> settings.copy(activeThemeId = (newValue as StringStateValue).value)
            StateField.SCOPE_GAIN -> settings.copy(scopeGain = (newValue as FloatStateValue).value)
            StateField.GRID_ENABLED -> settings.copy(gridEnabled = (newValue as BooleanStateValue).value)
            StateField.VIEW_LOCKED -> settings.copy(viewLocked = (newValue as BooleanStateValue).value)
            StateField.EPILEPSY_ACKNOWLEDGED ->
                settings.copy(epilepsyAcknowledged = (newValue as BooleanStateValue).value)
            else -> error("unsupported reference field $field")
        }

    private fun append(record: AuditRecord) {
        audit.addLast(record)
        while (audit.size > auditLimit) audit.removeFirst()
    }

    private fun refuse(
        action: PhosphorAction,
        refusal: Refusal,
        auditKind: AuditKind = AuditKind.REFUSAL,
    ): HarnessResult {
        append(
            AuditRecord(
                receiptId = "refusal-${nextReceipt()}",
                kind = auditKind,
                wallTimeMillis = currentDispatchWallTimeMillis,
                actionType = action.type,
                fields = action.type.affectedFields,
                refusal = refusal,
            ),
        )
        return result(refusal = refusal)
    }

    private fun result(
        acknowledgement: ActionAcknowledgement? = null,
        acceptance: AcceptedActionRecord? = null,
        refusal: Refusal? = null,
    ): HarnessResult = HarnessResult(acknowledgement, acceptance, refusal, state, audit.toList())

    private fun evictExpired(nowWallTimeMillis: Long) {
        idempotency.filterValues { nowWallTimeMillis >= it.expiresWallTimeMillis }
            .keys
            .toList()
            .forEach(idempotency::remove)
    }

    private fun nextReceipt(): Long = receipt.also { receipt = Math.addExact(receipt, 1L) }

    private fun codeFor(state: AvailabilityState): RefusalCode = when (state) {
        AvailabilityState.AVAILABLE -> error("available is not a refusal")
        AvailabilityState.SYSTEM_UNAVAILABLE -> RefusalCode.SYSTEM_UNAVAILABLE
        AvailabilityState.DISTRIBUTION_UNAVAILABLE -> RefusalCode.DISTRIBUTION_UNAVAILABLE
        AvailabilityState.AUTHORITY_UNAVAILABLE -> RefusalCode.AUTHORITY_UNAVAILABLE
        AvailabilityState.SESSION_UNAVAILABLE -> RefusalCode.SESSION_UNAVAILABLE
    }

    private fun codeFor(state: AuthorityState): RefusalCode = when (state) {
        AuthorityState.WRITABLE -> error("writable is not a refusal")
        AuthorityState.HUMAN_ONLY,
        AuthorityState.SYSTEM_BLOCKED,
        -> RefusalCode.AUTHORITY_UNAVAILABLE
        AuthorityState.DISTRIBUTION_BLOCKED -> RefusalCode.DISTRIBUTION_UNAVAILABLE
        AuthorityState.SESSION_BLOCKED -> RefusalCode.SESSION_UNAVAILABLE
    }
}

class StateCoreContractsTest {
    private val human = PrincipalId(PrincipalKind.HUMAN, "human")
    private val nexus = PrincipalId(PrincipalKind.NEXUS, "nexus")

    private fun live(vararg grants: Pair<PrincipalId, Set<Capability>>): PhosphorStateSnapshot =
        InitialSnapshots.fortress().copy(
            session = "s",
            liveness = SessionLiveness("s", SessionState.DRIVING),
            capabilities = FrozenMap.copyOf(
                grants.associate { (principal, capabilities) -> principal to FrozenSet.copyOf(capabilities) },
            ),
        )

    private fun request(
        principal: PrincipalId,
        action: PhosphorAction,
        revision: Long = 0L,
        key: String = "key",
        capability: Capability = action.type.requiredCapability,
    ): ActionRequest = ActionRequest(
        principal = principal,
        idempotencyKey = key,
        expectedRevision = revision,
        reason = "contract proof",
        requestedCapability = capability,
        transport = if (principal.kind == PrincipalKind.HUMAN) Transport.UI else Transport.BINDER,
        sessionId = if (principal.kind == PrincipalKind.NEXUS) "s" else null,
    )

    @Test
    fun snapshotCarriesEveryRequiredPlaneAndFieldContract() {
        val snapshot = InitialSnapshots.fortress(42L)
        assertEquals(PHOSPHOR_STATE_SCHEMA, snapshot.schema)
        assertEquals(Distribution.FORTRESS, snapshot.distribution)
        assertEquals(BuildProfile.FORTRESS_RELEASE, snapshot.buildProfile)
        assertEquals(StateField.entries.toSet(), snapshot.availability.keys)
        assertEquals(StateField.entries.toSet(), snapshot.authority.keys)
        assertEquals(CaptureTruthState.STOPPED, snapshot.capture.state)
        assertEquals(snapshot.desired, snapshot.effective)
        assertFailsWith<IllegalArgumentException> {
            snapshot.copy(buildProfile = BuildProfile.PLAY_RELEASE)
        }
        assertFailsWith<IllegalArgumentException> {
            snapshot.copy(session = "ghost")
        }
        assertFailsWith<IllegalArgumentException> {
            snapshot.copy(
                fixes = frozenMapOf(
                    StateField.DISPLAY_HUD to Fix(RefusalCode.AUTHORITY_UNAVAILABLE, "No blocker exists."),
                ),
            )
        }
    }

    @Test
    fun catalogCoversAllScopeActionsExactlyOnceWithTypedActionsOrExceptions() {
        val sourceMethods = currentScopeActionMethods()
        assertEquals(EXPECTED_SCOPE_ACTION_METHODS, sourceMethods)
        assertEquals(emptyList(), StateActionCatalog.validate(sourceMethods))
        assertEquals(EXPECTED_SCOPE_ACTION_METHODS, StateActionCatalog.entries.map { it.scopeActionMethod }.toSet())
        assertEquals(ActionType.entries.toSet(), ALL_ACTION_SAMPLES.map { it.type }.toSet())
        assertEquals(ActionType.entries.size, ALL_ACTION_SAMPLES.size)
        assertTrue(StateActionCatalog.entries.filterIsInstance<NonAgentExceptionBinding>().all { it.reason.isNotBlank() })
        assertTrue(ActionType.entries.filter { it.opensHumanFlow }.all { it.requiredCapability in setOf(
            Capability.CONTROL_SETTINGS,
            Capability.CONTROL_TRANSPORT,
            Capability.REQUEST_PERMISSIONS,
        ) })
    }

    @Test
    fun frozenCollectionsDefensivelyCopyAllMutableInputs() {
        val white = RgbaColor(UByte.MAX_VALUE, UByte.MAX_VALUE, UByte.MAX_VALUE)
        val sourceColors = mutableListOf(white)
        val gradient = BeamGradient(sourceColors, 1)
        sourceColors.clear()
        assertEquals(listOf(white), gradient.colors)

        val sourceCapabilities = linkedMapOf(human to frozenSetOf(Capability.CONTROL_SCOPE))
        val frozenCapabilities = FrozenMap.copyOf(sourceCapabilities)
        sourceCapabilities.clear()
        assertEquals(frozenSetOf(Capability.CONTROL_SCOPE), frozenCapabilities.getValue(human))

        assertEquals(listOf(white), gradient.colors)
    }

    @Test
    fun settingsSchemaCoversEveryFieldAndNineIntentionGroupsWithVisibleWriterTruth() {
        assertEquals(emptyList(), SettingsSchema.validate())
        assertEquals(StateField.entries.toSet(), SettingsSchema.rows.map { it.field }.toSet())
        assertEquals(SettingsGroup.entries.toSet(), SettingsSchema.rows.map { it.group }.toSet())

        val stamp = ProvenanceStamp("settings", nexus, "agent update", Transport.BINDER, "s", 1L, 1L)
        val schema = SettingsSchema.rows.first { it.field == StateField.DISPLAY_HUD }
        val projection = SettingsRowProjection(
            schema = schema,
            desired = StringStateValue("on"),
            effective = StringStateValue("on"),
            availability = Availability(AvailabilityState.AVAILABLE),
            authority = Authority(AuthorityState.WRITABLE),
            provenance = stamp,
            visibleLastWriter = stamp,
        )
        assertSame(stamp, projection.visibleLastWriter)
        assertFailsWith<IllegalArgumentException> {
            projection.copy(visibleLastWriter = stamp.copy())
        }
        assertFailsWith<IllegalArgumentException> {
            projection.copy(
                authority = Authority(
                    AuthorityState.SYSTEM_BLOCKED,
                    Fix(RefusalCode.AUTHORITY_UNAVAILABLE, "Open the controlling system setting."),
                ),
                fix = null,
            )
        }
        val systemFix = Fix(RefusalCode.AUTHORITY_UNAVAILABLE, "Open the controlling system setting.")
        val blockedProjection = projection.copy(
            authority = Authority(AuthorityState.SYSTEM_BLOCKED, systemFix),
            fix = systemFix,
        )
        assertEquals(systemFix, blockedProjection.fix)
        assertFailsWith<IllegalArgumentException> {
            blockedProjection.copy(fix = Fix(RefusalCode.INVALID_REQUEST, "Wrong repair."))
        }
        assertFailsWith<IllegalArgumentException> {
            projection.copy(desired = BooleanStateValue(true))
        }
    }

    @Test
    fun safetyConfirmationIsTypedHumanOnlyAndCannotBeForgedByNexus() {
        val action = ConfirmEpilepsySafety
        assertEquals(PrincipalPolicy.HUMAN_ONLY, action.type.principalPolicy)
        val harness = ReferenceHarness(
            live(
                nexus to setOf(Capability.CONTROL_SETTINGS),
                human to setOf(Capability.CONTROL_SETTINGS),
            ),
        )
        assertRefusal(
            RefusalCode.PERMISSION_REQUIRES_HUMAN,
            harness.dispatch(action, request(nexus, action, key = "nexus-safety"), 1L, 1L),
        )
        val accepted = harness.dispatch(action, request(human, action, key = "human-safety"), 2L, 2L)
        assertTrue(accepted.acknowledgement!!.changed)
        assertTrue(accepted.state.effective.epilepsyAcknowledged)
        assertEquals(PrincipalKind.HUMAN, accepted.acknowledgement.provenance.writer.kind)
    }

    @Test
    fun humanFlowRequestsHaveAProvenanceBoundEffectOnlyAcceptance() {
        val action = RequestSettingsExport
        val request = request(nexus, action)
        val snapshot = live(nexus to setOf(Capability.CONTROL_SETTINGS)).copy(wallTimeMillis = 5L)
        val stamp = ProvenanceStamp("flow", nexus, request.reason, Transport.BINDER, "s", 5L, 5L)
        val effect = InertEffectDescription(
            EffectKind.OPEN_HUMAN_FLOW,
            "ask the human to choose an export destination",
            stamp,
        )
        val acknowledgement = ActionAcknowledgement(
            receiptId = stamp.receiptId,
            actionType = action.type,
            changed = false,
            revision = snapshot.revision,
            sequence = snapshot.sequence,
            effectiveValue = null,
            provenance = stamp,
            delta = null,
            effects = frozenListOf(effect),
        )
        val audit = AuditRecord(
            receiptId = stamp.receiptId,
            kind = AuditKind.ACTION,
            wallTimeMillis = stamp.wallTimeMillis,
            actionType = action.type,
            fields = action.type.affectedFields,
            provenance = stamp,
        )
        val accepted = AcceptedActionRecord(
            request = request,
            action = action,
            canonicalPayload = action.canonicalPayload(),
            acknowledgement = acknowledgement,
            previousSnapshot = snapshot,
            snapshot = snapshot,
            audit = audit,
            settingsProjections = frozenListOf(),
        )
        assertEquals(EffectKind.OPEN_HUMAN_FLOW, accepted.acknowledgement.effects.single().kind)
        assertFailsWith<IllegalArgumentException> {
            acknowledgement.copy(effects = frozenListOf())
        }
        assertFailsWith<IllegalArgumentException> {
            acknowledgement.copy(
                effects = frozenListOf(
                    InertEffectDescription(EffectKind.PERSIST_STATE, "wrong effect", stamp),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            accepted.copy(audit = audit.copy(kind = AuditKind.NO_OP))
        }
    }

    @Test
    fun canonicalPayloadsAreStructuralSortedAndFloatBitExact() {
        val host = StartRemoteHost(label = "living-room", host = "100.64.0.2", port = 4747)
        assertEquals(listOf("host", "label", "port"), host.canonicalPayload().arguments.map { it.first })
        assertEquals(
            CanonicalFloatBits((-0.0f).toBits()),
            SetScopeGain(-0.0f).canonicalPayload().arguments.single().second,
        )
        assertTrue(SetScopeGain(0.0f).canonicalPayload() != SetScopeGain(-0.0f).canonicalPayload())
        assertTrue(SetActiveThemeId("a|b").canonicalPayload() != SetActiveThemeId("a").canonicalPayload())
    }

    @Test
    fun representativeActionsHaveStableNamesCapabilitiesAndFixBearingValidation() {
        assertEquals("display.hud.set", SetDisplayHud("on").type.wireName)
        assertEquals(Capability.CONTROL_DISPLAY, SetDisplayHud("on").type.requiredCapability)
        assertNull(SetDisplayHud("auto").validate())
        assertEquals(RefusalCode.INVALID_VALUE, SetDisplayHud("sometimes").validate()?.code)
        assertEquals(RefusalCode.INVALID_VALUE, SetScopeGain(Float.NaN).validate()?.code)
        assertEquals(RefusalCode.INVALID_VALUE, SetActiveThemeId("Bad Theme").validate()?.code)
        assertTrue(ALL_ACTION_SAMPLES.mapNotNull { it.validate() }.all { it.fix.isNotBlank() })
    }

    @Test
    fun acceptedMutationReusesOneProvenanceInstanceAndReceiptEverywhere() {
        val harness = ReferenceHarness(live(human to setOf(Capability.CONTROL_DISPLAY)))
        val action = SetDisplayHud("on")
        val result = harness.dispatch(action, request(human, action), 10L, 100L)
        val acknowledgement = assertNotNull(result.acknowledgement)
        val stamp = acknowledgement.provenance
        assertTrue(acknowledgement.changed)
        assertEquals(1L, acknowledgement.revision)
        assertSame(stamp, acknowledgement.delta!!.provenance)
        assertSame(stamp, acknowledgement.delta.changes.single().provenance)
        assertSame(stamp, result.state.provenance[StateField.DISPLAY_HUD])
        assertSame(stamp, result.audit.single().provenance)
        assertEquals(stamp.receiptId, result.audit.single().receiptId)
        acknowledgement.effects.forEach { assertSame(stamp, it.provenance) }
        val acceptance = assertNotNull(result.acceptance)
        val copiedStamp = stamp.copy()
        assertFailsWith<IllegalArgumentException> {
            acceptance.copy(audit = acceptance.audit.copy(provenance = copiedStamp))
        }
        assertFailsWith<IllegalArgumentException> {
            acceptance.copy(
                snapshot = acceptance.snapshot.copy(
                    provenance = FrozenMap.copyOf(
                        acceptance.snapshot.provenance + (StateField.DISPLAY_HUD to copiedStamp),
                    ),
                ),
            )
        }
        val copiedProjection = acceptance.settingsProjections.single().copy(
            provenance = copiedStamp,
            visibleLastWriter = copiedStamp,
        )
        assertFailsWith<IllegalArgumentException> {
            acceptance.copy(settingsProjections = frozenListOf(copiedProjection))
        }
    }

    @Test
    fun nexusThemeSliceReusesTheAcceptedStampForStateDeltaHudPersistenceAckAndAudit() {
        val harness = ReferenceHarness(live(nexus to setOf(Capability.CONTROL_THEMES)))
        val action = SetActiveThemeId("crt-amber")
        val result = harness.dispatch(action, request(nexus, action), 20L, 200L)
        val acknowledgement = assertNotNull(result.acknowledgement)
        val stamp = acknowledgement.provenance
        assertEquals("crt-amber", result.state.effective.activeThemeId)
        assertSame(stamp, result.state.provenance[StateField.ACTIVE_THEME_ID])
        assertSame(stamp, acknowledgement.delta!!.provenance)
        assertSame(stamp, result.audit.single().provenance)
        assertTrue(acknowledgement.effects.any { it.kind == EffectKind.HUD_PROVENANCE })
        assertTrue(acknowledgement.effects.any { it.kind == EffectKind.PERSIST_STATE })
        acknowledgement.effects.forEach { assertSame(stamp, it.provenance) }
    }

    @Test
    fun noOpAndIdempotentReplayDoNotAdvanceRevisionOrRerunEffects() {
        val harness = ReferenceHarness(live(nexus to setOf(Capability.CONTROL_DISPLAY)))
        val action = SetDisplayHud("auto")
        val first = harness.dispatch(action, request(nexus, action, key = "same"), 10L, 100L)
            .acknowledgement!!
        val replayResult = harness.dispatch(action, request(nexus, action, key = "same"), 11L, 101L)
        val replay = replayResult.acknowledgement!!
        assertFalse(first.changed)
        assertEquals(0L, first.revision)
        assertTrue(first.effects.isEmpty())
        assertSame(first, replay)
        assertSame(first.provenance, replay.provenance)
        assertEquals(listOf(AuditKind.NO_OP, AuditKind.IDEMPOTENT_REPLAY), replayResult.audit.map { it.kind })
    }

    @Test
    fun staleCapabilityLivenessAvailabilityAuthorityAndIdempotencyConflictsRefuseBeforeEffects() {
        val action = SetDisplayHud("on")
        val liveState = live(nexus to setOf(Capability.CONTROL_DISPLAY))
        assertRefusal(
            RefusalCode.INVALID_REQUEST,
            ReferenceHarness(liveState).dispatch(
                action,
                request(nexus, action, capability = Capability.CONTROL_SCOPE),
                1L,
                1L,
            ),
        )
        assertRefusal(
            RefusalCode.REVISION_CONFLICT,
            ReferenceHarness(liveState).dispatch(action, request(nexus, action, revision = 9L), 1L, 1L),
        )
        assertRefusal(
            RefusalCode.CAPABILITY_NOT_GRANTED,
            ReferenceHarness(live(nexus to emptySet())).dispatch(action, request(nexus, action), 1L, 1L),
        )
        assertRefusal(
            RefusalCode.SESSION_UNAVAILABLE,
            ReferenceHarness(
                liveState.copy(liveness = SessionLiveness("s", SessionState.OBSERVING)),
            ).dispatch(action, request(nexus, action), 1L, 1L),
        )

        val unavailableFix = Fix(RefusalCode.DISTRIBUTION_UNAVAILABLE, "Install Fortress.")
        val unavailable = liveState.copy(
            availability = FrozenMap.copyOf(
                liveState.availability + (
                    StateField.DISPLAY_HUD to Availability(
                        AvailabilityState.DISTRIBUTION_UNAVAILABLE,
                        unavailableFix,
                    )
                ),
            ),
            fixes = frozenMapOf(StateField.DISPLAY_HUD to unavailableFix),
        )
        assertRefusal(
            RefusalCode.DISTRIBUTION_UNAVAILABLE,
            ReferenceHarness(unavailable).dispatch(action, request(nexus, action), 1L, 1L),
        )

        val blockedFix = Fix(RefusalCode.AUTHORITY_UNAVAILABLE, "Use the human control.")
        val blocked = liveState.copy(
            authority = FrozenMap.copyOf(
                liveState.authority + (
                    StateField.DISPLAY_HUD to Authority(
                        AuthorityState.HUMAN_ONLY,
                        blockedFix,
                    )
                ),
            ),
            fixes = frozenMapOf(StateField.DISPLAY_HUD to blockedFix),
        )
        assertRefusal(
            RefusalCode.AUTHORITY_UNAVAILABLE,
            ReferenceHarness(blocked).dispatch(action, request(nexus, action), 1L, 1L),
        )

        val harness = ReferenceHarness(liveState)
        val accepted = harness.dispatch(action, request(nexus, action, key = "idem"), 2L, 2L)
            .acknowledgement!!
        val conflictAction = SetDisplayHud("off")
        val conflict = harness.dispatch(
            conflictAction,
            request(nexus, conflictAction, revision = 1L, key = "idem"),
            3L,
            3L,
        ).refusal!!
        assertEquals(RefusalCode.IDEMPOTENCY_CONFLICT, conflict.code)
        assertEquals(accepted.receiptId, conflict.originalReceiptId)
        assertTrue(conflict.fix.isNotBlank())
    }

    @Test
    fun latestAcceptedRevisionWinsContentionAndBothActionsRemainInAudit() {
        val harness = ReferenceHarness(
            live(
                nexus to setOf(Capability.CONTROL_SCOPE),
                human to setOf(Capability.CONTROL_SCOPE),
            ),
        )
        val nexusAction = SetScopeGain(2.0f)
        harness.dispatch(nexusAction, request(nexus, nexusAction, key = "nexus"), 1L, 1L)
        val humanAction = SetScopeGain(3.0f)
        val second = harness.dispatch(
            humanAction,
            request(human, humanAction, revision = 1L, key = "human"),
            2L,
            2L,
        )
        assertEquals(3.0f, second.state.effective.scopeGain)
        assertEquals(2L, second.state.revision)
        assertTrue(StateField.SCOPE_GAIN in second.state.contestedFields)
        assertEquals(listOf(nexus, human), second.audit.map { it.provenance!!.writer })
    }

    @Test
    fun boundedIdempotencyIndexNeverEvictsAKeyBeforeTwentyFourHours() {
        val harness = ReferenceHarness(
            live(nexus to setOf(Capability.CONTROL_DISPLAY)),
            idempotencyLimit = 1,
        )
        val firstAction = SetDisplayHud("on")
        harness.dispatch(firstAction, request(nexus, firstAction, key = "protected"), 1L, 1L)

        val secondAction = SetDisplayHud("off")
        val beforeExpiry = harness.dispatch(
            secondAction,
            request(nexus, secondAction, revision = 1L, key = "new"),
            2L,
            IDEMPOTENCY_TTL_MILLIS,
        )
        assertRefusal(RefusalCode.IDEMPOTENCY_INDEX_FULL, beforeExpiry)
        assertEquals(1L, beforeExpiry.state.revision)
        assertEquals("on", beforeExpiry.state.effective.displayHud)

        val atExpiry = harness.dispatch(
            secondAction,
            request(nexus, secondAction, revision = 1L, key = "new"),
            3L,
            1L + IDEMPOTENCY_TTL_MILLIS,
        )
        assertTrue(atExpiry.acknowledgement!!.changed)
        assertEquals(2L, atExpiry.state.revision)
    }

    @Test
    fun auditAndIdempotencySchemasRejectMissingOrInconsistentAcceptanceTruth() {
        val acknowledgement = noOpAcknowledgement()
        val stamp = acknowledgement.provenance
        assertFailsWith<IllegalArgumentException> {
            AuditRecord(
                receiptId = stamp.receiptId,
                kind = AuditKind.ACTION,
                wallTimeMillis = stamp.wallTimeMillis,
                actionType = ActionType.SET_DISPLAY_HUD,
                fields = ActionType.SET_DISPLAY_HUD.affectedFields,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuditRecord(
                receiptId = stamp.receiptId,
                kind = AuditKind.ACTION,
                wallTimeMillis = stamp.wallTimeMillis,
                actionType = ActionType.SET_DISPLAY_HUD,
                fields = frozenSetOf(),
                provenance = stamp,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StateChange(
                StateField.DISPLAY_HUD,
                BooleanStateValue(false),
                BooleanStateValue(true),
                stamp,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StateChange(
                StateField.DISPLAY_HUD,
                StringStateValue("on"),
                StringStateValue("on"),
                stamp,
            )
        }
        val hudChange = StateChange(
            StateField.DISPLAY_HUD,
            StringStateValue("auto"),
            StringStateValue("on"),
            stamp,
        )
        assertFailsWith<IllegalArgumentException> {
            StateDelta(
                revision = 1L,
                sequence = 1L,
                changes = frozenListOf(
                    hudChange,
                    StateChange(
                        StateField.DISPLAY_HUD,
                        StringStateValue("on"),
                        StringStateValue("off"),
                        stamp,
                    ),
                ),
                provenance = stamp,
            )
        }
        val gridChange = StateChange(
            StateField.GRID_ENABLED,
            BooleanStateValue(true),
            BooleanStateValue(false),
            stamp,
        )
        val gridDelta = StateDelta(
            revision = 1L,
            sequence = 1L,
            changes = frozenListOf(gridChange),
            provenance = stamp,
        )
        assertFailsWith<IllegalArgumentException> {
            ActionAcknowledgement(
                receiptId = stamp.receiptId,
                actionType = ActionType.SET_DISPLAY_HUD,
                changed = true,
                revision = 1L,
                sequence = 1L,
                effectiveValue = BooleanStateValue(false),
                provenance = stamp,
                delta = gridDelta,
                effects = frozenListOf(),
            )
        }
        val hudDelta = StateDelta(
            revision = 1L,
            sequence = 1L,
            changes = frozenListOf(hudChange),
            provenance = stamp,
        )
        assertFailsWith<IllegalArgumentException> {
            ActionAcknowledgement(
                receiptId = stamp.receiptId,
                actionType = ActionType.SET_DISPLAY_HUD,
                changed = true,
                revision = 1L,
                sequence = 1L,
                effectiveValue = null,
                provenance = stamp,
                delta = hudDelta,
                effects = frozenListOf(),
            )
        }
        assertFailsWith<IllegalArgumentException> { AuditRetentionPolicy(0, 1L) }
        assertFailsWith<IllegalArgumentException> { AuditRetentionPolicy(1, 0L) }
        val auditPolicy = AuditRetentionPolicy(32, IDEMPOTENCY_TTL_MILLIS)
        assertEquals(32, auditPolicy.maximumRecords)
        val acceptedAudit = AuditRecord(
            receiptId = stamp.receiptId,
            kind = AuditKind.NO_OP,
            wallTimeMillis = stamp.wallTimeMillis,
            actionType = ActionType.SET_DISPLAY_HUD,
            fields = ActionType.SET_DISPLAY_HUD.affectedFields,
            provenance = stamp,
        )
        assertEquals(
            acceptedAudit,
            AuditIndexSnapshot(auditPolicy, stamp.wallTimeMillis, frozenListOf(acceptedAudit)).records.single(),
        )
        assertFailsWith<IllegalArgumentException> {
            acceptedAudit.copy(wallTimeMillis = stamp.wallTimeMillis + 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            AuditIndexSnapshot(auditPolicy, stamp.wallTimeMillis - 1L, frozenListOf(acceptedAudit))
        }
        assertFailsWith<IllegalArgumentException> {
            AuditIndexSnapshot(
                auditPolicy,
                stamp.wallTimeMillis + IDEMPOTENCY_TTL_MILLIS + 1L,
                frozenListOf(acceptedAudit),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuditIndexSnapshot(
                AuditRetentionPolicy(1, IDEMPOTENCY_TTL_MILLIS),
                stamp.wallTimeMillis,
                frozenListOf(acceptedAudit, acceptedAudit),
            )
        }

        val record = IdempotencyRecord(
            principal = nexus,
            key = "stable",
            action = SetDisplayHud("auto"),
            canonicalPayload = SetDisplayHud("auto").canonicalPayload(),
            acknowledgement = acknowledgement,
            acceptedWallTimeMillis = 1L,
            expiresWallTimeMillis = 1L + IDEMPOTENCY_TTL_MILLIS,
            ordinal = 1L,
        )
        assertFailsWith<IllegalArgumentException> { record.copy(principal = human) }
        assertFailsWith<IllegalArgumentException> {
            record.copy(canonicalPayload = SetActiveThemeId("blossom-dark").canonicalPayload())
        }
        assertFailsWith<IllegalArgumentException> {
            IdempotencyIndexSnapshot(
                policy = IdempotencyIndexPolicy(maximumRecords = 1),
                records = frozenListOf(record, record.copy(key = "other", ordinal = 2L)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            IdempotencyIndexSnapshot(
                policy = IdempotencyIndexPolicy(maximumRecords = 2),
                records = frozenListOf(record, record.copy(ordinal = 2L)),
            )
        }
    }

    @Test
    fun acceptedRecordsBindTypedIntentClocksAndRefusalFields() {
        val action = SetDisplayHud("off")
        val accepted = assertNotNull(
            ReferenceHarness(live(nexus to setOf(Capability.CONTROL_DISPLAY))).dispatch(
                action,
                request(nexus, action),
                monotonicMillis = 10L,
                wallTimeMillis = 10L,
            ).acceptance,
        )
        assertEquals(StringStateValue("off"), accepted.snapshot.effective.valueOf(StateField.DISPLAY_HUD))

        val forgedAction = SetDisplayHud("on")
        assertFailsWith<IllegalArgumentException> {
            accepted.copy(
                action = forgedAction,
                canonicalPayload = forgedAction.canonicalPayload(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            accepted.copy(snapshot = accepted.snapshot.copy(wallTimeMillis = 9L))
        }
        assertFailsWith<IllegalArgumentException> {
            AuditRecord(
                receiptId = "refused",
                kind = AuditKind.REFUSAL,
                wallTimeMillis = 10L,
                actionType = ActionType.SET_DISPLAY_HUD,
                fields = frozenSetOf(StateField.SCOPE_GAIN),
                refusal = Refusal(RefusalCode.INVALID_REQUEST, "Retry with a valid HUD mode."),
            )
        }

        val idempotency = IdempotencyRecord(
            principal = nexus,
            key = "accepted",
            action = action,
            canonicalPayload = action.canonicalPayload(),
            acknowledgement = accepted.acknowledgement,
            acceptedWallTimeMillis = 10L,
            expiresWallTimeMillis = 10L + IDEMPOTENCY_TTL_MILLIS,
            ordinal = 1L,
        )
        assertFailsWith<IllegalArgumentException> {
            idempotency.copy(
                action = forgedAction,
                canonicalPayload = forgedAction.canonicalPayload(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            idempotency.copy(acceptedWallTimeMillis = 9L, expiresWallTimeMillis = 9L + IDEMPOTENCY_TTL_MILLIS)
        }
    }

    @Test
    fun acceptedDeltaExactlyCoversThePreviousToPostStateDiffForEveryActionShape() {
        val previous = live(nexus to setOf(Capability.CONTROL_SCOPE, Capability.CONTROL_TRANSPORT))

        val cycleAction = SetBeamCycle(seconds = 5.0f, perTrack = true)
        val cycleRequest = request(nexus, cycleAction)
        val cycleStamp = ProvenanceStamp("cycle", nexus, cycleRequest.reason, Transport.BINDER, "s", 10L, 10L)
        val cycleChange = StateChange(
            StateField.BEAM_CYCLE_SECONDS,
            FloatStateValue(0.0f),
            FloatStateValue(5.0f),
            cycleStamp,
        )
        val cycleDelta = StateDelta(
            revision = 1L,
            sequence = 1L,
            changes = frozenListOf(cycleChange),
            provenance = cycleStamp,
        )
        val cycleAcknowledgement = ActionAcknowledgement(
            receiptId = cycleStamp.receiptId,
            actionType = cycleAction.type,
            changed = true,
            revision = 1L,
            sequence = 1L,
            effectiveValue = FloatStateValue(5.0f),
            provenance = cycleStamp,
            delta = cycleDelta,
            effects = frozenListOf(),
        )
        val cyclePost = previous.copy(
            revision = 1L,
            sequence = 1L,
            wallTimeMillis = 10L,
            desired = previous.desired.copy(beamCycleSeconds = 5.0f, beamCyclePerTrack = true),
            effective = previous.effective.copy(beamCycleSeconds = 5.0f, beamCyclePerTrack = true),
            provenance = FrozenMap.copyOf(
                previous.provenance + mapOf(
                    StateField.BEAM_CYCLE_SECONDS to cycleStamp,
                    StateField.BEAM_CYCLE_PER_TRACK to cycleStamp,
                ),
            ),
        )
        val cycleAudit = AuditRecord(
            receiptId = cycleStamp.receiptId,
            kind = AuditKind.ACTION,
            wallTimeMillis = 10L,
            actionType = cycleAction.type,
            fields = cycleAction.type.affectedFields,
            provenance = cycleStamp,
        )
        val cycleProjection = SettingsRowProjection(
            schema = SettingsSchema.rows.single { it.field == StateField.BEAM_CYCLE_SECONDS },
            desired = FloatStateValue(5.0f),
            effective = FloatStateValue(5.0f),
            availability = cyclePost.availability.getValue(StateField.BEAM_CYCLE_SECONDS),
            authority = cyclePost.authority.getValue(StateField.BEAM_CYCLE_SECONDS),
            provenance = cycleStamp,
            visibleLastWriter = cycleStamp,
        )
        assertFailsWith<IllegalArgumentException> {
            AcceptedActionRecord(
                request = cycleRequest,
                action = cycleAction,
                canonicalPayload = cycleAction.canonicalPayload(),
                acknowledgement = cycleAcknowledgement,
                previousSnapshot = previous,
                snapshot = cyclePost,
                audit = cycleAudit,
                settingsProjections = frozenListOf(cycleProjection),
            )
        }

        val remoteAction = StartRemoteHost("living-room", "100.64.0.2", 4747)
        val remoteRequest = request(nexus, remoteAction)
        val remoteStamp = ProvenanceStamp("remote", nexus, remoteRequest.reason, Transport.BINDER, "s", 20L, 20L)
        val remoteChanges = frozenListOf(
            StateChange(StateField.SOURCE_KIND, StringStateValue("none"), StringStateValue("remote"), remoteStamp),
            StateChange(
                StateField.REMOTE_HOST_ID,
                NullableStringStateValue(null),
                NullableStringStateValue("living-room"),
                remoteStamp,
            ),
        )
        val remoteDelta = StateDelta(
            revision = 1L,
            sequence = 1L,
            changes = remoteChanges,
            provenance = remoteStamp,
        )
        val remoteAcknowledgement = ActionAcknowledgement(
            receiptId = remoteStamp.receiptId,
            actionType = remoteAction.type,
            changed = true,
            revision = 1L,
            sequence = 1L,
            effectiveValue = null,
            provenance = remoteStamp,
            delta = remoteDelta,
            effects = frozenListOf(),
        )
        val detachedPost = previous.copy(
            revision = 1L,
            sequence = 1L,
            wallTimeMillis = 20L,
            provenance = FrozenMap.copyOf(
                previous.provenance + mapOf(
                    StateField.SOURCE_KIND to remoteStamp,
                    StateField.REMOTE_HOST_ID to remoteStamp,
                ),
            ),
        )
        val remoteAudit = AuditRecord(
            receiptId = remoteStamp.receiptId,
            kind = AuditKind.ACTION,
            wallTimeMillis = 20L,
            actionType = remoteAction.type,
            fields = remoteAction.type.affectedFields,
            provenance = remoteStamp,
        )
        val remoteProjections = FrozenList.copyOf(remoteChanges.map { change ->
            SettingsRowProjection(
                schema = SettingsSchema.rows.single { it.field == change.field },
                desired = change.newValue,
                effective = change.newValue,
                availability = detachedPost.availability.getValue(change.field),
                authority = detachedPost.authority.getValue(change.field),
                provenance = remoteStamp,
                visibleLastWriter = remoteStamp,
            )
        })
        assertFailsWith<IllegalArgumentException> {
            AcceptedActionRecord(
                request = remoteRequest,
                action = remoteAction,
                canonicalPayload = remoteAction.canonicalPayload(),
                acknowledgement = remoteAcknowledgement,
                previousSnapshot = previous,
                snapshot = detachedPost,
                audit = remoteAudit,
                settingsProjections = remoteProjections,
            )
        }
    }

    @Test
    fun testOracleRejectsOrderingAndExpiryOverflowWithoutMutatingState() {
        val action = SetDisplayHud("on")
        val revisionHarness = ReferenceHarness(
            live(nexus to setOf(Capability.CONTROL_DISPLAY)).copy(
                revision = Long.MAX_VALUE,
                sequence = Long.MAX_VALUE,
            ),
        )
        assertFailsWith<ArithmeticException> {
            revisionHarness.dispatch(
                action,
                request(nexus, action, revision = Long.MAX_VALUE),
                1L,
                1L,
            )
        }
        assertEquals(Long.MAX_VALUE, revisionHarness.state.revision)
        assertEquals("auto", revisionHarness.state.effective.displayHud)
        assertEquals(0, revisionHarness.auditSize)
        assertEquals(0, revisionHarness.idempotencySize)

        val expiryHarness = ReferenceHarness(live(nexus to setOf(Capability.CONTROL_DISPLAY)))
        assertFailsWith<ArithmeticException> {
            expiryHarness.dispatch(action, request(nexus, action), 1L, Long.MAX_VALUE)
        }
        assertEquals(0L, expiryHarness.state.revision)
        assertEquals("auto", expiryHarness.state.effective.displayHud)
        assertEquals(0, expiryHarness.auditSize)
        assertEquals(0, expiryHarness.idempotencySize)

        val ordinalHarness = ReferenceHarness(
            live(nexus to setOf(Capability.CONTROL_DISPLAY)),
            initialOrdinal = Long.MAX_VALUE,
        )
        assertFailsWith<ArithmeticException> {
            ordinalHarness.dispatch(action, request(nexus, action), 1L, 1L)
        }
        assertEquals(0L, ordinalHarness.state.revision)
        assertEquals(0, ordinalHarness.auditSize)
        assertEquals(0, ordinalHarness.idempotencySize)

        val monotonicHarness = ReferenceHarness(live(nexus to setOf(Capability.CONTROL_DISPLAY)))
        monotonicHarness.dispatch(action, request(nexus, action, key = "first"), 100L, 100L)
        val secondAction = SetDisplayHud("off")
        assertFailsWith<IllegalArgumentException> {
            monotonicHarness.dispatch(
                secondAction,
                request(nexus, secondAction, revision = 1L, key = "backward-mono"),
                99L,
                101L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            monotonicHarness.dispatch(
                secondAction,
                request(nexus, secondAction, revision = 1L, key = "backward-wall"),
                101L,
                99L,
            )
        }
        assertEquals(1L, monotonicHarness.state.revision)
        assertEquals(1, monotonicHarness.auditSize)
        assertEquals(1, monotonicHarness.idempotencySize)
    }

    @Test
    fun captureTruthAvailabilityAuthorityAndProtocolRecordsFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            CaptureTruth(
                state = CaptureTruthState.FLOWING,
                cause = CaptureCause.SILENT_CONTENT,
            )
        }
        assertFailsWith<IllegalArgumentException> { CaptureTruth(CaptureTruthState.UNAVAILABLE) }
        assertFailsWith<IllegalArgumentException> {
            CaptureTruth(
                state = CaptureTruthState.RETRYING,
                attempt = 0,
                nextRetryWallTimeMillis = 2L,
                backoffMillis = 1L,
            )
        }
        val validRetry = CaptureTruth(
            state = CaptureTruthState.RETRYING,
            stateSinceWallTimeMillis = 10L,
            attempt = 1,
            nextRetryWallTimeMillis = 11L,
            backoffMillis = 1L,
        )
        assertEquals(1, validRetry.attempt)
        assertFailsWith<IllegalArgumentException> {
            validRetry.copy(nextRetryWallTimeMillis = 10L)
        }
        assertFailsWith<IllegalArgumentException> {
            validRetry.copy(backoffMillis = MAX_CAPTURE_RETRY_BACKOFF_MILLIS + 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            validRetry.copy(nextRetryWallTimeMillis = 12L)
        }
        val unavailableFix = Fix(RefusalCode.AUTHORITY_UNAVAILABLE, "Grant the controlling authority.")
        val unavailableAuthority = Authority(AuthorityState.SYSTEM_BLOCKED, unavailableFix)
        assertEquals(
            unavailableFix,
            CaptureTruth(
                state = CaptureTruthState.UNAVAILABLE,
                authority = unavailableAuthority,
                fix = unavailableFix,
            ).fix,
        )
        assertFailsWith<IllegalArgumentException> {
            CaptureTruth(
                state = CaptureTruthState.UNAVAILABLE,
                authority = unavailableAuthority,
                fix = Fix(RefusalCode.INVALID_REQUEST, "Wrong repair."),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Availability(AvailabilityState.AVAILABLE, Fix(RefusalCode.INVALID_REQUEST, "not needed"))
        }
        assertFailsWith<IllegalArgumentException> {
            Authority(AuthorityState.SYSTEM_BLOCKED)
        }
        assertFailsWith<IllegalArgumentException> {
            Availability(
                AvailabilityState.DISTRIBUTION_UNAVAILABLE,
                Fix(RefusalCode.INVALID_REQUEST, "Wrong blocker code."),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Authority(
                AuthorityState.SESSION_BLOCKED,
                Fix(RefusalCode.SYSTEM_UNAVAILABLE, "Wrong authority code."),
            )
        }
        assertFailsWith<IllegalArgumentException> { Refusal(RefusalCode.INVALID_REQUEST, "") }
        assertFailsWith<IllegalArgumentException> {
            IdempotencyRecord(
                principal = nexus,
                key = "short",
                action = SetDisplayHud("auto"),
                canonicalPayload = SetDisplayHud("auto").canonicalPayload(),
                acknowledgement = noOpAcknowledgement(),
                acceptedWallTimeMillis = 1L,
                expiresWallTimeMillis = 1L + IDEMPOTENCY_TTL_MILLIS - 1L,
                ordinal = 1L,
            )
        }
        val evidenceKinds = mapOf(
            CaptureCause.SOURCE_OPT_OUT to CaptureEvidenceKind.SOURCE_POLICY_OPT_OUT,
            CaptureCause.PROTECTED_OR_DRM to CaptureEvidenceKind.DRM_OR_PROTECTION,
            CaptureCause.SILENT_CONTENT to CaptureEvidenceKind.MEASURED_SILENCE,
            CaptureCause.UNKNOWN to CaptureEvidenceKind.INCONCLUSIVE,
        )
        evidenceKinds.forEach { (cause, evidenceKind) ->
            val capture = CaptureTruth(
                state = CaptureTruthState.PRESENT_SILENT_OR_OPTED_OUT,
                stateSinceWallTimeMillis = 5L,
                cause = cause,
                evidence = CaptureEvidence(
                    evidenceKind,
                    "capture-${cause.wireName}",
                    5L,
                    "evidence-qualified ${cause.wireName}",
                ),
            )
            assertEquals(cause, capture.cause)
        }
        assertFailsWith<IllegalArgumentException> {
            CaptureTruth(
                state = CaptureTruthState.PRESENT_SILENT_OR_OPTED_OUT,
                cause = CaptureCause.SOURCE_OPT_OUT,
                evidence = CaptureEvidence(
                    CaptureEvidenceKind.DRM_OR_PROTECTION,
                    "wrong-proof",
                    5L,
                    "wrong evidence kind",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CaptureTruth(
                state = CaptureTruthState.PRESENT_SILENT_OR_OPTED_OUT,
                stateSinceWallTimeMillis = 10L,
                cause = CaptureCause.SILENT_CONTENT,
                evidence = CaptureEvidence(
                    CaptureEvidenceKind.MEASURED_SILENCE,
                    "stale-proof",
                    9L,
                    "evidence predates the qualified transition",
                ),
            )
        }
    }

    @Test
    fun geometryContractKeepsTrueBeamSourceClockLayersAndHonestDropCount() {
        val frame = GeometryFrame(
            sourceClock = SourceClockIdentity("relay", "audio-clock-1"),
            sequence = 7L,
            monotonicMillis = 42L,
            scopeMode = 1,
            segments = frozenListOf(
                BeamSegment(NormalizedPoint(-1.0f, 0.0f), NormalizedPoint(1.0f, 0.0f), 0.5f),
            ),
            beamEnergy = 0.5f,
            signalState = CaptureTruthState.FLOWING,
            layerStates = frozenMapOf(
                GeometryLayer.TRUE_BEAM to GeometryLayerState(true, 1.0f, GeometryQualityTier.FULL),
                GeometryLayer.PHOSPHOR_GEOMETRY to GeometryLayerState(true, 0.5f, GeometryQualityTier.FULL),
                GeometryLayer.PROJECTM_FIELD to GeometryLayerState(true, 0.25f, GeometryQualityTier.REDUCED),
            ),
            projectM = ProjectMFieldMetadata(
                enabled = true,
                presetId = "preset-1",
                fieldMonotonicMillis = 42L,
                motionState = ProjectMMotionState.LIVE,
                parameters = frozenMapOf("blend" to 0.25f),
                featureValues = frozenMapOf("bass" to 0.5f),
            ),
            droppedFrames = 3L,
            qualityTier = GeometryQualityTier.FULL,
        )
        assertEquals("audio-clock-1", frame.sourceClock.clockId)
        assertEquals(GeometryLayer.entries.toSet(), frame.layerStates.keys)
        assertTrue(frame.layerStates.getValue(GeometryLayer.TRUE_BEAM).enabled)
        assertEquals(42L, frame.projectM.fieldMonotonicMillis)
        assertEquals(0.25f, frame.projectM.parameters.getValue("blend"))
        assertEquals(0.5f, frame.projectM.featureValues.getValue("bass"))
        assertEquals(3L, frame.droppedFrames)
        assertFailsWith<IllegalArgumentException> {
            frame.copy(
                layerStates = frozenMapOf(
                    GeometryLayer.TRUE_BEAM to GeometryLayerState(false, 0.0f, GeometryQualityTier.MINIMAL),
                    GeometryLayer.PHOSPHOR_GEOMETRY to GeometryLayerState(true, 0.5f, GeometryQualityTier.FULL),
                    GeometryLayer.PROJECTM_FIELD to GeometryLayerState(true, 0.25f, GeometryQualityTier.REDUCED),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(projectM = ProjectMFieldMetadata(false))
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(projectM = frame.projectM.copy(fieldMonotonicMillis = 41L))
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(qualityTier = GeometryQualityTier.REDUCED)
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(
                layerStates = FrozenMap.copyOf(
                    frame.layerStates + (
                        GeometryLayer.TRUE_BEAM to GeometryLayerState(
                            true,
                            GeometryFrame.MIN_TRUE_BEAM_BLEND / 2.0f,
                            GeometryQualityTier.FULL,
                        )
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(
                qualityTier = GeometryQualityTier.MINIMAL,
                layerStates = frozenMapOf(
                    GeometryLayer.TRUE_BEAM to GeometryLayerState(true, 1.0f, GeometryQualityTier.MINIMAL),
                    GeometryLayer.PHOSPHOR_GEOMETRY to GeometryLayerState(true, 0.5f, GeometryQualityTier.MINIMAL),
                    GeometryLayer.PROJECTM_FIELD to GeometryLayerState(true, 0.25f, GeometryQualityTier.FULL),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(segments = frozenListOf())
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(beamEnergy = 0.0f)
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(
                segments = frozenListOf(
                    BeamSegment(NormalizedPoint(-1.0f, 0.0f), NormalizedPoint(1.0f, 0.0f), 0.0f),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(
                segments = frozenListOf(
                    BeamSegment(NormalizedPoint(0.0f, 0.0f), NormalizedPoint(0.0f, 0.0f), 1.0f),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(signalState = CaptureTruthState.STOPPED)
        }
        val stillFrame = frame.copy(
            segments = frozenListOf(),
            beamEnergy = 0.0f,
            signalState = CaptureTruthState.STOPPED,
            projectM = frame.projectM.copy(motionState = ProjectMMotionState.STILL),
        )
        assertEquals(ProjectMMotionState.STILL, stillFrame.projectM.motionState)
        assertFailsWith<IllegalArgumentException> {
            stillFrame.copy(projectM = stillFrame.projectM.copy(motionState = ProjectMMotionState.LIVE))
        }
        assertFailsWith<IllegalArgumentException> {
            frame.copy(
                layerStates = FrozenMap.copyOf(frame.layerStates - GeometryLayer.PHOSPHOR_GEOMETRY),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GeometryLoadSheddingPolicy(
                frozenListOf(
                    GeometryLayer.TRUE_BEAM,
                    GeometryLayer.PHOSPHOR_GEOMETRY,
                    GeometryLayer.PROJECTM_FIELD,
                ),
            )
        }
    }

    private fun assertRefusal(expected: RefusalCode, result: HarnessResult) {
        val refusal = assertNotNull(result.refusal)
        assertEquals(expected, refusal.code)
        assertTrue(refusal.fix.isNotBlank())
        assertEquals(null, result.acknowledgement)
    }

    private fun noOpAcknowledgement(): ActionAcknowledgement {
        val stamp = ProvenanceStamp("noop", nexus, "proof", Transport.BINDER, "s", 1L, 1L)
        return ActionAcknowledgement(
            receiptId = stamp.receiptId,
            actionType = ActionType.SET_DISPLAY_HUD,
            changed = false,
            revision = 0L,
            sequence = 0L,
            effectiveValue = StringStateValue("auto"),
            provenance = stamp,
            delta = null,
            effects = frozenListOf(),
        )
    }

    private fun currentScopeActionMethods(): Set<String> {
        val methods = ScopeActions::class.java.declaredMethods
        val shapes = methods.map { method ->
            "${method.name}(${method.parameterTypes.joinToString(",") { it.name }}):${method.returnType.name}"
        }.toSet()
        assertEquals(EXPECTED_SCOPE_ACTION_SHAPES, shapes)
        assertEquals(methods.size, shapes.size, "ScopeActions must not hide overloads behind one method name")
        return methods.map { it.name }.toSet()
    }
}

private val EXPECTED_SCOPE_ACTION_SHAPES = setOf(
    "togglePlay():void",
    "openFile():void",
    "exportSettings():void",
    "importSettings():void",
    "startMic():void",
    "startCapture():void",
    "stopLive():void",
    "captureConsentNeeded():boolean",
    "next():void",
    "prev():void",
    "seekTo(long):void",
    "startRemote():void",
    "setMode(int):void",
    "setBeam(int):void",
    "setFps(int):void",
    "setOversample(int):void",
    "setRoom(dev.phosphor.mobil3.ui.Palette):void",
    "setFocus(float):void",
    "setCustomBeam(java.util.List,int):void",
    "setBeamCycle(float,boolean):void",
    "setBeamEnergy(float):void",
    "setGlow(float):void",
    "tapBeamRandom():void",
    "setBeamRandomRange(float,float):void",
    "tapGlowRandom():void",
    "setGlowRandomRange(float,float):void",
    "setGeomFx(int):void",
    "setGeomAmount(float):void",
    "setGrid(boolean):void",
    "setGainAuto(boolean):void",
    "setViewLock(boolean):void",
    "setHudMode(int):void",
    "setFullscreen(boolean):void",
    "openCaptureMetadataSettings():void",
    "openLink(java.lang.String):void",
    "markBestiaryFound():void",
    "isScopeRotationLocked():boolean",
    "setScopeRotationLocked(boolean):void",
    "isUiPlacementLocked():boolean",
    "lockedUiLandscape():boolean",
    "setUiPlacementLocked(boolean):void",
    "setRemoteLatencyMode(int):void",
    "setRemoteNetworkMode(int):void",
    "remoteHosts():java.util.List",
    "saveRemoteHost(java.lang.String,int,java.lang.String,java.lang.String,java.lang.String):java.lang.String",
    "removeRemoteHost(java.lang.String,int):java.lang.String",
    "startRemoteHost(java.lang.String,java.lang.String,int):void",
    "setRemoteStreams(boolean,boolean):void",
    "disconnectRemote():void",
    "epilepsyAcknowledged():boolean",
    "ackEpilepsy():void",
    "setGainAbsolute(float):void",
    "orbitBy(float,float):void",
    "dollyBy(float):void",
    "openFolder():void",
    "jumpToQueue(int):void",
    "volumeFrac():float",
    "setVolume(float):void",
    "makeSurface():android.view.SurfaceView",
)

private val EXPECTED_SCOPE_ACTION_METHODS = setOf(
    "togglePlay",
    "openFile",
    "exportSettings",
    "importSettings",
    "startMic",
    "startCapture",
    "stopLive",
    "captureConsentNeeded",
    "next",
    "prev",
    "seekTo",
    "startRemote",
    "setMode",
    "setBeam",
    "setFps",
    "setOversample",
    "setRoom",
    "setFocus",
    "setCustomBeam",
    "setBeamCycle",
    "setBeamEnergy",
    "setGlow",
    "tapBeamRandom",
    "setBeamRandomRange",
    "tapGlowRandom",
    "setGlowRandomRange",
    "setGeomFx",
    "setGeomAmount",
    "setGrid",
    "setGainAuto",
    "setViewLock",
    "setHudMode",
    "setFullscreen",
    "openCaptureMetadataSettings",
    "openLink",
    "markBestiaryFound",
    "isScopeRotationLocked",
    "setScopeRotationLocked",
    "isUiPlacementLocked",
    "lockedUiLandscape",
    "setUiPlacementLocked",
    "setRemoteLatencyMode",
    "setRemoteNetworkMode",
    "remoteHosts",
    "saveRemoteHost",
    "removeRemoteHost",
    "startRemoteHost",
    "setRemoteStreams",
    "disconnectRemote",
    "epilepsyAcknowledged",
    "ackEpilepsy",
    "setGainAbsolute",
    "orbitBy",
    "dollyBy",
    "openFolder",
    "jumpToQueue",
    "volumeFrac",
    "setVolume",
    "makeSurface",
)

private val ALL_ACTION_SAMPLES: List<PhosphorAction> = listOf(
    TogglePlayback,
    RequestOpenFile,
    RequestSettingsExport,
    RequestSettingsImport,
    RequestMicrophoneSource,
    RequestPlaybackCapture,
    StopLiveSource,
    NextTrack,
    PreviousTrack,
    SeekTo(0L),
    RequestRemoteSource,
    SetScopeMode(0),
    SetBeamPreset(0),
    SetFrameRate(120),
    SetOversample(1),
    SetActiveThemeId("blossom-dark"),
    SetFocus(0.3f),
    SetCustomBeam(BeamGradient(listOf(RgbaColor(UByte.MAX_VALUE, UByte.MAX_VALUE, UByte.MAX_VALUE)), 1)),
    SetBeamCycle(0.0f, false),
    SetBeamEnergy(1.0f),
    SetGlow(1.0f),
    RandomizeBeam,
    SetBeamRandomRange(FloatRangeValue(0.0f, 1.0f)),
    RandomizeGlow,
    SetGlowRandomRange(FloatRangeValue(0.0f, 1.0f)),
    SetGeometryEffect(0),
    SetGeometryAmount(0.0f),
    SetGridEnabled(true),
    SetAutoGain(false),
    SetViewLock(false),
    SetDisplayHud("auto"),
    SetFullscreen(false),
    OpenCaptureMetadataSettings,
    MarkBestiaryFound,
    SetScopeRotationLock(false),
    SetUiPlacementLock(false),
    SetRemoteLatencyMode(0),
    SetRemoteNetworkMode(0),
    StartRemoteHost("host", "100.64.0.2", 4747),
    SaveRemoteHost("", 0, "studio", "studio.tailnet", 45777),
    RemoveRemoteHost("studio.tailnet", 45777),
    SetRemoteStreams(audio = true, geometry = true),
    DisconnectRemote,
    ConfirmEpilepsySafety,
    SetScopeGain(1.0f),
    OrbitScope(0.0f, 0.0f),
    DollyScope(0.0f),
    RequestOpenFolder,
    JumpToQueue(0),
    SetVolume(1.0f),
)
