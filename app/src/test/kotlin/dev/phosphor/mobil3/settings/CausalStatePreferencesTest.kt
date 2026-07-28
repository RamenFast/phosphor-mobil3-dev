package dev.phosphor.mobil3.settings

import dev.phosphor.mobil3.state.ActionAcknowledgement
import dev.phosphor.mobil3.state.ActionRequest
import dev.phosphor.mobil3.state.ActionType
import dev.phosphor.mobil3.state.AuditIndexSnapshot
import dev.phosphor.mobil3.state.AuditKind
import dev.phosphor.mobil3.state.AuditRecord
import dev.phosphor.mobil3.state.AuditRetentionPolicy
import dev.phosphor.mobil3.state.BooleanStateValue
import dev.phosphor.mobil3.state.Capability
import dev.phosphor.mobil3.state.EffectKind
import dev.phosphor.mobil3.state.FrozenList
import dev.phosphor.mobil3.state.FrozenSet
import dev.phosphor.mobil3.state.IDEMPOTENCY_TTL_MILLIS
import dev.phosphor.mobil3.state.IdempotencyIndexPolicy
import dev.phosphor.mobil3.state.IdempotencyIndexSnapshot
import dev.phosphor.mobil3.state.IdempotencyRecord
import dev.phosphor.mobil3.state.InertEffectDescription
import dev.phosphor.mobil3.state.InitialSnapshots
import dev.phosphor.mobil3.state.PrincipalId
import dev.phosphor.mobil3.state.PrincipalKind
import dev.phosphor.mobil3.state.ProvenanceStamp
import dev.phosphor.mobil3.state.Refusal
import dev.phosphor.mobil3.state.RefusalCode
import dev.phosphor.mobil3.state.SetDisplayHud
import dev.phosphor.mobil3.state.StateChange
import dev.phosphor.mobil3.state.StateDelta
import dev.phosphor.mobil3.state.StateField
import dev.phosphor.mobil3.state.StringStateValue
import dev.phosphor.mobil3.state.TogglePlayback
import dev.phosphor.mobil3.state.Transport
import dev.phosphor.mobil3.state.frozenListOf
import dev.phosphor.mobil3.state.frozenMapOf
import dev.phosphor.mobil3.store.PhosphorStoreImage
import dev.phosphor.mobil3.store.PhosphorStoreLoadResult
import dev.phosphor.mobil3.store.PhosphorStorePersistenceResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.security.MessageDigest

class CausalStatePreferencesTest {
    @Test
    fun codecRoundTripRestoresChecksumBoundStoreImageAndSharedProvenanceIdentity() {
        val base = InitialSnapshots.play(nowWallMillis = 10)
        val image = sampleImage(base, mode = "auto", revision = 3)
        val encoded = HudCausalEnvelopeCodec.encode(image)

        val decoded = HudCausalEnvelopeCodec.decode(encoded, base)

        assertEquals("phosphor.causal.hud/2", JSONObject(encoded).getString("schema"))
        assertTrue(JSONObject(encoded).getString("checksum_sha256").matches(Regex("[0-9a-f]{64}")))
        assertEquals(3, decoded.snapshot.revision)
        assertEquals("auto", decoded.snapshot.effective.displayHud)
        assertEquals(base.effective.scopeMode, decoded.snapshot.effective.scopeMode)
        assertEquals(4, decoded.nextReceiptOrdinal)
        assertEquals(4, decoded.nextIdempotencyOrdinal)
        val stamp = decoded.snapshot.provenance.getValue(StateField.DISPLAY_HUD)
        assertSame(stamp, decoded.audit.records.single().provenance)
        assertSame(stamp, decoded.idempotency.records.single().acknowledgement.provenance)
    }

