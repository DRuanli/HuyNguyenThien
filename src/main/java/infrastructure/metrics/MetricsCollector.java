package infrastructure.metrics;

/**
 * MetricsCollector - Automatic instrumentation helper for TUFCI algorithm
 *
 * Provides convenient methods for collecting performance metrics during mining:
 * - Automatic phase timing with try-with-resources
 * - Candidate counting
 * - Cache tracking
 * - Itemset tracking
 *
 * Design Pattern: Builder + Fluent Interface
 * Thread Safety: NOT thread-safe (single-threaded use per instance)
 *
 * Example Usage:
 * <pre>
 * MetricsCollector collector = new MetricsCollector(metrics);
 *
 * // Automatic phase timing
 * try (PhaseTimer timer = collector.startPhase1()) {
 *     // Phase 1 code...
 *     collector.incrementCandidatesExamined(10);
 * } // Phase 1 time automatically recorded
 *
 * // Manual timing
 * collector.startSupportCalculation();
 * double support = calculateSupport(...);
 * collector.endSupportCalculation();
 *
 * // Tracking
 * collector.recordFrequentItemset();
 * collector.recordCacheHit();
 * </pre>
 *
 * @author TUFCI Team
 * @version 1.0
 */
public class MetricsCollector {

    private final PerformanceMetrics metrics;

    // Current phase timers (for nested timing)
    private long currentPhase1Start = 0;
    private long currentPhase2Start = 0;
    private long currentPhase3Start = 0;
    private long currentSupportCalcStart = 0;
    private long currentClosureCheckStart = 0;

    // Flags to prevent double-start/stop
    private boolean phase1Running = false;
    private boolean phase2Running = false;
    private boolean phase3Running = false;
    private boolean supportCalcRunning = false;
    private boolean closureCheckRunning = false;

