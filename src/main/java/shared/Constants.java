package shared;

public class Constants {
    /**
     * Epsilon value for floating-point comparisons.
     * Used to handle precision errors in probabilistic calculations.
     */
    public static final double EPSILON = 1e-10;

    /**
     * Minimum probability threshold for filtering near-zero probabilities.
     * Probabilities below this value are treated as zero.
     */
    public static final double MIN_PROB = 1e-10;

    /**
     * Default initial capacity for data structures.
     */
    public static final int DEFAULT_CAPACITY = 100;
}
