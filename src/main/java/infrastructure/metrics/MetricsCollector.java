package infrastructure.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

/**
 * MetricsCollector - Automatic instrumentation helper for TUFCI algorithm
 *
 * Provides convenient methods for collecting performance metrics during mining:
 * - Automatic phase timing with try-with-resources
 * - Phase-level memory tracking (IEEE Access compliant)
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

    // Memory tracking (IEEE Access compliant - phase-level memory)
    private long memoryBeforePhase1 = 0;
    private long memoryBeforePhase2 = 0;
    private long memoryBeforePhase3 = 0;

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

    /**
     * Gets current heap memory usage across all heap pools.
     * IEEE Access Compliant: Uses MemoryPoolMXBean for accurate measurement.
     *
     * @return Current heap memory used in bytes
     */
    private static long getCurrentHeapMemory() {
        long totalCurrent = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage usage = pool.getUsage();
                if (usage != null) {
                    totalCurrent += usage.getUsed();
                }
            }
        }
        return totalCurrent;
    }

    // ============================================================================
    // Phase Timing (Try-With-Resources Support)
    // ============================================================================

    /**
     * Starts Phase 1 timing and memory tracking.
     * Returns a PhaseTimer that automatically stops timing when closed.
     *
     * @return PhaseTimer for automatic timing
     */
    public PhaseTimer startPhase1() {
        if (phase1Running) {
            throw new IllegalStateException("Phase 1 is already running");
        }
        currentPhase1Start = System.nanoTime();
        memoryBeforePhase1 = getCurrentHeapMemory();
        phase1Running = true;
        return new PhaseTimer(() -> endPhase1());
    }

    /**
     * Ends Phase 1 timing and memory tracking, records duration and memory delta.
     */
    private void endPhase1() {
        if (!phase1Running) {
            return; // Already stopped
        }
        long elapsed = System.nanoTime() - currentPhase1Start;
        metrics.setPhase1TimeNanos(metrics.getPhase1TimeNanos() + elapsed);

        // Track memory consumption for Phase 1
        long memoryAfterPhase1 = getCurrentHeapMemory();
        long memoryDelta = memoryAfterPhase1 - memoryBeforePhase1;
        metrics.setPhase1MemoryBytes(memoryDelta);

        phase1Running = false;
    }

    /**
     * Starts Phase 2 timing and memory tracking.
     *
     * @return PhaseTimer for automatic timing
     */
    public PhaseTimer startPhase2() {
        if (phase2Running) {
            throw new IllegalStateException("Phase 2 is already running");
        }
        currentPhase2Start = System.nanoTime();
        memoryBeforePhase2 = getCurrentHeapMemory();
        phase2Running = true;
        return new PhaseTimer(() -> endPhase2());
    }

    /**
     * Ends Phase 2 timing and memory tracking, records duration and memory delta.
     */
    private void endPhase2() {
        if (!phase2Running) {
            return;
        }
        long elapsed = System.nanoTime() - currentPhase2Start;
        metrics.setPhase2TimeNanos(metrics.getPhase2TimeNanos() + elapsed);

        // Track memory consumption for Phase 2
        long memoryAfterPhase2 = getCurrentHeapMemory();
        long memoryDelta = memoryAfterPhase2 - memoryBeforePhase2;
        metrics.setPhase2MemoryBytes(memoryDelta);

        phase2Running = false;
    }

    /**
     * Starts Phase 3 timing and memory tracking.
     *
     * @return PhaseTimer for automatic timing
     */
    public PhaseTimer startPhase3() {
        if (phase3Running) {
            throw new IllegalStateException("Phase 3 is already running");
        }
        currentPhase3Start = System.nanoTime();
        memoryBeforePhase3 = getCurrentHeapMemory();
        phase3Running = true;
        return new PhaseTimer(() -> endPhase3());
    }

    /**
     * Ends Phase 3 timing and memory tracking, records duration and memory delta.
     */
    private void endPhase3() {
        if (!phase3Running) {
            return;
        }
        long elapsed = System.nanoTime() - currentPhase3Start;
        metrics.setPhase3TimeNanos(metrics.getPhase3TimeNanos() + elapsed);

        // Track memory consumption for Phase 3
        long memoryAfterPhase3 = getCurrentHeapMemory();
        long memoryDelta = memoryAfterPhase3 - memoryBeforePhase3;
        metrics.setPhase3MemoryBytes(memoryDelta);

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

        // ============================================================================
        // Timer Validation
        // ============================================================================

        // Validate: Check for negative timing values
        if (metrics.getPhase1TimeNanos() < 0 ||
            metrics.getPhase2TimeNanos() < 0 ||
            metrics.getPhase3TimeNanos() < 0 ||
            metrics.getSupportCalcTimeNanos() < 0 ||
            metrics.getClosureCheckTimeNanos() < 0) {
            System.err.println("WARNING: Negative timing detected in metrics!");
            System.err.println("  Phase 1: " + metrics.getPhase1TimeNanos() + " ns");
            System.err.println("  Phase 2: " + metrics.getPhase2TimeNanos() + " ns");
            System.err.println("  Phase 3: " + metrics.getPhase3TimeNanos() + " ns");
            System.err.println("  Support Calc: " + metrics.getSupportCalcTimeNanos() + " ns");
            System.err.println("  Closure Check: " + metrics.getClosureCheckTimeNanos() + " ns");
        }

        // Validate: Fine-grained timings should not exceed total time (with tolerance)
        // Allow 10% tolerance for measurement overhead and thread synchronization
        long tolerance = (long) (total * 0.10);
        if (metrics.getSupportCalcTimeNanos() > total + tolerance) {
            System.err.println("WARNING: Support calculation time (" +
                metrics.getSupportCalcTimeMillis() + " ms) exceeds total time (" +
                metrics.getTotalTimeMillis() + " ms)");
        }
        if (metrics.getClosureCheckTimeNanos() > total + tolerance) {
            System.err.println("WARNING: Closure check time (" +
                metrics.getClosureCheckTimeMillis() + " ms) exceeds total time (" +
                metrics.getTotalTimeMillis() + " ms)");
        }

        // Validate: Total time should be reasonable (not zero if phases ran)
        if (total == 0 && (metrics.getCandidatesExamined() > 0 || metrics.getFrequentItemsetsFound() > 0)) {
            System.err.println("WARNING: Total time is zero but algorithm produced results. " +
                "This may indicate a timer malfunction.");
        }

        // ============================================================================
        // Memory Validation (IEEE Access Compliant)
        // ============================================================================

        // Validate: Check for negative memory deltas (should be rare but possible with GC)
        if (metrics.getPhase1MemoryBytes() < 0) {
            System.err.println("WARNING: Negative memory delta in Phase 1: " +
                metrics.getPhase1MemoryMB() + " MB");
            System.err.println("  This may indicate GC occurred during phase measurement.");
        }
        if (metrics.getPhase2MemoryBytes() < 0) {
            System.err.println("WARNING: Negative memory delta in Phase 2: " +
                metrics.getPhase2MemoryMB() + " MB");
            System.err.println("  This may indicate GC occurred during phase measurement.");
        }
        if (metrics.getPhase3MemoryBytes() < 0) {
            System.err.println("WARNING: Negative memory delta in Phase 3: " +
                metrics.getPhase3MemoryMB() + " MB");
            System.err.println("  This may indicate GC occurred during phase measurement.");
        }

        // Validate: Peak memory should be reasonable
        if (metrics.getPeakMemoryBytes() < 0) {
            System.err.println("WARNING: Negative peak memory detected!");
            System.err.println("  Peak Memory: " + metrics.getPeakMemoryMB() + " MB");
        }

        // Validate: Phase memory sum sanity check
        // Note: Phase memory deltas may not sum to peak due to GC during execution
        // This is informational, not an error
        long totalPhaseMemory = metrics.getPhase1MemoryBytes() +
                               metrics.getPhase2MemoryBytes() +
                               metrics.getPhase3MemoryBytes();

        // IEEE Access: Log memory breakdown for verification
        if (totalPhaseMemory > 0 && metrics.getPeakMemoryBytes() > 0) {
            double ratio = (double) totalPhaseMemory / metrics.getPeakMemoryBytes();
            if (ratio > 1.5) {
                System.err.println("INFO: Sum of phase memory deltas (" +
                    (totalPhaseMemory / (1024.0 * 1024.0)) + " MB) significantly exceeds " +
                    "peak memory (" + metrics.getPeakMemoryMB() + " MB).");
                System.err.println("  This is normal if GC freed memory between phases.");
            }
        }
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
