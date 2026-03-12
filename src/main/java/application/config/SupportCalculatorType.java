package application.config;

/**
 * Enum for different support calculator types
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
     * Parse support calculator type from string (case-insensitive)
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

    public String[] getAliases() {
        return aliases;
    }
}