    /**
     * Creates a MetricsCollector wrapping the given PerformanceMetrics instance.
     *
     * @param metrics The PerformanceMetrics instance to populate
     */
    public MetricsCollector(PerformanceMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("PerformanceMetrics cannot be null");
        }
        this.metrics = metrics;
    }

    // ============================================================================
    // Phase Timing (Try-With-Resources Support)
    // ============================================================================

    /**
     * Starts Phase 1 timing.
     * Returns a PhaseTimer that automatically stops timing when closed.
     *
     * @return PhaseTimer for automatic timing
     */
    public PhaseTimer startPhase1() {
        if (phase1Running) {
            throw new IllegalStateException("Phase 1 is already running");
        }
        currentPhase1Start = System.nanoTime();
        phase1Running = true;
        return new PhaseTimer(() -> endPhase1());
    }

    /**
     * Ends Phase 1 timing and records duration.
     */
    private void endPhase1() {
        if (!phase1Running) {
            return; // Already stopped
        }
        long elapsed = System.nanoTime() - currentPhase1Start;
        metrics.setPhase1TimeNanos(metrics.getPhase1TimeNanos() + elapsed);
        phase1Running = false;
    }

    /**
     * Starts Phase 2 timing.
     *
     * @return PhaseTimer for automatic timing
     */
    public PhaseTimer startPhase2() {
        if (phase2Running) {
            throw new IllegalStateException("Phase 2 is already running");
        }
        currentPhase2Start = System.nanoTime();
        phase2Running = true;
        return new PhaseTimer(() -> endPhase2());
    }

    /**
     * Ends Phase 2 timing and records duration.
     */
    private void endPhase2() {
        if (!phase2Running) {
            return;
        }
        long elapsed = System.nanoTime() - currentPhase2Start;
        metrics.setPhase2TimeNanos(metrics.getPhase2TimeNanos() + elapsed);
        phase2Running = false;
    }

    /**
     * Starts Phase 3 timing.
     *
     * @return PhaseTimer for automatic timing
     */
    public PhaseTimer startPhase3() {
        if (phase3Running) {
            throw new IllegalStateException("Phase 3 is already running");
        }
        currentPhase3Start = System.nanoTime();
        phase3Running = true;
        return new PhaseTimer(() -> endPhase3());
    }

    /**
     * Ends Phase 3 timing and records duration.
     */
    private void endPhase3() {
        if (!phase3Running) {
            return;
        }
        long elapsed = System.nanoTime() - currentPhase3Start;
        metrics.setPhase3TimeNanos(metrics.getPhase3TimeNanos() + elapsed);
        phase3Running = false;
    }

    // ============================================================================
    // Fine-Grained Timing (Support Calculation, Closure Checking)
    // ============================================================================

    /**
     * Starts support calculation timing.
     * Can be called multiple times (accumulates total time).
     */
    public void startSupportCalculation() {
        if (supportCalcRunning) {
            return; // Already running, don't restart
        }
        currentSupportCalcStart = System.nanoTime();
        supportCalcRunning = true;
    }

    /**
     * Ends support calculation timing.
     */
    public void endSupportCalculation() {
        if (!supportCalcRunning) {
            return;
        }
        long elapsed = System.nanoTime() - currentSupportCalcStart;
        metrics.setSupportCalcTimeNanos(metrics.getSupportCalcTimeNanos() + elapsed);
        supportCalcRunning = false;
    }

    /**
     * Starts closure checking timing.
     */
    public void startClosureCheck() {
        if (closureCheckRunning) {
            return;
        }
        currentClosureCheckStart = System.nanoTime();
        closureCheckRunning = true;
    }

    /**
     * Ends closure checking timing.
     */
    public void endClosureCheck() {
        if (!closureCheckRunning) {
            return;
        }
        long elapsed = System.nanoTime() - currentClosureCheckStart;
        metrics.setClosureCheckTimeNanos(metrics.getClosureCheckTimeNanos() + elapsed);
        closureCheckRunning = false;
    }

    /**
     * Convenience method for timing a single support calculation.
     *
     * @return SupportTimer for automatic timing
     */
    public SupportTimer timeSupportCalculation() {
        startSupportCalculation();
        return new SupportTimer(() -> endSupportCalculation());
    }

    /**
     * Convenience method for timing a single closure check.
     *
     * @return ClosureTimer for automatic timing
     */
    public ClosureTimer timeClosureCheck() {
        startClosureCheck();
        return new ClosureTimer(() -> endClosureCheck());
    }

    // ============================================================================
    // Algorithm Metrics Tracking
    // ============================================================================

    /**
     * Increments the count of candidates examined.
     *
     * @param count Number of candidates examined
     */
    public void incrementCandidatesExamined(int count) {
        metrics.setCandidatesExamined(metrics.getCandidatesExamined() + count);
    }

    /**
     * Records a single candidate examined.
     */
    public void recordCandidateExamined() {
        incrementCandidatesExamined(1);
    }

    /**
     * Records discovery of a frequent itemset.
     */
    public void recordFrequentItemset() {
        metrics.setFrequentItemsetsFound(metrics.getFrequentItemsetsFound() + 1);
    }

    /**
     * Records discovery of a closed itemset.
     */
    public void recordClosedItemset() {
        metrics.setClosedItemsetsFound(metrics.getClosedItemsetsFound() + 1);
    }

    /**
     * Records a pruned candidate (did not need to compute support).
     */
    public void recordPrunedCandidate() {
        metrics.setItemsetsPruned(metrics.getItemsetsPruned() + 1);
    }

    /**
     * Records a cache hit for support calculation.
     */
    public void recordCacheHit() {
        metrics.setCacheHits(metrics.getCacheHits() + 1);
    }

    /**
     * Records a cache miss for support calculation.
     */
    public void recordCacheMiss() {
        metrics.setCacheMisses(metrics.getCacheMisses() + 1);
    }

    /**
     * Updates the maximum itemset size if the given size is larger.
     *
     * @param size Itemset size to compare
     */
    public void updateMaxItemsetSize(int size) {
        if (size > metrics.getMaxItemsetSize()) {
            metrics.setMaxItemsetSize(size);
        }
    }

    /**
     * Updates the minimum support threshold if it changed.
     *
     * @param threshold New threshold value
     */
    public void updateMinSupportThreshold(int threshold) {
        metrics.setMinSupportThreshold(threshold);
    }

    // ============================================================================
    // Finalization
    // ============================================================================

    /**
     * Finalizes all timers and prepares metrics for export.
     * Should be called after mining completes.
     *
     * Stops any running timers and calculates total time.
     */
    public void complete() {
        // Stop any running timers
        if (phase1Running) endPhase1();
        if (phase2Running) endPhase2();
        if (phase3Running) endPhase3();
        if (supportCalcRunning) endSupportCalculation();
        if (closureCheckRunning) endClosureCheck();

        // Calculate total time
        long total = metrics.getPhase1TimeNanos() +
                     metrics.getPhase2TimeNanos() +
                     metrics.getPhase3TimeNanos();
        metrics.setTotalTimeNanos(total);
    }

    /**
     * Gets the underlying PerformanceMetrics instance.
     *
     * @return The PerformanceMetrics being populated
     */
    public PerformanceMetrics getMetrics() {
        return metrics;
    }

    // ============================================================================
    // AutoCloseable Timer Classes
    // ============================================================================

    /**
     * PhaseTimer - Automatically stops phase timing when closed.
     */
    public static class PhaseTimer implements AutoCloseable {
        private final Runnable onClose;
        private boolean closed = false;

        PhaseTimer(Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        public void close() {
            if (!closed) {
                onClose.run();
                closed = true;
            }
        }
    }

    /**
     * SupportTimer - Automatically stops support calculation timing when closed.
     */
    public static class SupportTimer implements AutoCloseable {
        private final Runnable onClose;
        private boolean closed = false;

        SupportTimer(Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        public void close() {
            if (!closed) {
                onClose.run();
                closed = true;
            }
        }
    }

    /**
     * ClosureTimer - Automatically stops closure check timing when closed.
     */
    public static class ClosureTimer implements AutoCloseable {
        private final Runnable onClose;
        private boolean closed = false;

        ClosureTimer(Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        public void close() {
            if (!closed) {
                onClose.run();
                closed = true;
            }
        }
    }
}