    @Test
    fun legacyV1EnvelopeRequiresExplicitMigrationToStrictChecksumBoundV2() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base, mode = "auto", revision = 3)
        val legacyRoot = JSONObject(HudCausalEnvelopeCodec.encode(image))
            .put("schema", HudCausalEnvelopeCodec.LEGACY_SCHEMA)
        legacyRoot.getJSONObject("payload").remove("authority_plane")
        legacyRoot.put("checksum_sha256", sha256(HudCausalEnvelopeCodec.canonicalJson(legacyRoot.getJSONObject("payload"))))
        val legacy = legacyRoot.toString()

        val direct = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(legacy, base)
        }
        assertEquals("schema_migration_required", direct.error)

        val migrated = HudCausalEnvelopeCodec.migrateLegacyV1(legacy, base)
        val migratedRoot = JSONObject(migrated)
        assertEquals(HudCausalEnvelopeCodec.SCHEMA, migratedRoot.getString("schema"))
        assertEquals("auto", HudCausalEnvelopeCodec.decode(migrated, base).snapshot.effective.displayHud)
    }

    @Test
    fun tamperedLegacyV1EnvelopeCannotBeMigratedByRechecksumming() {
        val base = InitialSnapshots.play()
        val legacy = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base, mode = "auto", revision = 3)))
            .put("schema", HudCausalEnvelopeCodec.LEGACY_SCHEMA)
        legacy.getJSONObject("payload").remove("authority_plane")
        val validLegacy = JSONObject(legacy.toString())
        validLegacy.put("checksum_sha256", sha256(HudCausalEnvelopeCodec.canonicalJson(validLegacy.getJSONObject("payload"))))
        validLegacy.getJSONObject("payload").put("display_hud", "off")

        val error = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.migrateLegacyV1(validLegacy.toString(), base)
        }

        assertEquals("checksum_mismatch", error.error)
    }

    @Test
    fun checksumCorruptionFailsWithFixAndDoesNotFallBackToLegacy() {
        val base = InitialSnapshots.play()
        val prefs = InMemoryPreferenceBoundary(
            linkedMapOf(
                CausalStatePreferences.KEY_CAUSAL_ENVELOPE to HudCausalEnvelopeCodec.encode(sampleImage(base)).replace("auto", "off"),
                CausalStatePreferences.KEY_LEGACY_HUD_MODE to 0,
            )
        )

        val result = CausalStatePreferences(prefs, base).load()

        val corrupt = assertIs<PhosphorStoreLoadResult.CorruptEnvelope>(result)
        assertTrue(corrupt.refusal.fix.contains("Do not fall back silently"))
    }

    @Test
    fun validCausalEnvelopeWinsOverLegacyValues() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base, mode = "on", revision = 8)
        val prefs = InMemoryPreferenceBoundary(
            linkedMapOf(
                CausalStatePreferences.KEY_CAUSAL_ENVELOPE to HudCausalEnvelopeCodec.encode(image),
                CausalStatePreferences.KEY_LEGACY_HUD_MODE to 2,
                CausalStatePreferences.KEY_LEGACY_NERD_HUD to false,
            )
        )

        val loaded = assertIs<PhosphorStoreLoadResult.CausalImage>(CausalStatePreferences(prefs, base).load())

        assertEquals("on", loaded.image.snapshot.effective.displayHud)
        assertEquals(8, loaded.image.snapshot.revision)
    }

    @Test
    fun authorityPlaneRoundTripsThroughRealCausalPreferencesAndV1MigrationSetsItAbsent() {
        val base = InitialSnapshots.play()
        val authority = "{\"schema\":\"phosphor.nexus.authority/1\",\"payload\":{\"grants\":[],\"revoked_token_ids\":[],\"consumed_idempotency_keys\":[],\"consumed_nonces\":[]},\"checksum_sha256\":\"${"0".repeat(64)}\"}"
        val image = sampleImage(base, mode = "on", revision = 8).copy(authorityPlane = authority)
        val prefs = InMemoryPreferenceBoundary()

        assertEquals(PhosphorStorePersistenceResult.Saved, CausalStatePreferences(prefs, base).save(image))
        val loaded = assertIs<PhosphorStoreLoadResult.CausalImage>(CausalStatePreferences(prefs, base).load())
        assertEquals(authority, loaded.image.authorityPlane)

        val v1Root = JSONObject(HudCausalEnvelopeCodec.encode(image)).put("schema", HudCausalEnvelopeCodec.LEGACY_SCHEMA).apply {
            getJSONObject("payload").remove("authority_plane")
        }
        v1Root.put("checksum_sha256", sha256(HudCausalEnvelopeCodec.canonicalJson(v1Root.getJSONObject("payload"))))
        val v1 = v1Root.toString()
        val migrated = HudCausalEnvelopeCodec.migrateLegacyV1(v1, base)
        assertEquals(null, HudCausalEnvelopeCodec.decode(migrated, base).authorityPlane)
    }

    @Test
    fun loadUpgradesALegacyV1EnvelopeInPlaceInsteadOfLatchingReadOnly() {
        // Without this path, any device that ever ran a schema-v1 build loads
        // CorruptEnvelope forever: the store stays read-only, the HUD silently falls back,
        // and nothing short of clearing app data recovers it.
        val base = InitialSnapshots.play()
        val image = sampleImage(base, mode = "auto", revision = 5)
        val legacy = legacyV1EnvelopeOf(image)
        val prefs = InMemoryPreferenceBoundary(
            mapOf(CausalStatePreferences.KEY_CAUSAL_ENVELOPE to legacy),
        )

        val loaded = assertIs<PhosphorStoreLoadResult.CausalImage>(
            CausalStatePreferences(prefs, base).load(),
        )
        assertEquals("auto", loaded.image.snapshot.effective.displayHud)

        // The upgrade is durable: the stored envelope is now v2, so the next launch takes
        // the ordinary path rather than migrating again.
        val stored = JSONObject(prefs.getString(CausalStatePreferences.KEY_CAUSAL_ENVELOPE)!!)
        assertEquals(HudCausalEnvelopeCodec.SCHEMA, stored.getString("schema"))
        assertIs<PhosphorStoreLoadResult.CausalImage>(CausalStatePreferences(prefs, base).load())
    }

    @Test
    fun aTamperedLegacyEnvelopeIsStillRefusedByLoadRatherThanMigrated() {
        // Migration must not become a laundering path: re-checksumming forged content
        // would turn a tampered v1 envelope into a trusted v2 one.
        val base = InitialSnapshots.play()
        val image = sampleImage(base, mode = "on", revision = 2)
        val tampered = JSONObject(legacyV1EnvelopeOf(image)).apply {
            getJSONObject("payload").put("revision", 99)
        }.toString()
        val prefs = InMemoryPreferenceBoundary(
            mapOf(CausalStatePreferences.KEY_CAUSAL_ENVELOPE to tampered),
        )

        val result = assertIs<PhosphorStoreLoadResult.CorruptEnvelope>(
            CausalStatePreferences(prefs, base).load(),
        )
        assertTrue(result.refusal.fix.isNotBlank())
        // The original bytes survive for explicit repair.
        assertEquals(tampered, prefs.getString(CausalStatePreferences.KEY_CAUSAL_ENVELOPE))
    }

    /** A schema-v1 envelope: no authority plane, checksum bound over the v1 payload. */
    private fun legacyV1EnvelopeOf(image: PhosphorStoreImage): String {
        val root = JSONObject(HudCausalEnvelopeCodec.encode(image))
            .put("schema", HudCausalEnvelopeCodec.LEGACY_SCHEMA)
        root.getJSONObject("payload").remove("authority_plane")
        root.put(
            "checksum_sha256",
            sha256(HudCausalEnvelopeCodec.canonicalJson(root.getJSONObject("payload"))),
        )
        return root.toString()
    }

    @Test
    fun legacyBootstrapPreservesRawMigrationInputsForStorePrecedence() {
        listOf(0, 1, 2).forEach { legacy ->
            val result = CausalStatePreferences(
                InMemoryPreferenceBoundary(
                    linkedMapOf(
                        CausalStatePreferences.KEY_LEGACY_HUD_MODE to legacy,
                        CausalStatePreferences.KEY_LEGACY_NERD_HUD to true,
                    )
                ),
                InitialSnapshots.play(),
            ).load()
            val bootstrap = assertIs<PhosphorStoreLoadResult.LegacyBootstrap>(result)
            assertEquals(legacy.toString(), bootstrap.hudModeRaw)
            assertEquals("true", bootstrap.nerdHudRaw)
        }
    }

    @Test
    fun nerdHudAndAbsentFallbackRemainRawBootstrapInputsOnly() {
        val nerdTrue = assertIs<PhosphorStoreLoadResult.LegacyBootstrap>(
            CausalStatePreferences(
                InMemoryPreferenceBoundary(linkedMapOf(CausalStatePreferences.KEY_LEGACY_NERD_HUD to true)),
                InitialSnapshots.play(),
            ).load()
        )
        assertEquals(null, nerdTrue.hudModeRaw)
        assertEquals("true", nerdTrue.nerdHudRaw)

        val absent = assertIs<PhosphorStoreLoadResult.LegacyBootstrap>(
            CausalStatePreferences(InMemoryPreferenceBoundary(), InitialSnapshots.play()).load()
        )
        assertEquals(null, absent.hudModeRaw)
        assertEquals(null, absent.nerdHudRaw)
    }

    @Test
    fun malformedLegacyTypesRemainPresentAndCannotSilentlyFallBack() {
        val bootstrap = assertIs<PhosphorStoreLoadResult.LegacyBootstrap>(
            CausalStatePreferences(
                InMemoryPreferenceBoundary(
                    linkedMapOf(
                        CausalStatePreferences.KEY_LEGACY_HUD_MODE to true,
                        CausalStatePreferences.KEY_LEGACY_NERD_HUD to 1,
                    ),
                ),
                InitialSnapshots.play(),
            ).load(),
        )
        assertEquals(CausalStatePreferences.INVALID_LEGACY_TYPE, bootstrap.hudModeRaw)
        assertEquals(CausalStatePreferences.INVALID_LEGACY_TYPE, bootstrap.nerdHudRaw)
    }

    @Test
    fun malformedPrivateEnvelopeTypeIsCorruptAndNeverFallsBackToLegacy() {
        val result = CausalStatePreferences(
            InMemoryPreferenceBoundary(
                linkedMapOf(
                    CausalStatePreferences.KEY_CAUSAL_ENVELOPE to 7,
                    CausalStatePreferences.KEY_LEGACY_HUD_MODE to 0,
                ),
            ),
            InitialSnapshots.play(),
        ).load()
        val corrupt = assertIs<PhosphorStoreLoadResult.CorruptEnvelope>(result)
        assertTrue(corrupt.refusal.fix.contains("Do not fall back silently"))
    }

    @Test
    fun saveUsesSingleCommitForEnvelopeAndRollbackHudMode() {
        val prefs = InMemoryPreferenceBoundary()
        val result = CausalStatePreferences(prefs, InitialSnapshots.play()).save(sampleImage(InitialSnapshots.play(), mode = "off", revision = 4))

        assertEquals(PhosphorStorePersistenceResult.Saved, result)
        assertEquals(1, prefs.commitCount)
        assertEquals(2, prefs.snapshot()[CausalStatePreferences.KEY_LEGACY_HUD_MODE])
        val encoded = prefs.snapshot()[CausalStatePreferences.KEY_CAUSAL_ENVELOPE] as String
        assertEquals("off", HudCausalEnvelopeCodec.decode(encoded, InitialSnapshots.play()).snapshot.effective.displayHud)
    }

    @Test
    fun splitStorageLoadsPortableHudWithoutRestoringCausalRuntimeState() {
        val causal = InMemoryPreferenceBoundary()
        val portable = InMemoryPreferenceBoundary(
            linkedMapOf(CausalStatePreferences.KEY_LEGACY_HUD_MODE to 1),
        )

        val loaded = CausalStatePreferences(causal, InitialSnapshots.play(), portable).load()

        val bootstrap = assertIs<PhosphorStoreLoadResult.LegacyBootstrap>(loaded)
        assertEquals("1", bootstrap.hudModeRaw)
        assertEquals(null, bootstrap.nerdHudRaw)
    }

    @Test
    fun splitStorageKeepsEnvelopePrivateAndMirrorsPortableHudMode() {
        val base = InitialSnapshots.play()
        val causal = InMemoryPreferenceBoundary()
        val portable = InMemoryPreferenceBoundary()

        val result = CausalStatePreferences(causal, base, portable)
            .save(sampleImage(base, mode = "on", revision = 4))

        assertEquals(PhosphorStorePersistenceResult.Saved, result)
        assertTrue(causal.snapshot().containsKey(CausalStatePreferences.KEY_CAUSAL_ENVELOPE))
        assertFalse(portable.snapshot().containsKey(CausalStatePreferences.KEY_CAUSAL_ENVELOPE))
        assertEquals(0, causal.snapshot()[CausalStatePreferences.KEY_LEGACY_HUD_MODE])
        assertEquals(0, portable.snapshot()[CausalStatePreferences.KEY_LEGACY_HUD_MODE])
    }

    @Test
    fun splitStoragePortableFailureRollsBackBothFiles() {
        val base = InitialSnapshots.play()
        val priorEnvelope = HudCausalEnvelopeCodec.encode(sampleImage(base, mode = "auto", revision = 3))
        val causal = InMemoryPreferenceBoundary(
            linkedMapOf(
                CausalStatePreferences.KEY_CAUSAL_ENVELOPE to priorEnvelope,
                CausalStatePreferences.KEY_LEGACY_HUD_MODE to 1,
            ),
        )
        val portable = InMemoryPreferenceBoundary(
            linkedMapOf(CausalStatePreferences.KEY_LEGACY_HUD_MODE to 1),
            commitSucceeds = false,
        )

        val result = CausalStatePreferences(causal, base, portable)
            .save(sampleImage(base, mode = "off", revision = 4))

        assertIs<PhosphorStorePersistenceResult.Failed>(result)
        assertEquals(priorEnvelope, causal.snapshot()[CausalStatePreferences.KEY_CAUSAL_ENVELOPE])
        assertEquals(1, causal.snapshot()[CausalStatePreferences.KEY_LEGACY_HUD_MODE])
        assertEquals(1, portable.snapshot()[CausalStatePreferences.KEY_LEGACY_HUD_MODE])
    }

    @Test
    fun commitFailureReturnsFixBearingFailure() {
        val result = CausalStatePreferences(InMemoryPreferenceBoundary(commitSucceeds = false), InitialSnapshots.play())
            .save(sampleImage(InitialSnapshots.play()))

        val failed = assertIs<PhosphorStorePersistenceResult.Failed>(result)
        assertTrue(failed.refusal.fix.isNotBlank())
    }

    @Test
    fun commitFailureRestoresPriorEnvelopeAndHudModeAfterAndroidLikeMemoryMutation() {
        val base = InitialSnapshots.play()
        val priorEnvelope = HudCausalEnvelopeCodec.encode(sampleImage(base, mode = "on", revision = 4))
        val prefs = InMemoryPreferenceBoundary(
            initial = linkedMapOf(
                CausalStatePreferences.KEY_CAUSAL_ENVELOPE to priorEnvelope,
                CausalStatePreferences.KEY_LEGACY_HUD_MODE to 0,
            ),
            commitSucceeds = false,
        )

        val result = CausalStatePreferences(prefs, base).save(sampleImage(base, mode = "off", revision = 5))

        assertIs<PhosphorStorePersistenceResult.Failed>(result)
        assertTrue(prefs.mutatedBeforeFailedCommit)
        assertEquals(priorEnvelope, prefs.snapshot()[CausalStatePreferences.KEY_CAUSAL_ENVELOPE])
        assertEquals(0, prefs.snapshot()[CausalStatePreferences.KEY_LEGACY_HUD_MODE])
    }

    @Test
    fun compiledDistributionAndBuildMismatchIsRejected() {
        val fortress = InitialSnapshots.fortress()
        val encoded = HudCausalEnvelopeCodec.encode(sampleImage(fortress))

        val error = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(encoded, InitialSnapshots.play())
        }

        assertEquals("compiled_identity_mismatch", error.error)
        assertTrue(error.fix.isNotBlank())
    }

    @Test
    fun changedAcknowledgementRestartReplayRoundTripsDeltaOrderingAndProvenanceIdentity() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base, mode = "on", revision = 7, oldMode = "off", changed = true, receiptOrdinal = 7, idempotencyOrdinal = 11)

        val decoded = HudCausalEnvelopeCodec.decode(HudCausalEnvelopeCodec.encode(image), base)
        val replay = decoded.idempotency.records.single().acknowledgement

        assertTrue(replay.changed)
        assertEquals(7, replay.revision)
        assertEquals(7, replay.sequence)
        assertEquals(StringStateValue("on"), replay.effectiveValue)
        val delta = replay.delta!!
        assertEquals(7, delta.revision)
        assertEquals(7, delta.sequence)
        val change = delta.changes.single()
        assertEquals(StateField.DISPLAY_HUD, change.field)
        assertEquals(StringStateValue("off"), change.oldValue)
        assertEquals(StringStateValue("on"), change.newValue)
        assertSame(replay.provenance, delta.provenance)
        assertSame(replay.provenance, change.provenance)
        assertSame(replay.provenance, decoded.snapshot.provenance.getValue(StateField.DISPLAY_HUD))
        assertSame(replay.provenance, decoded.audit.records.single { it.receiptId == replay.receiptId }.provenance)
    }

    @Test
    fun refusalAuditRoundTripsRefusalDetailsWithoutInventingProvenance() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base).copy(
            audit = AuditIndexSnapshot(
                AuditRetentionPolicy(128, IDEMPOTENCY_TTL_MILLIS),
                2000,
                FrozenList.copyOf(listOf(
                    AuditRecord(
                        receiptId = "hud-9",
                        kind = AuditKind.IDEMPOTENCY_CONFLICT,
                        wallTimeMillis = 2000,
                        actionType = ActionType.SET_DISPLAY_HUD,
                        fields = FrozenSet.copyOf(setOf(StateField.DISPLAY_HUD)),
                        provenance = null,
                        refusal = Refusal(
                            code = RefusalCode.IDEMPOTENCY_CONFLICT,
                            fix = "Use a fresh idempotency key for a different HUD mode.",
                            currentRevision = 3,
                            originalReceiptId = "hud-3",
                        ),
                    ),
                )),
            ),
            nextReceiptOrdinal = 10,
        )

        val decoded = HudCausalEnvelopeCodec.decode(HudCausalEnvelopeCodec.encode(image), base)
        val refusalAudit = decoded.audit.records.single()

        assertEquals(AuditKind.IDEMPOTENCY_CONFLICT, refusalAudit.kind)
        assertEquals(null, refusalAudit.provenance)
        val refusal = refusalAudit.refusal!!
        assertEquals(RefusalCode.IDEMPOTENCY_CONFLICT, refusal.code)
        assertEquals(3, refusal.currentRevision)
        assertEquals("hud-3", refusal.originalReceiptId)
        assertTrue(refusal.fix.contains("fresh idempotency"))
    }

    @Test
    fun countersRoundTripAndPreventOrdinalReuse() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base, receiptOrdinal = 5, idempotencyOrdinal = 8).copy(
            nextReceiptOrdinal = 6,
            nextIdempotencyOrdinal = 9,
        )

        val decoded = HudCausalEnvelopeCodec.decode(HudCausalEnvelopeCodec.encode(image), base)

        assertEquals(6, decoded.nextReceiptOrdinal)
        assertEquals(9, decoded.nextIdempotencyOrdinal)
    }

    @Test
    fun invalidCounterReuseIsRejectedOnDecode() {
        val base = InitialSnapshots.play()
        val encoded = HudCausalEnvelopeCodec.encode(sampleImage(base, receiptOrdinal = 5, idempotencyOrdinal = 8).copy(
            nextReceiptOrdinal = 6,
            nextIdempotencyOrdinal = 9,
        ))
        val root = JSONObject(encoded)
        val payload = root.getJSONObject("payload")
        payload.put("next_idempotency_ordinal", 8)
        root.put("checksum_sha256", sha256(HudCausalEnvelopeCodec.canonicalJson(payload)))

        val error = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(root.toString(), base)
        }

        assertEquals("counter_reuse", error.error)
        assertTrue(error.fix.contains("strictly greater"))
    }

    @Test
    fun multipleHistoricalProvenanceStampsRemainDistinctButSharedByReceipt() {
        val base = InitialSnapshots.play()
        val first = sampleImage(base, mode = "on", revision = 3, oldMode = "off", changed = true, receiptOrdinal = 3, idempotencyOrdinal = 3)
        val second = sampleImage(base, mode = "off", revision = 4, oldMode = "on", changed = true, receiptOrdinal = 4, idempotencyOrdinal = 4)
        val image = second.copy(
            audit = second.audit.copy(records = FrozenList.copyOf(first.audit.records + second.audit.records)),
            idempotency = second.idempotency.copy(records = FrozenList.copyOf(first.idempotency.records + second.idempotency.records)),
            nextReceiptOrdinal = 5,
            nextIdempotencyOrdinal = 5,
        )

        val decoded = HudCausalEnvelopeCodec.decode(HudCausalEnvelopeCodec.encode(image), base)
        val firstAck = decoded.idempotency.records.first { it.key == "idem-3" }.acknowledgement
        val secondAck = decoded.idempotency.records.first { it.key == "idem-4" }.acknowledgement

        assertTrue(firstAck.provenance !== secondAck.provenance)
        assertSame(firstAck.provenance, decoded.audit.records.first { it.receiptId == "hud-3" }.provenance)
        assertSame(secondAck.provenance, decoded.audit.records.first { it.receiptId == "hud-4" }.provenance)
        assertSame(secondAck.provenance, decoded.snapshot.provenance.getValue(StateField.DISPLAY_HUD))
    }

    @Test
    fun receiptCounterValidationSeesPrunedAuditButRetainedIdempotencyReceipt() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base, receiptOrdinal = 12, idempotencyOrdinal = 2)

        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            image.copy(
                audit = AuditIndexSnapshot(AuditRetentionPolicy(128, IDEMPOTENCY_TTL_MILLIS), 1012, FrozenList.copyOf(emptyList())),
                nextReceiptOrdinal = 12,
                nextIdempotencyOrdinal = 3,
            )
        }

        assertTrue(error.message.orEmpty().contains("next receipt ordinal"))
    }

    @Test
    fun checksumValidMalformedNestedPayloadLoadsAsCorruptEnvelope() {
        val base = InitialSnapshots.play()
        val root = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        val payload = root.getJSONObject("payload")
        payload.put("audit", "not-an-object")
        root.put("checksum_sha256", sha256(HudCausalEnvelopeCodec.canonicalJson(payload)))
        val prefs = InMemoryPreferenceBoundary(mapOf(CausalStatePreferences.KEY_CAUSAL_ENVELOPE to root.toString()))

        val result = CausalStatePreferences(prefs, base).load()

        val corrupt = assertIs<PhosphorStoreLoadResult.CorruptEnvelope>(result)
        assertTrue(corrupt.refusal.fix.contains("Preserve"))
    }

    @Test
    fun checksumValidMalformedPayloadTypeLoadsAsCorruptEnvelope() {
        val base = InitialSnapshots.play()
        val root = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        val payload = root.getJSONObject("payload")
        payload.getJSONObject("idempotency").getJSONArray("records").getJSONObject(0).put("acknowledgement", 7)
        root.put("checksum_sha256", sha256(HudCausalEnvelopeCodec.canonicalJson(payload)))
        val prefs = InMemoryPreferenceBoundary(mapOf(CausalStatePreferences.KEY_CAUSAL_ENVELOPE to root.toString()))

        val result = CausalStatePreferences(prefs, base).load()

        assertIs<PhosphorStoreLoadResult.CorruptEnvelope>(result)
    }

    @Test
    fun exactJsonKeySetsRejectRootExtensionsAndNestedExtensions() {
        val base = InitialSnapshots.play()
        val rootExtension = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
            .put("extension", true)

        val rootError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(rootExtension.toString(), base)
        }
        assertEquals("field_invalid", rootError.error)

        val nested = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        val payload = nested.getJSONObject("payload")
        payload.getJSONArray("provenance_pool").getJSONObject(0).put("extension", "x")
        updateChecksum(nested)

        val nestedError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(nested.toString(), base)
        }
        assertEquals("field_invalid", nestedError.error)
    }

    @Test
    fun coerciveScalarTypesAreRejectedEvenWithValidChecksum() {
        val base = InitialSnapshots.play()
        val stringRevision = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        stringRevision.getJSONObject("payload").put("revision", "3")
        updateChecksum(stringRevision)

        val revisionError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(stringRevision.toString(), base)
        }
        assertEquals("field_invalid", revisionError.error)

        val stringChanged = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        stringChanged.getJSONObject("payload")
            .getJSONObject("idempotency")
            .getJSONArray("records")
            .getJSONObject(0)
            .getJSONObject("acknowledgement")
            .put("changed", "false")
        updateChecksum(stringChanged)

        val changedError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(stringChanged.toString(), base)
        }
        assertEquals("field_invalid", changedError.error)
    }

    @Test
    fun decimalNumericPayloadFieldsAreRejectedBeforeCanonicalChecksumValidation() {
        val base = InitialSnapshots.play()
        val decimalRevision = envelopeWithRawPayloadMutation(
            HudCausalEnvelopeCodec.encode(sampleImage(base)),
            "\"revision\":3",
            "\"revision\":3.0",
        )

        val revisionError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(decimalRevision, base)
        }
        assertEquals("field_invalid", revisionError.error)

        val decimalPolicyMaximumRecords = envelopeWithRawPayloadMutation(
            HudCausalEnvelopeCodec.encode(sampleImage(base)),
            "\"policy_maximum_records\":128",
            "\"policy_maximum_records\":128.0",
        )

        val policyError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(decimalPolicyMaximumRecords, base)
        }
        assertEquals("field_invalid", policyError.error)
    }

    @Test
    fun orphanProvenanceAndSnapshotReceiptCounterReuseAreRejected() {
        val base = InitialSnapshots.play()
        val orphan = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        val extra = JSONObject(orphan.getJSONObject("payload").getJSONArray("provenance_pool").getJSONObject(0).toString())
            .put("receipt_id", "hud-99")
        orphan.getJSONObject("payload").getJSONArray("provenance_pool").put(extra)
        updateChecksum(orphan)

        val orphanError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(orphan.toString(), base)
        }
        assertEquals("orphan_provenance", orphanError.error)

        val snapshotReuse = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base, receiptOrdinal = 12).copy(nextReceiptOrdinal = 13)))
        snapshotReuse.getJSONObject("payload").put("next_receipt_ordinal", 12)
        updateChecksum(snapshotReuse)

        val counterError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(snapshotReuse.toString(), base)
        }
        assertEquals("counter_reuse", counterError.error)
    }

    @Test
    fun auditReceiptIdsDoNotSatisfyProvenancePoolReferencesEvenWithValidChecksum() {
        val base = InitialSnapshots.play()
        val refusalAlias = JSONObject(HudCausalEnvelopeCodec.encode(refusalAliasImage(base, AuditKind.REFUSAL, RefusalCode.IDEMPOTENCY_CONFLICT)))
        val refusalExtra = JSONObject(refusalAlias.getJSONObject("payload").getJSONArray("provenance_pool").getJSONObject(0).toString())
            .put("receipt_id", "hud-99")
        refusalAlias.getJSONObject("payload").getJSONArray("provenance_pool").put(refusalExtra)
        updateChecksum(refusalAlias)

        val refusalError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(refusalAlias.toString(), base)
        }
        assertEquals("orphan_provenance", refusalError.error)

        val conflictAlias = JSONObject(HudCausalEnvelopeCodec.encode(refusalAliasImage(base, AuditKind.IDEMPOTENCY_CONFLICT, RefusalCode.IDEMPOTENCY_CONFLICT)))
        val conflictExtra = JSONObject(conflictAlias.getJSONObject("payload").getJSONArray("provenance_pool").getJSONObject(0).toString())
            .put("receipt_id", "hud-100")
        conflictAlias.getJSONObject("payload").getJSONArray("provenance_pool").put(conflictExtra)
        updateChecksum(conflictAlias)

        val conflictError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(conflictAlias.toString(), base)
        }
        assertEquals("orphan_provenance", conflictError.error)
    }

    @Test
    fun decodeRejectsAlternativeCanonicalByteStreamsEvenWithValidChecksum() {
        val base = InitialSnapshots.play()
        val encoded = HudCausalEnvelopeCodec.encode(sampleImage(base))

        assertEquals(encoded, HudCausalEnvelopeCodec.encode(HudCausalEnvelopeCodec.decode(encoded, base)))

        val whitespace = encoded.replaceFirst(":", " :")
        assertTrue(whitespace != encoded)
        val whitespaceError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(whitespace, base)
        }
        assertEquals("noncanonical_envelope", whitespaceError.error)

        val root = JSONObject(encoded)
        val payloadBytes = root.getJSONObject("payload").toString()
        val schemaBytes = "\"schema\":\"${root.getString("schema")}\""
        val checksumBytes = "\"checksum_sha256\":\"${root.getString("checksum_sha256")}\""
        val reordered = listOf(
            "{$schemaBytes,$checksumBytes,\"payload\":$payloadBytes}",
            "{\"payload\":$payloadBytes,$schemaBytes,$checksumBytes}",
            "{$checksumBytes,\"payload\":$payloadBytes,$schemaBytes}",
        ).first { it != encoded }
        assertTrue(reordered != encoded)
        val reorderError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(reordered, base)
        }
        assertEquals("noncanonical_envelope", reorderError.error)
    }

    @Test
    fun canonicalSerializerIsIndependentOfJSONObjectInsertionOrderAtEveryObjectLayer() {
        val first = JSONObject()
            .put("z", JSONObject().put("second", 2).put("first", 1))
            .put("a", JSONArray().put(JSONObject().put("beta", true).put("alpha", "line\nvalue")))
        val second = JSONObject()
            .put("a", JSONArray().put(JSONObject().put("alpha", "line\nvalue").put("beta", true)))
            .put("z", JSONObject().put("first", 1).put("second", 2))

        val firstCanonical = HudCausalEnvelopeCodec.canonicalJson(first)
        val secondCanonical = HudCausalEnvelopeCodec.canonicalJson(second)

        assertEquals(firstCanonical, secondCanonical)
        assertEquals(
            "{\"a\":[{\"alpha\":\"line\\nvalue\",\"beta\":true}],\"z\":{\"first\":1,\"second\":2}}",
            firstCanonical,
        )
    }

    @Test
    fun retainedAcknowledgementDeltaOrderingAndHudOnlyEffectsAreValidated() {
        val base = InitialSnapshots.play()
        val newerAck = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        newerAck.getJSONObject("payload")
            .getJSONObject("idempotency")
            .getJSONArray("records")
            .getJSONObject(0)
            .getJSONObject("acknowledgement")
            .put("revision", 4)
        updateChecksum(newerAck)

        val orderingError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(newerAck.toString(), base)
        }
        assertEquals("ordering_invalid", orderingError.error)

        val effectImage = sampleImage(base, changed = true).let { image ->
            val stamp = image.snapshot.provenance.getValue(StateField.DISPLAY_HUD)
            val ack = image.idempotency.records.single().acknowledgement.copy(
                effects = frozenListOf(InertEffectDescription(EffectKind.PERSIST_STATE, "persist HUD state", stamp)),
            )
            image.copy(
                idempotency = image.idempotency.copy(records = FrozenList.copyOf(listOf(image.idempotency.records.single().copy(acknowledgement = ack))))
            )
        }

        val effectError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.encode(effectImage)
        }
        assertEquals("unsupported_image", effectError.error)
    }

    @Test
    fun strictHudReducerAuditGrammarAllowsOnlyActionlessLifecycleRecords() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base).let { image ->
            image.copy(
                audit = image.audit.copy(records = FrozenList.copyOf(listOf(
                    image.audit.records.single().copy(
                        kind = AuditKind.AUTHENTICATION,
                        actionType = null,
                        fields = FrozenSet.copyOf(emptySet()),
                        provenance = null,
                        refusal = null,
                    )
                )))
            )
        }

        val decoded = HudCausalEnvelopeCodec.decode(HudCausalEnvelopeCodec.encode(image), base)

        assertEquals(AuditKind.AUTHENTICATION, decoded.audit.records.single().kind)
        assertEquals(null, decoded.audit.records.single().actionType)
        assertTrue(decoded.audit.records.single().fields.isEmpty())

        val malformed = image.copy(
            audit = image.audit.copy(records = FrozenList.copyOf(listOf(
                image.audit.records.single().copy(actionType = ActionType.SET_DISPLAY_HUD),
            )))
        )
        val error = kotlin.test.assertFailsWith<HudCausalCodecException> { HudCausalEnvelopeCodec.encode(malformed) }
        assertEquals("unsupported_image", error.error)
        assertTrue(error.message.contains("actionless") || error.fix.contains("actionless") || error.fix.contains("lifecycle"))
    }

    @Test
    fun exactReducerPoliciesAreRequiredOnEncodeAndDecode() {
        val base = InitialSnapshots.play()
        val badEncode = sampleImage(base).let { image ->
            image.copy(audit = image.audit.copy(policy = AuditRetentionPolicy(128, Long.MAX_VALUE)))
        }

        val encodeError = kotlin.test.assertFailsWith<HudCausalCodecException> { HudCausalEnvelopeCodec.encode(badEncode) }
        assertEquals("field_invalid", encodeError.error)

        val badDecode = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        badDecode.getJSONObject("payload").getJSONObject("audit").put("policy_maximum_age_millis", Long.MAX_VALUE)
        updateChecksum(badDecode)

        val decodeError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(badDecode.toString(), base)
        }
        assertEquals("field_invalid", decodeError.error)
    }

    @Test
    fun retainedPrincipalsMustBeExactLocalHudWritersWithoutSessions() {
        val base = InitialSnapshots.play()
        val remoteStamp = ProvenanceStamp(
            receiptId = "hud-3",
            writer = PrincipalId(PrincipalKind.HUMAN, "remote-human"),
            reason = "set HUD auto",
            transport = Transport.UI,
            sessionId = null,
            monotonicMillis = 3,
            wallTimeMillis = 1003,
        )
        val image = sampleImage(base).copy(
            snapshot = sampleImage(base).snapshot.copy(provenance = frozenMapOf(StateField.DISPLAY_HUD to remoteStamp)),
            audit = sampleImage(base).audit.copy(records = FrozenList.copyOf(listOf(sampleImage(base).audit.records.single().copy(provenance = remoteStamp)))),
            idempotency = sampleImage(base).idempotency.copy(records = FrozenList.copyOf(listOf(sampleImage(base).idempotency.records.single().copy(principal = remoteStamp.writer, acknowledgement = sampleImage(base).idempotency.records.single().acknowledgement.copy(provenance = remoteStamp))))),
        )

        val error = kotlin.test.assertFailsWith<HudCausalCodecException> { HudCausalEnvelopeCodec.encode(image) }

        assertEquals("unsupported_image", error.error)
        assertTrue(error.fix.contains("local HUD"))
    }

    @Test
    fun saveRejectsNonHudSnapshotPlaneThatWouldBeDroppedAgainstCompiledBase() {
        val base = InitialSnapshots.play()
        val drifted = sampleImage(base).let { image ->
            image.copy(snapshot = image.snapshot.copy(effective = image.snapshot.effective.copy(scopeMode = base.effective.scopeMode + 1)))
        }

        val result = CausalStatePreferences(InMemoryPreferenceBoundary(), base).save(drifted)

        val failed = assertIs<PhosphorStorePersistenceResult.Failed>(result)
        assertTrue(failed.refusal.fix.contains("compiled base"))
    }

    @Test
    fun saveRejectsNonHudProvenanceEvenWhenSnapshotValuesMatchBase() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base).let { image ->
            val hudStamp = image.snapshot.provenance.getValue(StateField.DISPLAY_HUD)
            val nonHudStamp = hudStamp.copy(receiptId = "scope-external", reason = "non-HUD provenance must not be retained")
            image.copy(snapshot = image.snapshot.copy(provenance = frozenMapOf(
                StateField.DISPLAY_HUD to hudStamp,
                StateField.SCOPE_MODE to nonHudStamp,
            )))
        }

        val result = CausalStatePreferences(InMemoryPreferenceBoundary(), base).save(image)

        val failed = assertIs<PhosphorStorePersistenceResult.Failed>(result)
        assertTrue(failed.refusal.fix.contains("DISPLAY_HUD provenance"))
    }

    @Test
    fun decodeRejectsEnvelopeWithRetainedEffectEvenWhenChecksumValid() {
        val base = InitialSnapshots.play()
        val withEffect = JSONObject(HudCausalEnvelopeCodec.encode(sampleImage(base)))
        val receiptId = withEffect.getJSONObject("payload").getString("snapshot_provenance_receipt_id")
        withEffect.getJSONObject("payload")
            .getJSONObject("idempotency")
            .getJSONArray("records")
            .getJSONObject(0)
            .getJSONObject("acknowledgement")
            .getJSONArray("effects")
            .put(JSONObject().put("kind", "persist_state").put("description", "persist HUD state").put("provenance_receipt_id", receiptId))
        updateChecksum(withEffect)

        val decodeError = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode(withEffect.toString(), base)
        }

        assertEquals("field_invalid", decodeError.error)
    }

    @Test
    fun unsupportedDesiredEffectiveHudDivergenceIsRejectedBeforeSave() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base).let { image ->
            image.copy(snapshot = image.snapshot.copy(desired = image.snapshot.desired.copy(displayHud = "on")))
        }

        val result = CausalStatePreferences(InMemoryPreferenceBoundary(), base).save(image)

        val failed = assertIs<PhosphorStorePersistenceResult.Failed>(result)
        assertTrue(failed.refusal.fix.contains("reconciled"))
    }

    @Test
    fun unsupportedNonHudIdempotencyActionIsRejectedBeforeSave() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base).let { image ->
            val stamp = image.snapshot.provenance.getValue(StateField.DISPLAY_HUD)
            val acknowledgement = ActionAcknowledgement(
                receiptId = stamp.receiptId,
                actionType = ActionType.TOGGLE_PLAYBACK,
                changed = false,
                revision = image.snapshot.revision,
                sequence = image.snapshot.sequence,
                effectiveValue = BooleanStateValue(false),
                provenance = stamp,
                delta = null,
                effects = frozenListOf(),
            )
            val nonHud = IdempotencyRecord(
                principal = stamp.writer,
                key = "non-hud",
                action = TogglePlayback,
                canonicalPayload = TogglePlayback.canonicalPayload(),
                acknowledgement = acknowledgement,
                acceptedWallTimeMillis = stamp.wallTimeMillis,
                expiresWallTimeMillis = stamp.wallTimeMillis + IDEMPOTENCY_TTL_MILLIS,
                ordinal = 50,
            )
            image.copy(
                idempotency = image.idempotency.copy(records = FrozenList.copyOf(listOf(nonHud))),
                nextIdempotencyOrdinal = 51,
            )
        }

        val result = CausalStatePreferences(InMemoryPreferenceBoundary(), base).save(image)

        val failed = assertIs<PhosphorStorePersistenceResult.Failed>(result)
        assertTrue(failed.refusal.fix.contains("HUD-slice"))
    }

    @Test
    fun duplicateReceiptIdWithDifferentProvenanceObjectIsRejected() {
        val base = InitialSnapshots.play()
        val image = sampleImage(base).let { image ->
            val duplicate = image.snapshot.provenance.getValue(StateField.DISPLAY_HUD).copy()
            image.copy(
                audit = image.audit.copy(records = FrozenList.copyOf(listOf(
                    image.audit.records.single().copy(provenance = duplicate),
                ))),
            )
        }

        val error = kotlin.test.assertFailsWith<HudCausalCodecException> { HudCausalEnvelopeCodec.encode(image) }

        assertEquals("duplicate_provenance_object", error.error)
    }

    @Test
    fun maximumLegalRetentionFitsThePrivateEnvelopeBound() {
        val base = InitialSnapshots.play()
        val images = (1L..128L).map { ordinal ->
            sampleImage(
                base,
                mode = if (ordinal % 2L == 0L) "off" else "on",
                revision = ordinal,
                oldMode = if (ordinal % 2L == 0L) "on" else "off",
                changed = true,
                receiptOrdinal = ordinal,
                idempotencyOrdinal = ordinal,
            )
        }
        val latest = images.last()
        val maximum = latest.copy(
            audit = latest.audit.copy(records = FrozenList.copyOf(images.flatMap { it.audit.records })),
            idempotency = latest.idempotency.copy(records = FrozenList.copyOf(images.flatMap { it.idempotency.records })),
            nextReceiptOrdinal = 129L,
            nextIdempotencyOrdinal = 129L,
        )

        val encoded = HudCausalEnvelopeCodec.encode(maximum)

        assertTrue(encoded.toByteArray(Charsets.UTF_8).size < HudCausalEnvelopeCodec.MAX_BYTES)
        val decoded = HudCausalEnvelopeCodec.decode(encoded, base)
        assertEquals(128, decoded.audit.records.size)
        assertEquals(128, decoded.idempotency.records.size)
    }

    @Test
    fun overLimitEnvelopeIsRejectedBeforeJsonParsing() {
        val error = kotlin.test.assertFailsWith<HudCausalCodecException> {
            HudCausalEnvelopeCodec.decode("x".repeat(HudCausalEnvelopeCodec.MAX_BYTES + 1), InitialSnapshots.play())
        }
        assertEquals("envelope_too_large", error.error)
    }

    @Test
    fun privateCausalKeysAreOutsideSettingsArchiveAllowlistEvidence() {
        val preferences = mapOf(
            CausalStatePreferences.KEY_CAUSAL_ENVELOPE to HudCausalEnvelopeCodec.encode(sampleImage(InitialSnapshots.play())),
            CausalStatePreferences.KEY_LEGACY_HUD_MODE to 0,
            "__phosphor_private.pending_settings_import_hud" to "v1:${"d".repeat(64)}:0:operation",
        )

        val exported = SettingsArchive.export(
            sourcePackage = "dev.phosphor.mobil3",
            sourceVersion = "2.0.0",
            sourceDistribution = "local_dev",
            exportedAt = "2026-07-26T07:40:00Z",
            allPreferences = preferences,
        )

        assertEquals(listOf(CausalStatePreferences.KEY_LEGACY_HUD_MODE), exported.exportedKeys)
        assertEquals(
            listOf(
                CausalStatePreferences.KEY_CAUSAL_ENVELOPE,
                "__phosphor_private.pending_settings_import_hud",
            ),
            exported.skippedKeys,
        )
        assertFalse(exported.json.contains(CausalStatePreferences.KEY_CAUSAL_ENVELOPE))
        assertFalse(exported.json.contains("pending_settings_import_hud"))
        assertTrue(CausalStatePreferences.KEY_CAUSAL_ENVELOPE.startsWith("__phosphor_private."))
    }

    private fun sampleImage(
        base: dev.phosphor.mobil3.state.PhosphorStateSnapshot,
        mode: String = "auto",
        revision: Long = 3,
        oldMode: String = "off",
        changed: Boolean = false,
        receiptOrdinal: Long = revision,
        idempotencyOrdinal: Long = revision,
    ): PhosphorStoreImage {
        val stamp = ProvenanceStamp(
            receiptId = "hud-$receiptOrdinal",
            writer = PrincipalId(PrincipalKind.HUMAN, "local-human"),
            reason = "set HUD $mode",
            transport = Transport.UI,
            sessionId = null,
            monotonicMillis = receiptOrdinal,
            wallTimeMillis = 1000 + receiptOrdinal,
        )
        val snapshot = base.copy(
            revision = revision,
            sequence = revision,
            wallTimeMillis = stamp.wallTimeMillis,
            desired = base.desired.copy(displayHud = mode),
            effective = base.effective.copy(displayHud = mode),
            provenance = frozenMapOf(StateField.DISPLAY_HUD to stamp),
        )
        val action = SetDisplayHud(mode)
        val delta = if (changed) StateDelta(
            revision = revision,
            sequence = revision,
            changes = FrozenList.copyOf(listOf(StateChange(StateField.DISPLAY_HUD, StringStateValue(oldMode), StringStateValue(mode), stamp))),
            provenance = stamp,
        ) else null
        val acknowledgement = ActionAcknowledgement(
            receiptId = stamp.receiptId,
            actionType = ActionType.SET_DISPLAY_HUD,
            changed = changed,
            revision = revision,
            sequence = revision,
            effectiveValue = StringStateValue(mode),
            provenance = stamp,
            delta = delta,
            effects = frozenListOf(),
        )
        val auditRecord = AuditRecord(
            receiptId = stamp.receiptId,
            kind = if (changed) AuditKind.ACTION else AuditKind.NO_OP,
            wallTimeMillis = stamp.wallTimeMillis,
            actionType = ActionType.SET_DISPLAY_HUD,
            fields = FrozenSet.copyOf(setOf(StateField.DISPLAY_HUD)),
            provenance = stamp,
        )
        val idempotencyRecord = IdempotencyRecord(
            principal = stamp.writer,
            key = "idem-$idempotencyOrdinal",
            action = action,
            canonicalPayload = action.canonicalPayload(),
            acknowledgement = acknowledgement,
            acceptedWallTimeMillis = stamp.wallTimeMillis,
            expiresWallTimeMillis = stamp.wallTimeMillis + IDEMPOTENCY_TTL_MILLIS,
            ordinal = idempotencyOrdinal,
        )
        return PhosphorStoreImage(
            snapshot = snapshot,
            audit = AuditIndexSnapshot(AuditRetentionPolicy(128, IDEMPOTENCY_TTL_MILLIS), stamp.wallTimeMillis, FrozenList.copyOf(listOf(auditRecord))),
            idempotency = IdempotencyIndexSnapshot(IdempotencyIndexPolicy(maximumRecords = 128), FrozenList.copyOf(listOf(idempotencyRecord))),
            nextReceiptOrdinal = receiptOrdinal + 1,
            nextIdempotencyOrdinal = idempotencyOrdinal + 1,
        )
    }

    private fun refusalAliasImage(
        base: dev.phosphor.mobil3.state.PhosphorStateSnapshot,
        kind: AuditKind,
        code: RefusalCode,
    ): PhosphorStoreImage = sampleImage(base).let { image ->
        image.copy(
            audit = AuditIndexSnapshot(
                AuditRetentionPolicy(128, IDEMPOTENCY_TTL_MILLIS),
                2000,
                FrozenList.copyOf(listOf(
                    AuditRecord(
                        receiptId = if (kind == AuditKind.REFUSAL) "hud-99" else "hud-100",
                        kind = kind,
                        wallTimeMillis = 2000,
                        actionType = ActionType.SET_DISPLAY_HUD,
                        fields = FrozenSet.copyOf(setOf(StateField.DISPLAY_HUD)),
                        provenance = null,
                        refusal = Refusal(
                            code = code,
                            fix = "Use an authorized local HUD writer or a fresh idempotency key.",
                            currentRevision = image.snapshot.revision,
                            originalReceiptId = image.snapshot.provenance.getValue(StateField.DISPLAY_HUD).receiptId,
                        ),
                    ),
                )),
            ),
            nextReceiptOrdinal = 101,
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun updateChecksum(root: JSONObject) {
        root.put("checksum_sha256", sha256(HudCausalEnvelopeCodec.canonicalJson(root.getJSONObject("payload"))))
    }

    private fun envelopeWithRawPayloadMutation(encoded: String, oldPayloadField: String, newPayloadField: String): String {
        return encoded.replace(oldPayloadField, newPayloadField)
    }
}

private class InMemoryPreferenceBoundary(
    initial: Map<String, Any?> = emptyMap(),
    private val commitSucceeds: Boolean = true,
) : PreferenceBoundary {
    private val values = linkedMapOf<String, Any?>().also { it.putAll(initial) }
    var commitCount: Int = 0
        private set
    var mutatedBeforeFailedCommit: Boolean = false
        private set

    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun getString(key: String): String? = values[key] as? String
    override fun getIntOrNull(key: String): Int? = values[key] as? Int
    override fun getBooleanOrNull(key: String): Boolean? = values[key] as? Boolean
    override fun editCommit(block: PreferenceEditorBoundary.() -> Unit): Boolean {
        val pending = linkedMapOf<String, Any?>()
        val removals = mutableSetOf<String>()
        val editor = object : PreferenceEditorBoundary {
            override fun putString(key: String, value: String) { pending[key] = value; removals -= key }
            override fun putInt(key: String, value: Int) { pending[key] = value; removals -= key }
            override fun remove(key: String) { removals += key; pending -= key }
        }
        editor.block()
        commitCount += 1
        removals.forEach { values.remove(it) }
        values.putAll(pending)
        if (!commitSucceeds) mutatedBeforeFailedCommit = true
        return commitSucceeds
    }
    override fun snapshot(): Map<String, Any?> = values.toMap()
    override fun restore(snapshot: Map<String, Any?>, keys: Set<String>) {
        keys.forEach { key ->
            if (snapshot.containsKey(key)) values[key] = snapshot[key] else values.remove(key)
        }
    }
}
