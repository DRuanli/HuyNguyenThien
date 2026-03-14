package domain.support;

import domain.model.Tidset;
import shared.Constants;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * ParallelRecursiveConvolutionSupportCalculator - Parallel hierarchical probabilistic support calculator.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * PARALLEL ALGORITHM OVERVIEW
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * This is a parallel version of RecursiveConvolutionSupportCalculator that uses
 * Java's Fork/Join framework to parallelize the divide-and-conquer recursion.
 *
 * Key Improvements:
 * 1. Left and right subtrees computed in PARALLEL (not sequential)
 * 2. Work-stealing algorithm distributes load across CPU cores
 * 3. Adaptive threshold prevents overhead on small tasks
 * 4. Configurable parallelism for scalability experiments
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * PARALLELIZATION STRATEGY
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Sequential version:
 *   leftDist = compute(left)   ← Blocks
 *   rightDist = compute(right) ← Waits for left
 *   return convolve(left, right)
 *
 * Parallel version (Fork/Join):
 *   leftTask.fork()            ← Async execution
 *   rightDist = compute(right) ← Work in current thread
 *   leftDist = leftTask.join() ← Wait if needed
 *   return convolve(left, right)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * PERFORMANCE CHARACTERISTICS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Complexity:
 *   Sequential: O(n² log n)
 *   Parallel (p cores): O(n² log n / p) with optimal load balancing
 *
 * Speedup:
 *   - Small tidsets (< 50):  ~1.0x (overhead dominates)
 *   - Medium tidsets (100-500): ~2-3x on 4 cores
 *   - Large tidsets (> 1000): ~3-4x on 4 cores
 *
 * Memory:
 *   Parallel version creates more temporary arrays simultaneously.
 *   Each forked task allocates O(n) space for its distribution.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * CONFIGURABLE PARALLELISM (NEW)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * This class now supports configurable parallelism for precise control in experiments:
 *
 *   // Use common pool (default)
 *   new ParallelRecursiveConvolutionSupportCalculator(tau);
 *
 *   // Use custom pool with 4 threads (for scalability experiments)
 *   new ParallelRecursiveConvolutionSupportCalculator(tau, 4);
 *
 * Resource management:
 *   Custom pools must be shutdown() when done to prevent resource leaks.
 *   Common pool is managed by JVM and should NOT be shutdown.
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public class ParallelRecursiveConvolutionSupportCalculator extends AbstractSupportCalculator implements AutoCloseable {

    /**
     * Minimum number of transactions to justify parallel processing.
     * Below this threshold, sequential processing is faster due to:
     * - Thread creation overhead
     * - Task scheduling overhead
     * - Context switching costs
     *
     * Tuning guide:
     * - Fast CPU + many cores: Lower threshold (32-64)
     * - Slow CPU + few cores: Higher threshold (128-256)
     * - Default: 64 (balanced for most systems)
     */
    private static final int PARALLEL_THRESHOLD = 64;

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
    public ParallelRecursiveConvolutionSupportCalculator(double tau, int parallelism) {
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
    public ParallelRecursiveConvolutionSupportCalculator(double tau) {
        this(tau, 0);  // 0 = use common pool
    }

    /**
     * Compute probabilistic support from dense probability array.
     *
     * @param transactionProbs probability array for all transactions
     * @param minRequiredSupport hint for early termination (unused in this implementation)
     * @return probabilistic support value
     */
    @Override
    public int computeProbabilisticSupport(double[] transactionProbs, int minRequiredSupport) {
        // ═════════════════════════════════════════════════════════════════
        // STEP 1: Compute probability distribution using PARALLEL D&C
        // ═════════════════════════════════════════════════════════════════
        double[] distribution = parallelDivideAndConquer(transactionProbs, 0, transactionProbs.length);

        // ═════════════════════════════════════════════════════════════════
        // STEP 2: Convert distribution to frequentness (cumulative prob)
        // ═════════════════════════════════════════════════════════════════
        double[] frequentness = computeFrequentness(distribution);

        // ═════════════════════════════════════════════════════════════════
        // STEP 3: Find maximum support where P(support >= s) >= τ
        // ═════════════════════════════════════════════════════════════════
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
        // Compute full distribution using parallel algorithm
        double[] distribution = parallelDivideAndConquer(transactionProbs, 0, transactionProbs.length);
        double[] frequentness = computeFrequentness(distribution);

        // Return frequentness at requested support level
        if (support >= 0 && support < frequentness.length) {
            return frequentness[support];
        }
        return 0.0;
    }

    /**
     * Compute both support and probability in single call (optimized).
     *
     * @param transactionProbs probability array
     * @return [support, probability]
     */
    @Override
    public double[] computeProbabilisticSupportWithFrequentness(double[] transactionProbs) {
        // Single distribution computation using parallel algorithm
        double[] distribution = parallelDivideAndConquer(transactionProbs, 0, transactionProbs.length);
        double[] frequentness = computeFrequentness(distribution);

        // Extract both values
        int support = findProbabilisticSupport(frequentness);
        double probability = (support >= 0 && support < frequentness.length)
                           ? frequentness[support] : 0.0;

        return new double[]{support, probability};
    }

    /**
     * Compute support and probability from sparse tidset.
     *
     * @param tidset sparse transaction ID set with probabilities
     * @param totalTransactions total transactions (unused, for interface compatibility)
     * @return [support, probability]
     */
    @Override
    public double[] computeProbabilisticSupportFromTidset(Tidset tidset, int totalTransactions) {
        // ═════════════════════════════════════════════════════════════════
        // EMPTY TIDSET CHECK
        // ═════════════════════════════════════════════════════════════════
        if (tidset.isEmpty()) {
            return new double[]{0, 0.0};
        }

        // ═════════════════════════════════════════════════════════════════
        // EXTRACT PROBABILITIES: Convert sparse Tidset to dense array
        // ═════════════════════════════════════════════════════════════════
        int m = tidset.size();
        double[] probs = new double[m];
        int idx = 0;

        for (Tidset.TIDProb entry : tidset.getEntries()) {
            probs[idx++] = entry.prob;
        }

        // ═════════════════════════════════════════════════════════════════
        // COMPUTE: Run PARALLEL divide-and-conquer on extracted probabilities
        // ═════════════════════════════════════════════════════════════════
        return computeProbabilisticSupportWithFrequentness(probs);
    }

    /**
     * Get strategy name for logging.
     *
     * @return strategy name
     */
    @Override
    public String getStrategyName() {
        return "Parallel Divide & Conquer (Fork/Join with Naive Convolution)";
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * PARALLEL DIVIDE AND CONQUER ENTRY POINT
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Launches parallel computation using Fork/Join framework.
     *
     * @param probs probability array
     * @param start start index (inclusive)
     * @param end end index (exclusive)
     * @return probability distribution where result[s] = P(support = s)
     */
    private double[] parallelDivideAndConquer(double[] probs, int start, int end) {
        int length = end - start;

        // ═════════════════════════════════════════════════════════════════
        // THRESHOLD CHECK: Use sequential for small tasks
        // ═════════════════════════════════════════════════════════════════
        // Parallel overhead not worth it for small problems
        if (length < PARALLEL_THRESHOLD) {
            return sequentialDivideAndConquer(probs, start, end);
        }

        // ═════════════════════════════════════════════════════════════════
        // FORK/JOIN: Launch parallel computation using instance pool
        // ═════════════════════════════════════════════════════════════════
        ConvolutionTask task = new ConvolutionTask(probs, start, end);
        return pool.invoke(task);  // Use instance pool (not static)
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * SEQUENTIAL DIVIDE AND CONQUER (Fallback for small tasks)
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Identical to RecursiveConvolutionSupportCalculator.divideAndConquer()
     * Used when task is too small to benefit from parallelization.
     *
     * @param probs probability array
     * @param start start index (inclusive)
     * @param end end index (exclusive)
     * @return probability distribution where result[s] = P(support = s)
     */
    private double[] sequentialDivideAndConquer(double[] probs, int start, int end) {
        int length = end - start;

        // ─────────────────────────────────────────────────────────────────
        // BASE CASE: Single transaction
        // ─────────────────────────────────────────────────────────────────
        if (length == 1) {
            double p = probs[start];
            if (p < Constants.MIN_PROB) {
                return new double[]{1.0, 0.0};
            }
            return new double[]{1.0 - p, p};
        }

        // ─────────────────────────────────────────────────────────────────
        // DIVIDE: Split at midpoint
        // ─────────────────────────────────────────────────────────────────
        int mid = start + length / 2;

        // ─────────────────────────────────────────────────────────────────
        // CONQUER: Recursively compute distributions (SEQUENTIAL)
        // ─────────────────────────────────────────────────────────────────
        double[] leftDist = sequentialDivideAndConquer(probs, start, mid);
        double[] rightDist = sequentialDivideAndConquer(probs, mid, end);

        // ─────────────────────────────────────────────────────────────────
        // COMBINE: Merge via convolution
        // ─────────────────────────────────────────────────────────────────
        return naiveConvolve(leftDist, rightDist);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * FORK/JOIN TASK: Parallel Convolution Computation
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * RecursiveTask implements the Fork/Join pattern:
     * 1. Fork: Split work into subtasks and execute asynchronously
     * 2. Compute: Work on one subtask in current thread
     * 3. Join: Wait for async subtask to complete
     * 4. Combine: Merge results
     *
     * The ForkJoinPool uses work-stealing:
     * - Idle threads "steal" tasks from busy threads' queues
     * - Provides automatic load balancing
     * - Maximizes CPU utilization
     */
    private class ConvolutionTask extends RecursiveTask<double[]> {
        private final double[] probs;
        private final int start;
        private final int end;

        /**
         * Constructor.
         *
         * @param probs probability array (shared across all tasks)
         * @param start start index for this task
         * @param end end index for this task
         */
        public ConvolutionTask(double[] probs, int start, int end) {
            this.probs = probs;
            this.start = start;
            this.end = end;
        }

        /**
         * ═══════════════════════════════════════════════════════════════
         * COMPUTE: Core Fork/Join computation method
         * ═══════════════════════════════════════════════════════════════
         *
         * Called by ForkJoinPool to execute this task.
         * Returns the probability distribution for transactions [start, end).
         *
         * @return probability distribution array
         */
        @Override
        protected double[] compute() {
            int length = end - start;

            // ═════════════════════════════════════════════════════════════
            // BASE CASE: Single transaction
            // ═════════════════════════════════════════════════════════════
            if (length == 1) {
                double p = probs[start];
                if (p < Constants.MIN_PROB) {
                    return new double[]{1.0, 0.0};
                }
                return new double[]{1.0 - p, p};
            }

            // ═════════════════════════════════════════════════════════════
            // THRESHOLD CHECK: Switch to sequential for small tasks
            // ═════════════════════════════════════════════════════════════
            // Avoid overhead of creating tasks for tiny problems
            if (length < PARALLEL_THRESHOLD) {
                return sequentialDivideAndConquer(probs, start, end);
            }

            // ═════════════════════════════════════════════════════════════
            // DIVIDE: Split into left and right subtasks
            // ═════════════════════════════════════════════════════════════
            int mid = start + length / 2;

            ConvolutionTask leftTask = new ConvolutionTask(probs, start, mid);
            ConvolutionTask rightTask = new ConvolutionTask(probs, mid, end);

            // ═════════════════════════════════════════════════════════════
            // FORK: Execute left task asynchronously
            // ═════════════════════════════════════════════════════════════
            // leftTask.fork() adds task to ForkJoinPool's work queue
            // Another thread (possibly idle) can steal and execute it
            leftTask.fork();

            // ═════════════════════════════════════════════════════════════
            // COMPUTE: Execute right task in current thread
            // ═════════════════════════════════════════════════════════════
            // Don't fork both tasks - keep current thread busy
            // This is the "work-stealing" optimization
            double[] rightDist = rightTask.compute();

            // ═════════════════════════════════════════════════════════════
            // JOIN: Wait for left task to complete
            // ═════════════════════════════════════════════════════════════
            // If left task already finished, join() returns immediately
            // Otherwise, current thread waits (or steals other work)
            double[] leftDist = leftTask.join();

            // ═════════════════════════════════════════════════════════════
            // COMBINE: Merge the two distributions
            // ═════════════════════════════════════════════════════════════
            return naiveConvolve(leftDist, rightDist);
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * NAIVE CONVOLUTION: Direct computation of discrete convolution
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Identical to RecursiveConvolutionSupportCalculator.naiveConvolve()
     *
     * Computes: result[s] = Σ_{i=0}^{s} a[i] × b[s-i]
     *
     * Time Complexity: O(|a| × |b|)
     * Space Complexity: O(|a| + |b| - 1)
     *
     * @param a first probability distribution where a[i] = P(X = i)
     * @param b second probability distribution where b[j] = P(Y = j)
     * @return convolution result where result[s] = P(X + Y = s)
     */
    private double[] naiveConvolve(double[] a, double[] b) {
        int lenA = a.length;
        int lenB = b.length;

        // Result array size: max sum is (lenA-1) + (lenB-1)
        double[] result = new double[lenA + lenB - 1];

        // Double loop: compute all pairwise products
        for (int i = 0; i < lenA; i++) {
            for (int j = 0; j < lenB; j++) {
                // Accumulate contribution to result[i+j]
                result[i + j] += a[i] * b[j];
            }
        }

        return result;
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * PERFORMANCE TUNING: Get current parallel threshold
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Useful for benchmarking and tuning.
     *
     * @return current parallel threshold value
     */
    public static int getParallelThreshold() {
        return PARALLEL_THRESHOLD;
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * DIAGNOSTICS: Get Fork/Join pool parallelism level
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Returns number of threads in this calculator's pool.
     *
     * @return parallelism level (number of worker threads)
     */
    public int getPoolParallelism() {
        return pool.getParallelism();
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * RESOURCE MANAGEMENT: Shutdown custom pool
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Shuts down the ForkJoinPool if this instance owns it (custom pool).
     * Does nothing if using common pool (managed by JVM).
     *
     * Should be called when calculator is no longer needed to prevent resource leaks.
     * After shutdown, this calculator cannot be used for further computations.
     *
     * Example usage:
     * <pre>
     * try (ParallelRecursiveConvolutionSupportCalculator calc =
     *          new ParallelRecursiveConvolutionSupportCalculator(0.7, 4)) {
     *     // Use calculator
     *     calc.computeProbabilisticSupport(...);
     * }  // Automatically calls close() → shutdown()
     * </pre>
     */
    public void shutdown() {
        if (ownsPool && !pool.isShutdown()) {
            pool.shutdown();
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * AUTOCLOSEABLE INTERFACE: Automatic resource management
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * Implements AutoCloseable to support try-with-resources.
     * Delegates to shutdown().
     */
    @Override
    public void close() {
        shutdown();
    }
}
