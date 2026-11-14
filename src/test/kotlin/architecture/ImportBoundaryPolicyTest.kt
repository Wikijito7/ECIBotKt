package architecture

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportBoundaryPolicyTest {

    private val vendorImports = listOf(
        VendorImport("dev.kord.core.Kord", VendorFamily.KORD),
        VendorImport("net.dv8tion.jda.api.JDA", VendorFamily.JDA),
        VendorImport("dev.minn.jda.ktx.coroutines.await", VendorFamily.JDA_KTX),
        VendorImport("moe.kyokobot.libdave.DaveSession", VendorFamily.LIBDAVE),
    )

    private val adapterPackages = listOf(
        "es.wokis.jda",
        "es.wokis.jda.command",
        "es.wokis.jda.player",
        "es.wokis.jda.voice",
    )

    @TestFactory
    fun `Given the Phase 2 and Phase 6 matrix When evaluating imports Then decisions match`(): List<DynamicTest> =
        policyCases().map { case ->
            DynamicTest.dynamicTest(
                "Given ${case.label} When the policy evaluates Then allowed is ${case.expectedAllowed}",
            ) {
                // Given
                val policy = ImportBoundaryPolicy(case.mode)
                val imported = importOf(case.importName)

                // When
                val decision = policy.evaluate(case.module, case.packageName, imported)

                // Then
                assertEquals(case.expectedAllowed, decision.allowed, case.label)
                assertEquals(case.expectedVendor, decision.vendor, case.label)
            }
        }

    @Test
    fun `Given a semicolon inside a backtick identifier When evaluated Then it is not Kord and the following vendor import is denied`() {
        // Given
        val header = KotlinFileHeaderParser.parse(
            "import dev.`kord;legacy`.core.Type; import net.dv8tion.jda.api.JDA\n",
        )
        val policy = ImportBoundaryPolicy(ScanMode.PHASE_2)

        // When
        val backticked = policy.evaluate(SourceModule.ROOT, "solid.root", header.imports[0])
        val vendor = policy.evaluate(SourceModule.ROOT, "solid.root", header.imports[1])

        // Then
        assertTrue(backticked.allowed)
        assertNull(backticked.vendor)
        assertFalse(vendor.allowed)
        assertEquals(VendorFamily.JDA, vendor.vendor)
    }

    private fun policyCases(): List<PolicyCase> = buildList {
        addAll(phase2Cases())
        addAll(phase6Cases())
    }

    private fun phase2Cases(): List<PolicyCase> = buildList {
        // Phase 2 root: Kord is grandfathered legacy; every other vendor is denied,
        // including when the file declares an adapter-looking package.
        vendorImports.forEach { vendorImport ->
            val expected = vendorImport.vendor == VendorFamily.KORD
            add(
                PolicyCase(
                    "P2 root ${vendorImport.vendor} ${if (expected) "allowed" else "denied"}",
                    ScanMode.PHASE_2,
                    SourceModule.ROOT,
                    "solid.root",
                    vendorImport.importName,
                    expected,
                    vendorImport.vendor,
                ),
            )
            add(
                PolicyCase(
                    "P2 root ${vendorImport.vendor} ${if (expected) "allowed" else "denied"} in adapter-looking package",
                    ScanMode.PHASE_2,
                    SourceModule.ROOT,
                    "es.wokis.jda.command",
                    vendorImport.importName,
                    expected,
                    vendorImport.vendor,
                ),
            )
        }

        // Phase 2 gateway-free modules deny all four vendors.
        gatewayFreeModules().forEach { (module, packageName) ->
            vendorImports.forEach { vendorImport ->
                add(
                    PolicyCase(
                        "P2 ${module.label} ${vendorImport.vendor} denied",
                        ScanMode.PHASE_2,
                        module,
                        packageName,
                        vendorImport.importName,
                        false,
                        vendorImport.vendor,
                    ),
                )
            }
        }

        // Phase 2 :jda-runtime: Kord denied; other vendors only inside declared adapter packages.
        vendorImports.forEach { vendorImport ->
            if (vendorImport.vendor == VendorFamily.KORD) {
                adapterPackages.forEach { packageName ->
                    add(jdaRuntimeDeniedPhase2(vendorImport, packageName))
                }
                add(jdaRuntimeDeniedPhase2(vendorImport, "other.pkg"))
                add(jdaRuntimeDeniedPhase2(vendorImport, null))
            } else {
                adapterPackages.forEach { packageName ->
                    add(jdaRuntimeAllowedPhase2(vendorImport, packageName))
                    add(jdaRuntimeAllowedPhase2(vendorImport, "$packageName.sub"))
                }
                add(jdaRuntimeDeniedPhase2(vendorImport, "other.pkg"))
                add(jdaRuntimeDeniedPhase2(vendorImport, "es.wokis.jdaextra"))
                add(jdaRuntimeDeniedPhase2(vendorImport, null))
            }
        }

        // Phase 2 explicitly excludes the spike, so its module row fails closed.
        vendorImports.forEach { vendorImport ->
            add(
                PolicyCase(
                    "P2 spike ${vendorImport.vendor} denied",
                    ScanMode.PHASE_2,
                    SourceModule.SPIKE,
                    "spike.jdadave",
                    vendorImport.importName,
                    false,
                    vendorImport.vendor,
                ),
            )
        }

        addAll(normalizationCases(ScanMode.PHASE_2))
        addAll(nonVendorCases())
    }

    private fun phase6Cases(): List<PolicyCase> = buildList {
        // Phase 6 root removes the Kord exception and never authorizes by package.
        vendorImports.forEach { vendorImport ->
            add(
                PolicyCase(
                    "P6 root ${vendorImport.vendor} denied",
                    ScanMode.PHASE_6,
                    SourceModule.ROOT,
                    "solid.root",
                    vendorImport.importName,
                    false,
                    vendorImport.vendor,
                ),
            )
            add(
                PolicyCase(
                    "P6 root ${vendorImport.vendor} denied in adapter-looking package",
                    ScanMode.PHASE_6,
                    SourceModule.ROOT,
                    "es.wokis.jda",
                    vendorImport.importName,
                    false,
                    vendorImport.vendor,
                ),
            )
        }

        // Phase 6 :core and :locale-persistence deny all four vendors.
        gatewayFreeModules().forEach { (module, packageName) ->
            vendorImports.forEach { vendorImport ->
                add(
                    PolicyCase(
                        "P6 ${module.label} ${vendorImport.vendor} denied",
                        ScanMode.PHASE_6,
                        module,
                        packageName,
                        vendorImport.importName,
                        false,
                        vendorImport.vendor,
                    ),
                )
            }
        }

        // Phase 6 :jda-runtime: Kord denied; other vendors only inside declared adapter packages.
        vendorImports.forEach { vendorImport ->
            if (vendorImport.vendor == VendorFamily.KORD) {
                adapterPackages.forEach { packageName ->
                    add(jdaRuntimeDeniedPhase6(vendorImport, packageName))
                }
                add(jdaRuntimeDeniedPhase6(vendorImport, "other.pkg"))
                add(jdaRuntimeDeniedPhase6(vendorImport, null))
            } else {
                adapterPackages.forEach { packageName ->
                    add(jdaRuntimeAllowedPhase6(vendorImport, packageName))
                    add(jdaRuntimeAllowedPhase6(vendorImport, "$packageName.sub"))
                }
                add(jdaRuntimeDeniedPhase6(vendorImport, "other.pkg"))
                add(jdaRuntimeDeniedPhase6(vendorImport, "es.wokis.jdaextra"))
                add(jdaRuntimeDeniedPhase6(vendorImport, null))
            }
        }

        // Phase 6 retained spike: Kord denied; other vendors only in spike.jdadave/subpackages.
        vendorImports.forEach { vendorImport ->
            if (vendorImport.vendor == VendorFamily.KORD) {
                add(spikeDeniedPhase6(vendorImport, "spike.jdadave"))
            } else {
                add(spikeAllowedPhase6(vendorImport, "spike.jdadave"))
                add(spikeAllowedPhase6(vendorImport, "spike.jdadave.sub"))
                add(spikeDeniedPhase6(vendorImport, "spike.other"))
                add(spikeDeniedPhase6(vendorImport, "spike.jdadaveextra"))
                add(spikeDeniedPhase6(vendorImport, "es.wokis.jda"))
                add(spikeDeniedPhase6(vendorImport, null))
            }
        }

        addAll(normalizationCases(ScanMode.PHASE_6))
        addAll(nonVendorCases())
    }

    private fun gatewayFreeModules(): List<Pair<SourceModule, String>> = listOf(
        SourceModule.CORE to "es.wokis.core",
        SourceModule.LOCALE_PERSISTENCE to "es.wokis.data.locale",
    )

    private fun jdaRuntimeAllowedPhase2(vendorImport: VendorImport, packageName: String): PolicyCase =
        PolicyCase(
            "P2 jda-runtime ${vendorImport.vendor} allowed in $packageName",
            ScanMode.PHASE_2,
            SourceModule.JDA_RUNTIME,
            packageName,
            vendorImport.importName,
            true,
            vendorImport.vendor,
        )

    private fun jdaRuntimeDeniedPhase2(vendorImport: VendorImport, packageName: String?): PolicyCase =
        PolicyCase(
            "P2 jda-runtime ${vendorImport.vendor} denied in ${packageName ?: "missing package"}",
            ScanMode.PHASE_2,
            SourceModule.JDA_RUNTIME,
            packageName,
            vendorImport.importName,
            false,
            vendorImport.vendor,
        )

    private fun jdaRuntimeAllowedPhase6(vendorImport: VendorImport, packageName: String): PolicyCase =
        PolicyCase(
            "P6 jda-runtime ${vendorImport.vendor} allowed in $packageName",
            ScanMode.PHASE_6,
            SourceModule.JDA_RUNTIME,
            packageName,
            vendorImport.importName,
            true,
            vendorImport.vendor,
        )

    private fun jdaRuntimeDeniedPhase6(vendorImport: VendorImport, packageName: String?): PolicyCase =
        PolicyCase(
            "P6 jda-runtime ${vendorImport.vendor} denied in ${packageName ?: "missing package"}",
            ScanMode.PHASE_6,
            SourceModule.JDA_RUNTIME,
            packageName,
            vendorImport.importName,
            false,
            vendorImport.vendor,
        )

    private fun spikeAllowedPhase6(vendorImport: VendorImport, packageName: String): PolicyCase =
        PolicyCase(
            "P6 spike ${vendorImport.vendor} allowed in $packageName",
            ScanMode.PHASE_6,
            SourceModule.SPIKE,
            packageName,
            vendorImport.importName,
            true,
            vendorImport.vendor,
        )

    private fun spikeDeniedPhase6(vendorImport: VendorImport, packageName: String?): PolicyCase =
        PolicyCase(
            "P6 spike ${vendorImport.vendor} denied in ${packageName ?: "missing package"}",
            ScanMode.PHASE_6,
            SourceModule.SPIKE,
            packageName,
            vendorImport.importName,
            false,
            vendorImport.vendor,
        )

    private fun normalizationCases(mode: ScanMode): List<PolicyCase> = listOf(
        PolicyCase(
            "$mode core backticked Kord denied",
            mode,
            SourceModule.CORE,
            "es.wokis.core",
            "dev.`kord`.core.SomeType as Alias",
            false,
            VendorFamily.KORD,
        ),
        PolicyCase(
            "$mode core aliased jda-ktx denied",
            mode,
            SourceModule.CORE,
            "es.wokis.core",
            "dev.minn.jda.ktx.coroutines.await as AwaitKtx",
            false,
            VendorFamily.JDA_KTX,
        ),
        PolicyCase(
            "$mode core wildcard libdave denied",
            mode,
            SourceModule.CORE,
            "es.wokis.core",
            "moe.kyokobot.libdave.*",
            false,
            VendorFamily.LIBDAVE,
        ),
        PolicyCase(
            "$mode root wildcard JDA denied",
            mode,
            SourceModule.ROOT,
            "solid.root",
            "net.dv8tion.jda.api.*",
            false,
            VendorFamily.JDA,
        ),
    )

    private fun nonVendorCases(): List<PolicyCase> = listOf(
        PolicyCase(
            "root kordextra is not Kord",
            ScanMode.PHASE_2,
            SourceModule.ROOT,
            "solid.root",
            "dev.kordextra.Type",
            true,
            null,
        ),
        PolicyCase(
            "core normal import allowed",
            ScanMode.PHASE_2,
            SourceModule.CORE,
            "es.wokis.core",
            "kotlinx.coroutines.flow.Flow",
            true,
            null,
        ),
        PolicyCase(
            "P6 core normal import allowed",
            ScanMode.PHASE_6,
            SourceModule.CORE,
            "es.wokis.core",
            "kotlinx.coroutines.flow.Flow",
            true,
            null,
        ),
    )

    private fun importOf(name: String): KotlinImport =
        KotlinFileHeaderParser.parse("import $name\n").imports.single()

    private data class VendorImport(
        val importName: String,
        val vendor: VendorFamily,
    )

    private data class PolicyCase(
        val label: String,
        val mode: ScanMode,
        val module: SourceModule,
        val packageName: String?,
        val importName: String,
        val expectedAllowed: Boolean,
        val expectedVendor: VendorFamily?,
    )
}
