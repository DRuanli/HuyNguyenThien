package application.config;

/**
 * Enumeration of support calculator types available in TUFCI.
 *
 * Support calculation computes the expected support of an itemset in uncertain databases
 * using polynomial convolution of probability distributions.
 *
 * Each type offers different trade-offs between time complexity and implementation approach:
 * - DIRECT: Simple dynamic programming, best for small transactions
 * - RECURSIVE: Divide-and-conquer, good general-purpose algorithm
 * - PARALLEL: Parallel D&C using Fork/Join, best for multi-core systems
 * - FFT: Fast Fourier Transform-based, optimal for long transactions
 * - PARALLEL_FFT: Parallel FFT, combines FFT efficiency with parallelism
 * - AUTO: Automatically selects based on parallelization mode
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public enum SupportCalculatorType {
    /**
     * Direct Convolution Support Calculator - O(n²) DP
     */
    DIRECT("direct", "DCS", "DirectConvolutionSupport"),

    /**
     * Recursive Convolution Support Calculator - O(n² log n) sequential
     */
    RECURSIVE("recursive", "RCS", "RecursiveConvolutionSupport"),

    /**
     * Parallel Recursive Convolution Support Calculator - O(n² log n / p) parallel
     */
    PARALLEL("parallel", "PRC", "ParallelRecursiveConvolution"),

    /**
     * FFT Convolution Support Calculator - O(n log² n) FFT-based
     */
    FFT("fft", "FFT", "FFTConvolutionSupport"),

    /**
     * Parallel FFT Convolution Support Calculator - O(n log² n / p) parallel FFT
     */
    PARALLEL_FFT("parallelfft", "PFFT", "ParallelFFTConvolution"),

    /**
     * Auto mode - let the system choose based on parallelization mode
     */
    AUTO("auto");

    private final String[] aliases;

    SupportCalculatorType(String... aliases) {
        this.aliases = aliases;
    }

    /**
     * Parse support calculator type from string (case-insensitive).
     *
     * Accepts various aliases for each type:
     * - "direct", "DCS", "DirectConvolutionSupport" → DIRECT
     * - "recursive", "RCS", "RecursiveConvolutionSupport" → RECURSIVE
     * - "parallel", "PRC", "ParallelRecursiveConvolution" → PARALLEL
     * - "fft", "FFT", "FFTConvolutionSupport" → FFT
     * - "parallelfft", "PFFT", "ParallelFFTConvolution" → PARALLEL_FFT
     * - "auto" → AUTO
     *
     * @param value the string to parse (case-insensitive, whitespace-trimmed)
     * @return the corresponding SupportCalculatorType
     * @throws IllegalArgumentException if value is not a valid type
     */
    public static SupportCalculatorType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return AUTO;
        }

        String normalized = value.trim().toLowerCase();

        for (SupportCalculatorType type : values()) {
            for (String alias : type.aliases) {
                if (alias.toLowerCase().equals(normalized)) {
                    return type;
                }
            }
        }

        throw new IllegalArgumentException(
            "Unknown support calculator type: " + value +
            ". Valid options: direct, recursive, parallel, fft, parallelfft, auto"
        );
    }

    /**
     * Get all string aliases for this calculator type.
     *
     * @return array of alias strings that can be used to reference this type
     */
    public String[] getAliases() {
        return aliases;
    }
}
