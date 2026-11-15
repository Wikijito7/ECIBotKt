package architecture

enum class ScanMode {
    PHASE_2,
    PHASE_6,
}

enum class SourceModule(val label: String) {
    ROOT("root"),
    CORE("core"),
    LOCALE_PERSISTENCE("locale-persistence"),
    PLAYER("player"),
    JDA_RUNTIME("jda-runtime"),
    SPIKE("spike/jda-dave"),
}

enum class VendorFamily(
    val prefixSegments: List<String>,
    val displayPrefix: String,
) {
    KORD(listOf("dev", "kord"), "dev.kord."),
    JDA(listOf("net", "dv8tion"), "net.dv8tion."),
    JDA_KTX(listOf("dev", "minn", "jda", "ktx"), "dev.minn.jda.ktx."),
    LIBDAVE(listOf("moe", "kyokobot", "libdave"), "moe.kyokobot.libdave."),
}

data class ImportPolicyDecision(
    val allowed: Boolean,
    val vendor: VendorFamily?,
    val reason: String,
)

/**
 * Phase 2 / Phase 6 vendor-import matrix.
 *
 * Matching is segment-based and package-boundary-based. A package name alone
 * never authorizes root imports.
 */
class ImportBoundaryPolicy(private val mode: ScanMode) {

    fun classify(imported: KotlinImport): VendorFamily? =
        VendorFamily.entries.firstOrNull { imported.startsWithSegments(it.prefixSegments) }

    fun evaluate(
        module: SourceModule,
        packageName: String?,
        imported: KotlinImport,
    ): ImportPolicyDecision {
        val vendor = classify(imported) ?: return ImportPolicyDecision(
            allowed = true,
            vendor = null,
            reason = "Not a vendor import",
        )
        val allowed = isAllowed(module, vendor, packageName)
        return ImportPolicyDecision(
            allowed = allowed,
            vendor = vendor,
            reason = if (allowed) {
                allowedReason(module, packageName)
            } else {
                denialReason(module, vendor, packageName)
            },
        )
    }

    fun isAllowed(module: SourceModule, vendor: VendorFamily, packageName: String?): Boolean = when (mode) {
        ScanMode.PHASE_2 -> when (module) {
            SourceModule.ROOT -> vendor == VendorFamily.KORD

            SourceModule.CORE,
            SourceModule.LOCALE_PERSISTENCE,
            SourceModule.PLAYER,
            -> false

            SourceModule.JDA_RUNTIME -> vendor != VendorFamily.KORD && isJdaAdapterPackage(packageName)

            SourceModule.SPIKE -> false
        }

        ScanMode.PHASE_6 -> when (module) {
            SourceModule.ROOT,
            SourceModule.CORE,
            SourceModule.LOCALE_PERSISTENCE,
            SourceModule.PLAYER,
            -> false

            SourceModule.JDA_RUNTIME -> vendor != VendorFamily.KORD && isJdaAdapterPackage(packageName)

            SourceModule.SPIKE -> vendor != VendorFamily.KORD && isSpikePackage(packageName)
        }
    }

    private fun allowedReason(module: SourceModule, packageName: String?): String =
        when (module) {
            SourceModule.ROOT -> "Root Kord imports are grandfathered legacy in Phase 2"

            SourceModule.JDA_RUNTIME -> "Vendor import allowed by the declared JDA adapter package '$packageName'"

            SourceModule.SPIKE -> "Vendor import allowed by the non-production spike package '$packageName'"

            SourceModule.CORE,
            SourceModule.LOCALE_PERSISTENCE,
            SourceModule.PLAYER,
            -> "Vendor import allowed"
        }

    private fun denialReason(module: SourceModule, vendor: VendorFamily, packageName: String?): String =
        when (module) {
            SourceModule.ROOT -> "The root project may not import ${vendor.displayPrefix} in mode $mode"

            SourceModule.CORE,
            SourceModule.LOCALE_PERSISTENCE,
            SourceModule.PLAYER,
            -> "Gateway-free module ${module.label} may not import ${vendor.displayPrefix} in mode $mode"

            SourceModule.JDA_RUNTIME -> if (vendor == VendorFamily.KORD) {
                ":jda-runtime may not import ${vendor.displayPrefix} in mode $mode"
            } else {
                ":jda-runtime vendor imports require a declared es.wokis.jda adapter package, " +
                    "but the file package is ${packageName ?: "<none>"}"
            }

            SourceModule.SPIKE ->
                "The spike may only import ${vendor.displayPrefix} from the non-production " +
                    "spike.jdadave package, but the file package is ${packageName ?: "<none>"}"
        }

    private companion object {
        val JDA_ADAPTER_PACKAGES = listOf(
            "es.wokis.jda",
            "es.wokis.jda.command",
            "es.wokis.jda.player",
            "es.wokis.jda.voice",
        )
        val SPIKE_PACKAGES = listOf("spike.jdadave")

        fun isJdaAdapterPackage(packageName: String?): Boolean =
            matchesPackageBoundary(packageName, JDA_ADAPTER_PACKAGES)

        fun isSpikePackage(packageName: String?): Boolean =
            matchesPackageBoundary(packageName, SPIKE_PACKAGES)

        fun matchesPackageBoundary(packageName: String?, allowedPackages: List<String>): Boolean {
            if (packageName == null) return false
            return allowedPackages.any { allowed ->
                packageName == allowed || packageName.startsWith("$allowed.")
            }
        }
    }
}

data class ImportViolation(
    val file: String,
    val line: Int,
    val importStatement: String,
    val vendor: VendorFamily,
    val module: SourceModule,
    val mode: ScanMode,
    val packageName: String?,
    val reason: String,
) {
    val message: String
        get() = "Forbidden vendor import '$importStatement' at $file:$line " +
            "[module=${module.label}, mode=$mode, package=${packageName ?: "<none>"}]: $reason"
}
