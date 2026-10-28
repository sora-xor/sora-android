package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkamarktWebContractTest {

    @Test
    fun `status filters require exact normalized membership`() {
        assertTrue(
            PolkamarktWebContract.matchesStatusFilter(
                status = " OPEN ",
                filter = "active",
            )
        )
        assertTrue(
            PolkamarktWebContract.matchesStatusFilter(
                status = "FINALIZED",
                filter = " finalized ",
            )
        )
        assertFalse(
            PolkamarktWebContract.matchesStatusFilter(
                status = "unresolved",
                filter = "finalized",
            )
        )
        assertFalse(
            PolkamarktWebContract.matchesStatusFilter(
                status = "not_open",
                filter = "active",
            )
        )
        assertTrue(
            PolkamarktWebContract.matchesOwnerFilter(
                creator = "5ExactCaseSensitiveOwner",
                selectedAccount = "5ExactCaseSensitiveOwner",
                mineOnly = true,
            )
        )
        assertFalse(
            PolkamarktWebContract.matchesOwnerFilter(
                creator = "5exactCaseSensitiveOwner",
                selectedAccount = "5ExactCaseSensitiveOwner",
                mineOnly = true,
            )
        )
        assertFalse(
            PolkamarktWebContract.matchesOwnerFilter(
                creator = null,
                selectedAccount = null,
                mineOnly = true,
            )
        )
        assertTrue(
            PolkamarktWebContract.matchesOwnerFilter(
                creator = null,
                selectedAccount = null,
                mineOnly = false,
            )
        )
    }

    @Test
    fun `all status filter preserves markets outside pinned status sets`() {
        listOf(null, "", "unresolved", "not_open").forEach { status ->
            assertTrue(
                PolkamarktWebContract.matchesStatusFilter(
                    status = status,
                    filter = "all",
                )
            )
        }
        assertFalse(
            PolkamarktWebContract.matchesStatusFilter(
                status = "open",
                filter = "unknown",
            )
        )
    }

    @Test
    fun `mobile quote slippage rejects zero and values outside canonical range`() {
        PolkamarktSlippageValidator.requireMobileQuoteValue(
            PolkamarktWebContract.MIN_SLIPPAGE_BPS
        )
        PolkamarktSlippageValidator.requireMobileQuoteValue(
            PolkamarktWebContract.MAX_SLIPPAGE_BPS
        )

        assertThrows(IllegalArgumentException::class.java) {
            PolkamarktSlippageValidator.requireMobileQuoteValue(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PolkamarktSlippageValidator.requireMobileQuoteValue(
                PolkamarktWebContract.MAX_SLIPPAGE_BPS + 1
            )
        }
    }

    @Test
    fun `external Polkamarkt links require credential-free HTTPS`() {
        assertEquals(
            "https://example.org/rules",
            PolkamarktExternalLinkPolicy.validated(
                "https://example.org/rules"
            )
        )
        assertEquals(
            null,
            PolkamarktExternalLinkPolicy.validated("http://example.org/rules")
        )
        assertEquals(
            null,
            PolkamarktExternalLinkPolicy.validated(
                "https://user:secret@example.org/rules"
            )
        )
        assertEquals(
            null,
            PolkamarktExternalLinkPolicy.validated(
                "https://example.org/rules\nhttps://evil.example"
            )
        )
    }

    @Test
    fun `mobile constants match pinned web fixture`() {
        val resource = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("polkamarkt_web_contract.json")
        )
        val fixture = resource.bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val webReference = fixture["webReference"].asJsonObject
        val behavior = fixture["canonicalBehavior"].asJsonObject
        val runtime = fixture["runtime"].asJsonObject
        val canonicalVectors = fixture["canonicalVectors"].asJsonObject
        val claimConfirmation = fixture["mobileClaimConfirmation"].asJsonObject
        val translationKeys = fixture["translationKeys"].asJsonObject

        assertEquals(
            PolkamarktWebContract.SOURCE_REVISION,
            webReference["inspectedRevision"].asString,
        )
        assertEquals(
            PolkamarktWebContract.SOURCE_BRANCH,
            webReference["branch"].asString,
        )
        assertEquals(
            PolkamarktWebContract.SOURCE_COMMIT_TREE,
            webReference["commitTreeObject"].asString,
        )
        assertEquals(
            PolkamarktWebContract.SOURCE_POLKAMARKT_TREE,
            webReference["polkamarktTreeObject"].asString,
        )
        assertEquals(
            "verified",
            webReference["commitSignatureStatus"].asString,
        )
        val sourceFiles =
            webReference["sourceFiles"].asJsonArray.map { it.asString }
        val sourceBlobs = webReference["sourceBlobObjects"].asJsonObject
        assertEquals(
            PolkamarktWebContract.SOURCE_FILE_COUNT,
            sourceFiles.size,
        )
        assertEquals(sourceFiles.toSet(), sourceBlobs.keySet())
        assertEquals(
            true,
            sourceBlobs.entrySet().all {
                val blob = it.value.asJsonObject
                blob["gitSha1"].asString
                    .matches(Regex("^[0-9a-f]{40}$")) &&
                    blob["bytes"].asLong > 0
            },
        )
        assertEquals(
            true,
            webReference["polkamarktContractPresentAtInspectedRevision"].asBoolean,
        )
        assertEquals("u32", runtime["marketIdScaleType"].asString)
        assertEquals(
            PolkamarktMarketId.MAX_VALUE,
            runtime["marketIdMaximum"].asLong,
        )
        assertEquals("u32", runtime["closeBlockScaleType"].asString)
        assertEquals(
            PolkamarktMarketId.MAX_VALUE,
            runtime["closeBlockMaximum"].asLong,
        )
        assertEquals(
            PolkamarktWebContract.DEFAULT_SLIPPAGE_BPS,
            behavior["defaultSlippageBps"].asInt,
        )
        assertEquals(
            PolkamarktWebContract.MIN_SLIPPAGE_BPS,
            behavior["minimumMobileSlippageBps"].asInt,
        )
        assertEquals(
            PolkamarktWebContract.MAX_SLIPPAGE_BPS,
            behavior["maximumMobileSlippageBps"].asInt,
        )
        assertEquals(
            PolkamarktWebContract.SLIPPAGE_PRESETS_BPS,
            behavior["slippagePresetsBps"].asJsonArray.map { it.asInt },
        )
        assertEquals(
            PolkamarktWebContract.STATUS_FILTERS,
            behavior["statusFilters"].asJsonArray.map { it.asString },
        )
        assertEquals(
            PolkamarktWebContract.OPEN_STATUSES,
            behavior["openStatuses"].asJsonArray.map { it.asString },
        )
        assertEquals(
            PolkamarktWebContract.FINALIZED_STATUSES,
            behavior["finalizedStatuses"].asJsonArray.map { it.asString },
        )
        assertEquals(
            PolkamarktWebContract.CLAIMABLE_STATUSES,
            behavior["claimableStatuses"].asJsonArray.map { it.asString },
        )
        assertEquals(
            PolkamarktWebContract.CATEGORIES,
            behavior["categories"].asJsonArray.map { it.asString },
        )
        assertEquals(
            PolkamarktWebContract.QUOTE_DEBOUNCE_MILLISECONDS,
            behavior["quoteDebounceMilliseconds"].asLong,
        )
        assertEquals(
            PolkamarktWebContract.CARD_HISTORY_MARKET_LIMIT,
            behavior["cardHistoryMarketLimit"].asInt,
        )
        assertEquals(
            PolkamarktWebContract.DPM_CURVE_POINT_COUNT,
            behavior["dpmCurvePointCount"].asInt,
        )
        assertEquals(
            PolkamarktWebContract.MAX_BATCH_CLAIMS,
            behavior["maxBatchClaims"].asInt,
        )
        assertEquals(
            PolkamarktWebContract.TRANSLATION_KEYS,
            translationKeys.entrySet().associate {
                it.key to it.value.asString
            },
        )
        assertEquals(
            true,
            claimConfirmation["requiresExplicitConfirmation"].asBoolean,
        )
        assertEquals(
            PolkamarktWebContract.CLAIM_CONFIRMATION_REVIEW_FIELDS,
            claimConfirmation["requiredReviewedFields"].asJsonArray.map {
                it.asString
            },
        )
        assertEquals(
            PolkamarktWebContract.CLAIM_CONFIRMATION_FRESH_CHECKS,
            claimConfirmation["freshChecksBeforeSigning"].asJsonArray.map {
                it.asString
            },
        )
        val claimConfirmationCopy = claimConfirmation["copy"].asJsonObject
        assertEquals(
            PolkamarktWebContract.CLAIM_CONFIRMATION_TITLE,
            claimConfirmationCopy["title"].asString,
        )
        assertEquals(
            PolkamarktWebContract.CLAIM_CONFIRMATION_BATCH_TITLE,
            claimConfirmationCopy["batchTitle"].asString,
        )
        assertEquals(
            PolkamarktWebContract.CLAIM_CONFIRMATION_BODY,
            claimConfirmationCopy["body"].asString,
        )
        assertEquals(
            PolkamarktWebContract.CLAIM_CONFIRMATION_FEE_NOTICE,
            claimConfirmationCopy["feeNotice"].asString,
        )
        val derivationSources =
            canonicalVectors["derivationSources"].asJsonObject
        assertEquals(
            "lib/amounts.ts",
            derivationSources["minimumOutput"].asString,
        )
        assertEquals(
            "services/runtimeMarkets.ts",
            derivationSources["runtimeCalls"].asString,
        )
        assertEquals(
            "../sora2-network/pallets/polkamarkt/src/lib.rs",
            derivationSources["scaleArguments"].asString,
        )
        assertEquals(
            "411dcdb70c5c00b21482a44d02334840d5f338c6",
            derivationSources["runtimeRevision"].asString,
        )
        canonicalVectors["minimumOutput"].asJsonArray.forEach { element ->
            val vector = element.asJsonObject
            assertEquals(
                vector["expectedMinimum"].asString.toBigInteger(),
                PolkamarktSlippageValidator.minimumOutput(
                    amount = vector["quotedOutput"].asString.toBigInteger(),
                    slippageBps = vector["slippageBps"].asInt,
                ),
            )
        }
        val qualification =
            canonicalVectors["fullExtrinsicQualification"].asJsonObject
        assertEquals(
            "sora-mobile-polkamarkt-full-extrinsic-receipt-v1",
            qualification["receiptSchema"].asString,
        )
        assertEquals(
            "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf",
            qualification["requiredMetadataSha256"].asString,
        )
        assertEquals(
            "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5",
            qualification["requiredGenesisHash"].asString,
        )
        assertEquals(130, qualification["requiredSpecVersion"].asInt)
        assertEquals(130, qualification["requiredTransactionVersion"].asInt)
        assertEquals(
            true,
            qualification["metadataIndicesMustBeResolvedDynamically"].asBoolean,
        )
        val requiredVectorOrder = qualification["requiredVectorOrder"]
            .asJsonArray.map { it.asString }
        assertEquals(
            runtime["calls"].asJsonArray.map { it.asString },
            requiredVectorOrder,
        )
        val generation = qualification["generationContract"].asJsonObject
        assertEquals(
            "sora-mobile-polkamarkt-platform-extrinsic-receipt-v1",
            generation["candidateReceiptSchema"].asString,
        )
        assertEquals(
            "sora-mobile-polkamarkt-extrinsic-review-v1",
            generation["independentReviewSchema"].asString,
        )
        assertEquals(
            "scripts/qualify-polkamarkt-extrinsic-receipts.mjs",
            generation["merger"].asString,
        )
        val reference = generation["referenceImplementation"].asJsonObject
        assertEquals(
            PolkamarktWebContract.SOURCE_REVISION,
            reference["revision"].asString,
        )
        assertEquals("polkadotApi", reference["package"].asString)
        assertEquals("11.2.1", reference["packageVersion"].asString)
        val metadata = generation["runtimeMetadata"].asJsonObject
        assertEquals(
            qualification["requiredMetadataSha256"].asString,
            metadata["sha256"].asString,
        )
        assertEquals(
            "resolve-from-this-metadata-never-hardcode",
            metadata["palletAndCallIndices"].asString,
        )
        val signing = generation["signingContext"].asJsonObject
        assertEquals("sr25519", signing["cryptoType"].asString)
        assertEquals(
            256,
            signing["signaturePayloadHashingThresholdBytes"].asInt,
        )
        assertFalse(signing["privateSigningMaterialPermittedInFixture"].asBoolean)
        assertEquals(
            listOf(
                "id",
                "call",
                "arguments",
                "metadataPalletIndex",
                "metadataCallIndex",
                "scaleArgumentsHex",
                "fullCallHex",
                "rawSigningPayloadHex",
                "signingPrehashHex",
                "signingPrehashRule",
                "decodedProjection",
                "decodedProjectionSha256",
            ),
            generation["requiredSharedVectorFields"].asJsonArray.map { it.asString },
        )
        assertEquals(
            listOf(
                "id",
                "signerPublicKeyHex",
                "signatureHex",
                "signedExtrinsicHex",
                "extrinsicHashHex",
                "signingPrehashHex",
                "decodedProjection",
                "decodedProjectionSha256",
            ),
            generation["requiredPlatformVectorFields"].asJsonArray.map { it.asString },
        )
        assertEquals(7, generation["parityRules"].asJsonArray.size())
        val reviewedQualification =
            qualification["reviewedWebAndRuntimeReceiptQualified"]
        assertTrue(reviewedQualification.isJsonPrimitive)
        assertTrue(reviewedQualification.asJsonPrimitive.isBoolean)
        if (!reviewedQualification.asBoolean) {
            assertTrue(qualification["reviewedReceipt"].isJsonNull)
            assertEquals(
                "Generate independent reference, Android, and iOS receipts from the pinned web revision and exact runtime metadata; independently sign the composite receipt; then prove full call bytes, signing prehashes, signatures, signed extrinsics, and decoded projections before enabling mutations.",
                qualification["blocker"].asString,
            )
        } else {
            assertTrue(qualification["blocker"].isJsonNull)
            val receipt = qualification["reviewedReceipt"].asJsonObject
            assertEquals(
                qualification["receiptSchema"].asString,
                receipt["format"].asString,
            )
            assertEquals(
                qualification["requiredMetadataSha256"].asString,
                receipt["metadataSha256"].asString,
            )
            assertEquals(
                qualification["requiredGenesisHash"].asString,
                receipt["genesisHash"].asString,
            )
            assertEquals(
                requiredVectorOrder.size,
                receipt["sharedVectors"].asJsonArray.size(),
            )
            listOf("reference", "android", "ios").forEach { platform ->
                val platformReceipt = receipt["platforms"]
                    .asJsonObject[platform].asJsonObject
                assertEquals(
                    requiredVectorOrder.size,
                    platformReceipt["vectors"].asJsonArray.size(),
                )
                assertTrue(
                    Regex("^[0-9a-f]{64}$").matches(
                        platformReceipt["candidateReceiptSha256"].asString,
                    ),
                )
            }
            val reviewKeySha256 = receipt["review"]
                .asJsonObject["publicKeySha256"].asString
            assertTrue(Regex("^[0-9a-f]{64}$").matches(reviewKeySha256))
            assertFalse(reviewKeySha256.all { it == '0' })
            assertTrue(receipt["parityQualified"].asBoolean)
        }
    }
}
