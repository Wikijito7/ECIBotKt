package architecture

data class SourceRootRule(
    val path: String,
    val module: SourceModule,
) {
    init {
        require(path.isNotEmpty()) { "A source root path cannot be empty" }
        require(!path.endsWith('/')) { "A source root path must not end with '/': $path" }
    }
}

data class ImportBoundaryConfig(
    val mode: ScanMode,
    val roots: List<SourceRootRule>,
    val excludedRoots: List<String> = emptyList(),
    val excludedOutputRoots: List<String> = emptyList(),
) {
    init {
        val duplicated = roots.groupingBy { it.path }.eachCount().filterValues { it > 1 }.keys
        require(duplicated.isEmpty()) { "Duplicated source root rules: $duplicated" }
        val invalidExclusions = excludedRoots.filter { it.endsWith('/') }
        require(invalidExclusions.isEmpty()) { "Excluded roots must not end with '/': $invalidExclusions" }
        val invalidOutputExclusions = excludedOutputRoots.filter { it.endsWith('/') }
        require(invalidOutputExclusions.isEmpty()) {
            "Excluded output roots must not end with '/': $invalidOutputExclusions"
        }
    }
}

object ImportBoundaryConfigs {

    private val PHASE_2_ROOTS = listOf(
        SourceRootRule("src/main/kotlin", SourceModule.ROOT),
        SourceRootRule("src/test/kotlin", SourceModule.ROOT),
        SourceRootRule("core/src/main/kotlin", SourceModule.CORE),
        SourceRootRule("core/src/test/kotlin", SourceModule.CORE),
        SourceRootRule("locale-persistence/src/main/kotlin", SourceModule.LOCALE_PERSISTENCE),
        SourceRootRule("locale-persistence/src/test/kotlin", SourceModule.LOCALE_PERSISTENCE),
        SourceRootRule("jda-runtime/src/main/kotlin", SourceModule.JDA_RUNTIME),
        SourceRootRule("jda-runtime/src/test/kotlin", SourceModule.JDA_RUNTIME),
    )

    private val SPIKE_ROOTS = listOf(
        SourceRootRule("spikes/jda-dave/src/main/kotlin", SourceModule.SPIKE),
        SourceRootRule("spikes/jda-dave/src/test/kotlin", SourceModule.SPIKE),
    )

    /**
     * Phase 2 scans the current production modules and explicitly excludes the
     * separately built `spikes/jda-dave` spike.
     */
    fun phase2(): ImportBoundaryConfig = ImportBoundaryConfig(
        mode = ScanMode.PHASE_2,
        roots = PHASE_2_ROOTS,
        excludedRoots = SPIKE_ROOTS.map { it.path },
    )

    /**
     * Phase 6 removes the root Kord exception. The optional spike row keeps its
     * separate non-production `spike.jdadave` allowance when the spike exists.
     */
    fun phase6(includeSpike: Boolean = true): ImportBoundaryConfig = ImportBoundaryConfig(
        mode = ScanMode.PHASE_6,
        roots = PHASE_2_ROOTS + if (includeSpike) SPIKE_ROOTS else emptyList(),
        excludedRoots = emptyList(),
    )
}
