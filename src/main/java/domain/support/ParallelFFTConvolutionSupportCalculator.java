package domain.support;

import domain.model.Tidset;
import shared.Constants;
import infrastructure.math.FFT;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * ParallelFFTConvolutionSupportCalculator - Parallel FFT-based probabilistic support calculator.
 *
 * ==================== PARALLEL FFT ALGORITHM OVERVIEW ====================
 *
 * This calculator combines the asymptotic efficiency of FFT (O(n log² n)) with
 * the parallelism of Fork/Join framework, achieving O(n log² n / p) complexity
 * on p cores.
 *
 * Key Features:
 * 1. FFT-based polynomial multiplication (O(n log n) per multiplication)
 * 2. Parallel divide-and-conquer recursion using Fork/Join
 * 3. Optimal for very large tidsets (> 5,000 transactions)
 * 4. Configurable parallelism for scalability experiments
 *
 * ==================== COMPARISON WITH OTHER CALCULATORS ====================
 *
 * Method                          | Complexity        | Best For
 * --------------------------------|-------------------|-------------------------
 * DirectConvolution               | O(n²)             | n < 50
 * RecursiveConvolution            | O(n² log n)       | n < 100
 * ParallelRecursiveConvolution    | O(n² log n / p)   | 100 < n < 10,000
 * FFTConvolution                  | O(n log² n)       | n > 5,000 (sequential)
 * ParallelFFTConvolution (THIS)   | O(n log² n / p)   | n > 10,000 (parallel)
 *
 * ==================== PARALLELIZATION STRATEGY ====================
 *
 * Sequential FFT version:
 *   leftDist = multiplyRange(left)   ← Blocks
 *   rightDist = multiplyRange(right) ← Waits for left
 *   return FFT.multiply(left, right) ← Sequential combine
 *
 * Parallel version (Fork/Join):
 *   leftTask.fork()                  ← Async execution
 *   rightDist = multiplyRange(right) ← Work in current thread
 *   leftDist = leftTask.join()       ← Wait if needed
 *   return FFT.multiply(left, right) ← FFT combine (O(n log n))
 *
 * ==================== PERFORMANCE CHARACTERISTICS ====================
 *
 * Complexity:
 *   Sequential FFT: O(n log² n)
 *   Parallel FFT (p cores): O(n log² n / p)
 *
 * Expected Speedup on 4 cores:
 *   - Small tidsets (< 100):     ~0.8x (overhead dominates)
 *   - Medium tidsets (100-1000): ~2.0x
 *   - Large tidsets (1000-5000): ~2.5x
 *   - Huge tidsets (> 10,000):   ~3.5x
 *
 * Memory:
 *   Parallel version creates more temporary arrays simultaneously.
 *   Peak memory: O(p × n) where p = active parallel tasks
 *
 * ==================== CONFIGURABLE PARALLELISM (NEW) ====================
 *
 * This class now supports configurable parallelism for precise control in experiments:
 *
 *   // Use common pool (default)
 *   new ParallelFFTConvolutionSupportCalculator(tau);
 *
 *   // Use custom pool with 4 threads (for scalability experiments)
 *   new ParallelFFTConvolutionSupportCalculator(tau, 4);
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public class ParallelFFTConvolutionSupportCalculator extends AbstractSupportCalculator implements AutoCloseable {

    /**
     * Minimum number of polynomials to justify parallel processing.
     *
     * Lower threshold than ParallelRecursiveConvolution (64) because:
     * - FFT multiplication is already fast O(n log n)
     * - Each multiplication does more work, justifying earlier parallelization
     * - Overhead is amortized better with FFT's efficiency
     *
     * Tuning guide:
     * - Fast CPU + many cores: 16
     * - Moderate CPU: 32
     * - Slow CPU + few cores: 64
     * - Default: 32 (balanced)
     */
    private static final int PARALLEL_THRESHOLD = 32;

    /**
     * Fork/Join pool for parallel computation.
     * Can be either common pool (shared) or custom pool (owned by this instance).
     */
    private final ForkJoinPool pool;

    /**
     * Flag indicating whether this instance owns the pool.
     * If true, we must shutdown() the pool when done.
     * If false, the pool is the common pool managed by JVM.
     */
    private final boolean ownsPool;

    /**
     * Constructor with configurable parallelism.
     *
     * @param tau probability threshold (0 < τ ≤ 1)
     * @param parallelism number of worker threads (0 or negative = use common pool)
     */
    public ParallelFFTConvolutionSupportCalculator(double tau, int parallelism) {
        super(tau);

        if (parallelism <= 0) {
            // Use JVM's common pool (shared, managed by JVM)
            this.pool = ForkJoinPool.commonPool();
            this.ownsPool = false;
        } else {
            // Create custom pool with specified parallelism
            this.pool = new ForkJoinPool(parallelism);
            this.ownsPool = true;
        }
    }

    /**
     * Constructor using common pool (backward compatible).
     *
     * @param tau probability threshold (0 < τ ≤ 1)
     */
    public ParallelFFTConvolutionSupportCalculator(double tau) {
        this(tau, 0);  // 0 = use common pool
    }

    /**
     * Compute probabilistic support using parallel FFT-based polynomial multiplication.
     *
     * @param transactionProbs probability array for all transactions
     * @param minRequiredSupport hint for early termination (unused)
     * @return probabilistic support value
     */
    @Override
    public int computeProbabilisticSupport(double[] transactionProbs, int minRequiredSupport) {
        // Step 1: Compute distribution via parallel FFT
        double[] distribution = computeDistributionViaParallelFFT(transactionProbs);

        // Step 2: Convert to frequentness (inherited from AbstractSupportCalculator)
        double[] frequentness = computeFrequentness(distribution);

        // Step 3: Binary search for max support (inherited)
        return findProbabilisticSupport(frequentness);
    }

    /**
     * Compute probability P(support ≥ s) for given support value.
     *
     * @param transactionProbs probability array
     * @param support target support value
     * @return P(support ≥ s)
     */
    @Override
    public double computeProbability(double[] transactionProbs, int support) {
        double[] distribution = computeDistributionViaParallelFFT(transactionProbs);
        double[] frequentness = computeFrequentness(distribution);

        if (support >= 0 && support < frequentness.length) {
            return frequentness[support];
        }
        return 0.0;
    }

    /**
     * Get strategy name for logging.
     *
     * @return "Parallel Divide & Conquer (Fork/Join with FFT)"
     */
    @Override
    public String getStrategyName() {
        return "Parallel Divide & Conquer (Fork/Join with FFT)";
    }

    /**
     * Compute both support and probability in single call (optimized).
     *
     * @param transactionProbs probability array
     * @return [support, probability]
     */
    @Override
    public double[] computeProbabilisticSupportWithFrequentness(double[] transactionProbs) {
        // Single distribution computation
        double[] distribution = computeDistributionViaParallelFFT(transactionProbs);
        double[] frequentness = computeFrequentness(distribution);

        int support = findProbabilisticSupport(frequentness);
        double probability = (support >= 0 && support < frequentness.length)
                           ? frequentness[support] : 0.0;

        return new double[]{support, probability};
    }

    /**
     * Compute support and probability from sparse tidset.
     *
     * @param tidset sparse transaction ID set with probabilities
     * @param totalTransactions total transactions (unused)
     * @return [support, probability]
     */
    @Override
    public double[] computeProbabilisticSupportFromTidset(Tidset tidset, int totalTransactions) {
        if (tidset.isEmpty()) {
            return new double[]{0, 0.0};
        }

        // Extract only non-zero probabilities (sparse)
        int m = tidset.size();
        double[] probs = new double[m];
        int idx = 0;
        for (Tidset.TIDProb entry : tidset.getEntries()) {
            probs[idx++] = entry.prob;
        }

        // Parallel FFT on sparse array (size m, not totalTransactions)
        return computeProbabilisticSupportWithFrequentness(probs);
    }

    /**
     * ==================== CORE: Compute distribution using parallel FFT-based polynomial multiplication ====================
     *
     * Algorithm:
     *   1. Filter out near-zero probabilities
     *   2. Convert each probability p to polynomial [1-p, p]
     *   3. Multiply all polynomials using PARALLEL divide-and-conquer + FFT
     *   4. Result coefficients = probability distribution
     *
     * @param transactionProbs probability array indexed by transaction ID
     * @return distribution where distribution[s] = P(support = s)
     */
    private double[] computeDistributionViaParallelFFT(double[] transactionProbs) {
        // -------------------- STEP 1: Filter out near-zero probabilities --------------------
        double[] filteredProbs = filterProbabilities(transactionProbs);

        // Handle empty case
        if (filteredProbs.length == 0) {
            return new double[] {1.0};
        }

        // -------------------- STEP 2: Convert each probability to polynomial coefficients --------------------
        // Transaction with probability p contributes: (1-p) + p·x
        double[][] polynomials = new double[filteredProbs.length][];

        for (int i = 0; i < filteredProbs.length; i++) {
            double p = filteredProbs[i];
            polynomials[i] = new double[] {1.0 - p, p};
        }

        // -------------------- STEP 3: Multiply all polynomials using PARALLEL divide-and-conquer --------------------
        double[] result = multiplyAllPolynomialsParallel(polynomials);

        return result;
    }

    /**
     * Filter probabilities to remove near-zero and invalid values.
     *
     * @param probs input probability array
     * @return filtered array with only valid probabilities
     */
    private double[] filterProbabilities(double[] probs) {
        // Count valid probabilities
        int count = 0;
        for (double p : probs) {
            if (p >= Constants.MIN_PROB && p <= 1.0) {
                count++;
            }
        }

        // Create filtered array
        double[] filtered = new double[count];
        int idx = 0;

        for (double p : probs) {
            if (p >= Constants.MIN_PROB && p <= 1.0) {
                filtered[idx++] = p;
            }
        }

        return filtered;
    }

    /**
     * ==================== PARALLEL ENTRY POINT: Multiply all polynomials using Fork/Join ====================
     *
     * @param polynomials array of polynomial coefficient arrays
     * @return product of all polynomials
     */
    private double[] multiplyAllPolynomialsParallel(double[][] polynomials) {
        // Edge case: no polynomials
        if (polynomials.length == 0) {
            return new double[] {1.0};  // Multiplicative identity
        }

        // Base case: single polynomial
        if (polynomials.length == 1) {
            return polynomials[0];
        }

        // Check if we should use parallel processing
        if (polynomials.length < PARALLEL_THRESHOLD) {
            // Use sequential for small tasks
            return multiplyRangeSequential(polynomials, 0, polynomials.length);
        }

        // Launch parallel computation using instance pool
        FFTMultiplicationTask task = new FFTMultiplicationTask(polynomials, 0, polynomials.length);
        return pool.invoke(task);  // Use instance pool (not static)
    }

    /**
     * ==================== SEQUENTIAL FALLBACK: Multiply polynomials in range [start, end) ====================
     *
     * Used for small tasks below parallel threshold.
     * Identical to FFTConvolutionSupportCalculator.multiplyRange() but sequential.
     *
     * @param polynomials all polynomials
     * @param start start index (inclusive)
     * @param end end index (exclusive)
     * @return product of polynomials[start..end-1]
     */
    private double[] multiplyRangeSequential(double[][] polynomials, int start, int end) {
        int count = end - start;

        // Base case: single polynomial
        if (count == 1) {
            return polynomials[start];
        }

        // Base case: two polynomials
        if (count == 2) {
            return FFT.multiplyPolynomials(polynomials[start], polynomials[start + 1]);
        }

        // Recursive case: divide and conquer
        int mid = start + count / 2;

        // Sequential recursion
        double[] left = multiplyRangeSequential(polynomials, start, mid);
        double[] right = multiplyRangeSequential(polynomials, mid, end);

        // Combine using FFT
        return FFT.multiplyPolynomials(left, right);
    }

    /**
     * ==================== FORK/JOIN TASK: Parallel FFT Multiplication ====================
     *
     * RecursiveTask implements the Fork/Join pattern:
     * 1. Fork: Split work into subtasks and execute asynchronously
     * 2. Compute: Work on one subtask in current thread
     * 3. Join: Wait for async subtask to complete
     * 4. Combine: Merge results using FFT
     *
     * The ForkJoinPool uses work-stealing for automatic load balancing.
     */
    private class FFTMultiplicationTask extends RecursiveTask<double[]> {
        private final double[][] polynomials;
        private final int start;
        private final int end;

        /**
         * Constructor.
         *
         * @param polynomials polynomial array (shared across all tasks)
         * @param start start index for this task
         * @param end end index for this task
         */
        public FFTMultiplicationTask(double[][] polynomials, int start, int end) {
            this.polynomials = polynomials;
            this.start = start;
            this.end = end;
        }

        /**
         * ==================== COMPUTE: Core Fork/Join computation method ====================
         *
         * Called by ForkJoinPool to execute this task.
         * Returns the product of polynomials [start, end).
         *
         * @return product polynomial
         */
        @Override
        protected double[] compute() {
            int count = end - start;

            // ==================== BASE CASE 1: Single polynomial ====================
            if (count == 1) {
                return polynomials[start];
            }

            // ==================== BASE CASE 2: Two polynomials - multiply directly with FFT ====================
            if (count == 2) {
                return FFT.multiplyPolynomials(polynomials[start], polynomials[start + 1]);
            }

            // ==================== THRESHOLD CHECK: Switch to sequential for small tasks ====================
            if (count < PARALLEL_THRESHOLD) {
                return multiplyRangeSequential(polynomials, start, end);
            }

            // ==================== DIVIDE: Split into left and right subtasks ====================
            int mid = start + count / 2;

            FFTMultiplicationTask leftTask = new FFTMultiplicationTask(polynomials, start, mid);
            FFTMultiplicationTask rightTask = new FFTMultiplicationTask(polynomials, mid, end);

            // ==================== FORK: Execute left task asynchronously ====================
            leftTask.fork();

            // ==================== COMPUTE: Execute right task in current thread ====================
            double[] rightResult = rightTask.compute();

            // ==================== JOIN: Wait for left task to complete ====================
            double[] leftResult = leftTask.join();

            // ==================== COMBINE: Merge using FFT multiplication (O(n log n)) ====================
            return FFT.multiplyPolynomials(leftResult, rightResult);
        }
    }

    /**
     * ==================== PERFORMANCE TUNING: Get current parallel threshold ====================
     *
     * @return current parallel threshold value
     */
    public static int getParallelThreshold() {
        return PARALLEL_THRESHOLD;
    }

    /**
     * ==================== DIAGNOSTICS: Get Fork/Join pool parallelism level ====================
     *
     * Returns number of threads in this calculator's pool.
     *
     * @return parallelism level (number of worker threads)
     */
    public int getPoolParallelism() {
        return pool.getParallelism();
    }

    /**
     * ==================== RESOURCE MANAGEMENT: Shutdown custom pool ====================
     *
     * Shuts down the ForkJoinPool if this instance owns it (custom pool).
     * Does nothing if using common pool (managed by JVM).
     *
     * Should be called when calculator is no longer needed to prevent resource leaks.
     */
    public void shutdown() {
        if (ownsPool && !pool.isShutdown()) {
            pool.shutdown();
        }
    }

    /**
     * ==================== AUTOCLOSEABLE INTERFACE: Automatic resource management ====================
     *
     * Implements AutoCloseable to support try-with-resources.
     * Delegates to shutdown().
     */
    @Override
    public void close() {
        shutdown();
    }
}
