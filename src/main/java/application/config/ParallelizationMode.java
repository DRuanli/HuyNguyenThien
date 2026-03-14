package application.config;

/**
 * Parallelization mode enumeration for controlling which parts of the mining algorithm run in parallel.
 *
 * ==================== PARALLELIZATION LEVELS ====================
 *
 * The TUFCI algorithm has multiple opportunities for parallelization:
 *
 * 1. PHASE 1 PARALLELIZATION:
 *    - Compute support for all singleton itemsets in parallel
 *    - Each item processed independently using parallel streams
 *    - Location: AbstractMiner.computeAllSingletonSupports()
 *
 * 2. SUPPORT CALCULATOR PARALLELIZATION:
 *    - Use Fork/Join framework to parallelize recursive convolution
 *    - Divide-and-conquer tree computed in parallel
 *    - Location: ParallelRecursiveConvolutionSupportCalculator
 *    - Used in: Phase 1, Phase 2, and Phase 3 support calculations
 *
 * 3. PHASE 2 CLOSURE CHECK PARALLELIZATION:
 *    - Parallel closure checking for 1-itemsets
 *    - Location: AbstractMiner.checkClosure1ItemsetParallel()
 *
 * 4. PHASE 3 EXTENSION GENERATION PARALLELIZATION:
 *    - Parallel extension generation and closure checking
 *    - Location: AbstractMiner.checkClosureAndGenerateExtensionsParallel()
 *
 * ==================== AVOIDING NESTED PARALLELISM ====================
 *
 * IMPORTANT: Modes that combine closure parallelization (3 or 4) with support calculator
 * parallelization (2) have been REMOVED due to nested parallelism issues:
 * - ONLY_PHASE2 (removed): closure parallel + support parallel → thread contention
 * - ONLY_PHASE3 (removed): extensions parallel + support parallel → thread contention
 * - FULL_PARALLEL (removed): everything parallel → severe thread contention
 *
 * Only non-nested modes remain:
 * - ONLY_CLOSURE: closure parallel, support sequential
 * - ONLY_SUPPORT: closure sequential, support parallel
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public enum ParallelizationMode {
    /**
     * DEFAULT: Fully sequential execution (default).
     *
     * Phase 1: Sequential
     * Support Calculator: Sequential
     * Phase 2 Closure: Sequential
     * Phase 3 Extensions: Sequential
     *
     * Use when:
     * - Single-core CPU
     * - Small datasets
     * - Debugging or baseline comparison
     */
    DEFAULT("default", "Fully sequential execution"),

    /**
     * ONLY_PHASE1: Parallelize only Phase 1 (singleton support computation).
     *
     * Phase 1: Parallel
     * Support Calculator: Sequential
     * Phase 2 Closure: Sequential
     * Phase 3 Extensions: Sequential
     *
     * Use when:
     * - Large vocabulary (many unique items)
     * - Sparse tidsets (items appear in few transactions)
     */
    ONLY_PHASE1("onlyPhase1", "Parallel Phase 1 only"),

    /**
     * ONLY_CLOSURE: Parallelize only closure checking (both Phase 2 and Phase 3).
     *
     * Phase 1: Sequential
     * Support Calculator: Sequential
     * Phase 2 Closure: Parallel
     * Phase 3 Extensions: Parallel
     *
     * Use when:
     * - Large vocabulary
     * - Deep searches
     * - Want to maximize closure checking performance only
     * - AVOIDS nested parallelism (support calculator is sequential)
     */
    ONLY_CLOSURE("onlyClosure", "Parallel closure checking only (Phase 2 + Phase 3)"),

    /**
     * ONLY_SUPPORT: Parallelize only the support calculator (all phases).
     *
     * Phase 1: Sequential
     * Support Calculator: Parallel
     * Phase 2 Closure: Sequential
     * Phase 3 Extensions: Sequential
     *
     * Use when:
     * - Dense tidsets (items appear in many transactions)
     * - Deep itemsets with large support calculations
     * - Want to optimize only support computation
     * - AVOIDS nested parallelism (closure checking is sequential)
     */
    ONLY_SUPPORT("onlySupport", "Parallel support calculator only");

    /** Command-line argument value */
    private final String cliValue;

    /** Human-readable description */
    private final String description;

    /**
     * Constructor.
     *
     * @param cliValue command-line argument value
     * @param description human-readable description
     */
    ParallelizationMode(String cliValue, String description) {
        this.cliValue = cliValue;
        this.description = description;
    }

    /**
     * Get the command-line argument value.
     *
     * @return CLI value (e.g., "none", "onlyPhase1", "onlySupport", "full")
     */
    public String getCliValue() {
        return cliValue;
    }

    /**
     * Get human-readable description.
     *
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Parse command-line argument to ParallelizationMode.
     *
     * Supported formats:
     * - "default" → DEFAULT
     * - "onlyPhase1" → ONLY_PHASE1
     * - "onlyPhase2" → ONLY_PHASE2
     * - "onlyPhase3" → ONLY_PHASE3
     * - "onlyClosure" → ONLY_CLOSURE
     * - "onlySupport" → ONLY_SUPPORT
     * - "fullParallel" → FULL_PARALLEL
     * - null or empty → DEFAULT (default)
     *
     * @param cliValue command-line argument value
     * @return corresponding ParallelizationMode
     * @throws IllegalArgumentException if cliValue is invalid
     */
    public static ParallelizationMode fromCliValue(String cliValue) {
        // Default to DEFAULT if no value provided
        if (cliValue == null || cliValue.trim().isEmpty()) {
            return DEFAULT;
        }

        // Try to match each enum value
        for (ParallelizationMode mode : values()) {
            if (mode.cliValue.equalsIgnoreCase(cliValue.trim())) {
                return mode;
            }
        }

        // Invalid value - throw exception with helpful message
        throw new IllegalArgumentException(
            String.format("Invalid parallelization mode: '%s'. Valid options: default, onlyPhase1, onlyClosure, onlySupport",
                         cliValue)
        );
    }

    /**
     * Check if Phase 1 should run in parallel.
     *
     * @return true if Phase 1 parallelization is enabled
     */
    public boolean isPhase1Parallel() {
        return this == ONLY_PHASE1;
    }

    /**
     * Check if support calculator should run in parallel.
     *
     * @return true if support calculator parallelization is enabled
     */
    public boolean isSupportCalculatorParallel() {
        return this == ONLY_SUPPORT;
    }

    /**
     * Check if Phase 2 closure checking should run in parallel.
     *
     * @return true if Phase 2 closure checking parallelization is enabled
     */
    public boolean isPhase2ClosureCheckParallel() {
        return this == ONLY_CLOSURE;
    }

    /**
     * Check if Phase 3 extension generation should run in parallel.
     *
     * @return true if Phase 3 extension generation parallelization is enabled
     */
    public boolean isPhase3ExtensionGenerationParallel() {
        return this == ONLY_CLOSURE;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", cliValue, description);
    }
}
