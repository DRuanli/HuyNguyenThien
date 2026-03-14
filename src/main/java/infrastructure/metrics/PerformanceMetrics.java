package infrastructure.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * PerformanceMetrics - Comprehensive performance measurement for IEEE publication standards.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * IEEE PUBLICATION REQUIREMENTS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * This class captures all metrics required for rigorous experimental evaluation
 * in IEEE/ACM publications:
 *
 * 1. TIMING METRICS:
 *    - Total execution time
 *    - Per-phase execution time (Phase 1, 2, 3)
 *    - Candidate generation time
 *    - Support calculation time
 *    - Closure checking time
 *
 * 2. ALGORITHM METRICS:
 *    - Number of candidates examined
 *    - Number of frequent patterns found
 *    - Number of closed patterns found
 *    - Cache hit rate
 *    - Pruning effectiveness
 *
 * 3. PARALLEL METRICS:
 *    - Number of cores used
 *    - Speedup: S(p) = T_seq / T_par(p)
 *    - Efficiency: E(p) = S(p) / p × 100%
 *    - Parallel overhead
 *
 * 4. DATASET CHARACTERISTICS:
 *    - Number of transactions
 *    - Vocabulary size
 *    - Average transaction length
 *    - Database density
 *
 * 5. SYSTEM CONFIGURATION:
 *    - Parallelization mode
 *    - Support calculator type
 *    - Probability threshold (tau)
 *    - Top-K value
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public class PerformanceMetrics {

    // ═══════════════════════════════════════════════════════════════
    // TIMING METRICS (nanosecond precision for accuracy)
    // ═══════════════════════════════════════════════════════════════

    /** Total execution time in nanoseconds */
    private long totalTimeNanos = 0;

    /** Phase 1 execution time (singleton itemset generation) in nanoseconds */
    private long phase1TimeNanos = 0;

    /** Phase 2 execution time (2-itemset closure checking) in nanoseconds */
    private long phase2TimeNanos = 0;

    /** Phase 3 execution time (recursive mining) in nanoseconds */
    private long phase3TimeNanos = 0;

    /** Support calculation time in nanoseconds */
    private long supportCalcTimeNanos = 0;

    /** Closure checking time in nanoseconds */
    private long closureCheckTimeNanos = 0;

    // ═══════════════════════════════════════════════════════════════
    // ALGORITHM METRICS
    // ═══════════════════════════════════════════════════════════════

    /** Total number of candidate itemsets examined */
    private int candidatesExamined = 0;

    /** Number of frequent itemsets found (before closure checking) */
    private int frequentItemsetsFound = 0;

    /** Number of closed frequent itemsets found */
    private int closedItemsetsFound = 0;

    /** Number of itemsets pruned by threshold */
    private int itemsetsPruned = 0;

    /** Number of cache hits */
    private int cacheHits = 0;

    /** Number of cache misses */
    private int cacheMisses = 0;

    /** Maximum itemset size discovered */
    private int maxItemsetSize = 0;

    // ═══════════════════════════════════════════════════════════════
    // PARALLEL METRICS
    // ═══════════════════════════════════════════════════════════════

    /** Number of cores/threads used */
    private int coresUsed = 1;

    /** Parallelization mode used */
    private String parallelizationMode = "default";

    /** Support calculator type used */
    private String supportCalculatorType = "auto";

    // ═══════════════════════════════════════════════════════════════
    // DATASET CHARACTERISTICS
    // ═══════════════════════════════════════════════════════════════

    /** Number of transactions in database */
    private int transactionCount = 0;

    /** Size of vocabulary (number of unique items) */
    private int vocabularySize = 0;

    /** Average transaction length */
    private double avgTransactionLength = 0.0;

    /** Database density (% of cells that are non-zero) */
    private double databaseDensity = 0.0;

    // ═══════════════════════════════════════════════════════════════
    // ALGORITHM PARAMETERS
    // ═══════════════════════════════════════════════════════════════

    /** Probability threshold (tau) */
    private double tau = 0.0;

    /** Top-K parameter */
    private int topK = 0;

    /** Minimum support threshold (dynamic, from top-K heap) */
    private int minSupportThreshold = 0;

    // ═══════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════

    /** Dataset name */
    private String datasetName = "unknown";

    /** Timestamp of execution start */
    private long timestampMillis = System.currentTimeMillis();

    /** Run identifier (for multiple runs) */
    private int runId = 0;

    /** Additional custom metrics */
    private Map<String, Object> customMetrics = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Default constructor.
     */
    public PerformanceMetrics() {
        this.timestampMillis = System.currentTimeMillis();
    }

    /**
     * Constructor with basic parameters.
     */
    public PerformanceMetrics(String datasetName, int runId, double tau, int topK) {
        this.datasetName = datasetName;
        this.runId = runId;
        this.tau = tau;
        this.topK = topK;
        this.timestampMillis = System.currentTimeMillis();
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMING SETTERS
    // ═══════════════════════════════════════════════════════════════

    public void setTotalTimeNanos(long nanos) {
        this.totalTimeNanos = nanos;
    }

    public void setPhase1TimeNanos(long nanos) {
        this.phase1TimeNanos = nanos;
    }

    public void setPhase2TimeNanos(long nanos) {
        this.phase2TimeNanos = nanos;
    }

    public void setPhase3TimeNanos(long nanos) {
        this.phase3TimeNanos = nanos;
    }

    public void setSupportCalcTimeNanos(long nanos) {
        this.supportCalcTimeNanos = nanos;
    }

    public void setClosureCheckTimeNanos(long nanos) {
        this.closureCheckTimeNanos = nanos;
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMING GETTERS (nanoseconds for precision)
    // ═══════════════════════════════════════════════════════════════

    public long getTotalTimeNanos() {
        return totalTimeNanos;
    }

    public long getPhase1TimeNanos() {
        return phase1TimeNanos;
    }

    public long getPhase2TimeNanos() {
        return phase2TimeNanos;
    }

    public long getPhase3TimeNanos() {
        return phase3TimeNanos;
    }

    public long getSupportCalcTimeNanos() {
        return supportCalcTimeNanos;
    }

    public long getClosureCheckTimeNanos() {
        return closureCheckTimeNanos;
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMING GETTERS (milliseconds for readability)
    // ═══════════════════════════════════════════════════════════════

    public double getTotalTimeMillis() {
        return totalTimeNanos / 1_000_000.0;
    }

    public double getPhase1TimeMillis() {
        return phase1TimeNanos / 1_000_000.0;
    }

    public double getPhase2TimeMillis() {
        return phase2TimeNanos / 1_000_000.0;
    }

    public double getPhase3TimeMillis() {
        return phase3TimeNanos / 1_000_000.0;
    }

    public double getSupportCalcTimeMillis() {
        return supportCalcTimeNanos / 1_000_000.0;
    }

    public double getClosureCheckTimeMillis() {
        return closureCheckTimeNanos / 1_000_000.0;
    }

    // ═══════════════════════════════════════════════════════════════
    // ALGORITHM METRICS SETTERS/GETTERS
    // ═══════════════════════════════════════════════════════════════

    public void setCandidatesExamined(int count) {
        this.candidatesExamined = count;
    }

    public void incrementCandidatesExamined() {
        this.candidatesExamined++;
    }

    public int getCandidatesExamined() {
        return candidatesExamined;
    }

    public void setFrequentItemsetsFound(int count) {
        this.frequentItemsetsFound = count;
    }

    public int getFrequentItemsetsFound() {
        return frequentItemsetsFound;
    }

    public void setClosedItemsetsFound(int count) {
        this.closedItemsetsFound = count;
    }

    public int getClosedItemsetsFound() {
        return closedItemsetsFound;
    }

    public void setItemsetsPruned(int count) {
        this.itemsetsPruned = count;
    }

    public void incrementItemsetsPruned() {
        this.itemsetsPruned++;
    }

    public int getItemsetsPruned() {
        return itemsetsPruned;
    }

    public void setCacheHits(int hits) {
        this.cacheHits = hits;
    }

    public void incrementCacheHits() {
        this.cacheHits++;
    }

    public int getCacheHits() {
        return cacheHits;
    }

    public void setCacheMisses(int misses) {
        this.cacheMisses = misses;
    }

    public void incrementCacheMisses() {
        this.cacheMisses++;
    }

    public int getCacheMisses() {
        return cacheMisses;
    }

    public void setMaxItemsetSize(int size) {
        if (size > this.maxItemsetSize) {
            this.maxItemsetSize = size;
        }
    }

    public int getMaxItemsetSize() {
        return maxItemsetSize;
    }

    // ═══════════════════════════════════════════════════════════════
    // PARALLEL METRICS SETTERS/GETTERS
    // ═══════════════════════════════════════════════════════════════

    public void setCoresUsed(int cores) {
        this.coresUsed = cores;
    }

    public int getCoresUsed() {
        return coresUsed;
    }

    public void setParallelizationMode(String mode) {
        this.parallelizationMode = mode;
    }

    public String getParallelizationMode() {
        return parallelizationMode;
    }

    public void setSupportCalculatorType(String type) {
        this.supportCalculatorType = type;
    }

    public String getSupportCalculatorType() {
        return supportCalculatorType;
    }

    // ═══════════════════════════════════════════════════════════════
    // DATASET CHARACTERISTICS SETTERS/GETTERS
    // ═══════════════════════════════════════════════════════════════

    public void setTransactionCount(int count) {
        this.transactionCount = count;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setVocabularySize(int size) {
        this.vocabularySize = size;
    }

    public int getVocabularySize() {
        return vocabularySize;
    }

    public void setAvgTransactionLength(double avgLength) {
        this.avgTransactionLength = avgLength;
    }

    public double getAvgTransactionLength() {
        return avgTransactionLength;
    }

    public void setDatabaseDensity(double density) {
        this.databaseDensity = density;
    }

    public double getDatabaseDensity() {
        return databaseDensity;
    }

    // ═══════════════════════════════════════════════════════════════
    // ALGORITHM PARAMETERS SETTERS/GETTERS
    // ═══════════════════════════════════════════════════════════════

    public void setTau(double tau) {
        this.tau = tau;
    }

    public double getTau() {
        return tau;
    }

    public void setTopK(int k) {
        this.topK = k;
    }

    public int getTopK() {
        return topK;
    }

    public void setMinSupportThreshold(int threshold) {
        this.minSupportThreshold = threshold;
    }

    public int getMinSupportThreshold() {
        return minSupportThreshold;
    }

    // ═══════════════════════════════════════════════════════════════
    // METADATA SETTERS/GETTERS
    // ═══════════════════════════════════════════════════════════════

    public void setDatasetName(String name) {
        this.datasetName = name;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setRunId(int id) {
        this.runId = id;
    }

    public int getRunId() {
        return runId;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM METRICS
    // ═══════════════════════════════════════════════════════════════

    public void setCustomMetric(String key, Object value) {
        this.customMetrics.put(key, value);
    }

    public Object getCustomMetric(String key) {
        return this.customMetrics.get(key);
    }

    public Map<String, Object> getAllCustomMetrics() {
        return new HashMap<>(customMetrics);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPUTED METRICS (IEEE REQUIREMENTS)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate speedup compared to baseline.
     *
     * IEEE Definition: S(p) = T_sequential / T_parallel(p)
     *
     * @param baseline sequential baseline metrics
     * @return speedup factor
     */
    public double calculateSpeedup(PerformanceMetrics baseline) {
        if (baseline.totalTimeNanos == 0) {
            return 0.0;
        }
        return (double) baseline.totalTimeNanos / this.totalTimeNanos;
    }

    /**
     * Calculate parallel efficiency.
     *
     * IEEE Definition: E(p) = S(p) / p × 100%
     *
     * @param baseline sequential baseline metrics
     * @return efficiency percentage (0-100+)
     */
    public double calculateEfficiency(PerformanceMetrics baseline) {
        if (this.coresUsed == 0) {
            return 0.0;
        }
        double speedup = calculateSpeedup(baseline);
        return (speedup / this.coresUsed) * 100.0;
    }

    /**
     * Calculate cache hit rate.
     *
     * @return cache hit rate as percentage (0-100)
     */
    public double getCacheHitRate() {
        int total = cacheHits + cacheMisses;
        if (total == 0) {
            return 0.0;
        }
        return ((double) cacheHits / total) * 100.0;
    }

    /**
     * Calculate pruning effectiveness.
     *
     * @return percentage of candidates pruned (0-100)
     */
    public double getPruningEffectiveness() {
        int total = candidatesExamined + itemsetsPruned;
        if (total == 0) {
            return 0.0;
        }
        return ((double) itemsetsPruned / total) * 100.0;
    }

    /**
     * Calculate parallel overhead.
     *
     * Overhead = T_parallel(p) - T_sequential / p
     *
     * Negative overhead indicates super-linear speedup (rare).
     *
     * @param baseline sequential baseline metrics
     * @return overhead in milliseconds
     */
    public double calculateParallelOverhead(PerformanceMetrics baseline) {
        if (this.coresUsed == 0 || baseline.totalTimeNanos == 0) {
            return 0.0;
        }
        double idealParallelTime = baseline.totalTimeNanos / (double) this.coresUsed;
        double overhead = this.totalTimeNanos - idealParallelTime;
        return overhead / 1_000_000.0;  // Convert to milliseconds
    }

    /**
     * Calculate time distribution percentages.
     *
     * @return array [phase1%, phase2%, phase3%]
     */
    public double[] getTimeDistribution() {
        if (totalTimeNanos == 0) {
            return new double[]{0.0, 0.0, 0.0};
        }

        double phase1Pct = (phase1TimeNanos / (double) totalTimeNanos) * 100.0;
        double phase2Pct = (phase2TimeNanos / (double) totalTimeNanos) * 100.0;
        double phase3Pct = (phase3TimeNanos / (double) totalTimeNanos) * 100.0;

        return new double[]{phase1Pct, phase2Pct, phase3Pct};
    }

    // ═══════════════════════════════════════════════════════════════
    // STRING REPRESENTATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PerformanceMetrics[\n");
        sb.append(String.format("  Dataset: %s (Run %d)\n", datasetName, runId));
        sb.append(String.format("  Parameters: tau=%.2f, k=%d\n", tau, topK));
        sb.append(String.format("  Parallelization: %s (%d cores)\n", parallelizationMode, coresUsed));
        sb.append(String.format("  Total Time: %.2f ms\n", getTotalTimeMillis()));
        sb.append(String.format("    Phase 1: %.2f ms (%.1f%%)\n",
            getPhase1TimeMillis(), getTimeDistribution()[0]));
        sb.append(String.format("    Phase 2: %.2f ms (%.1f%%)\n",
            getPhase2TimeMillis(), getTimeDistribution()[1]));
        sb.append(String.format("    Phase 3: %.2f ms (%.1f%%)\n",
            getPhase3TimeMillis(), getTimeDistribution()[2]));
        sb.append(String.format("  Candidates: %d examined, %d pruned (%.1f%% effective)\n",
            candidatesExamined, itemsetsPruned, getPruningEffectiveness()));
        sb.append(String.format("  Results: %d frequent, %d closed\n",
            frequentItemsetsFound, closedItemsetsFound));
        sb.append(String.format("  Cache: %d hits, %d misses (%.1f%% hit rate)\n",
            cacheHits, cacheMisses, getCacheHitRate()));
        sb.append("]");
        return sb.toString();
    }

    /**
     * Get CSV header for export.
     *
     * @return comma-separated header string
     */
    public static String getCSVHeader() {
        return "timestamp,run_id,dataset,transactions,vocabulary," +
               "tau,topK,mode,support_calc,cores," +
               "total_ms,phase1_ms,phase2_ms,phase3_ms," +
               "support_calc_ms,closure_check_ms," +
               "candidates_examined,frequent_found,closed_found," +
               "pruned,cache_hits,cache_misses,cache_hit_rate," +
               "max_itemset_size,min_support_threshold," +
               "avg_transaction_length,database_density";
    }

    /**
     * Convert metrics to CSV row.
     *
     * @return comma-separated values string
     */
    public String toCSV() {
        return String.format("%d,%d,%s,%d,%d," +
                           "%.2f,%d,%s,%s,%d," +
                           "%.3f,%.3f,%.3f,%.3f," +
                           "%.3f,%.3f," +
                           "%d,%d,%d," +
                           "%d,%d,%d,%.2f," +
                           "%d,%d," +
                           "%.2f,%.4f",
            timestampMillis, runId, datasetName, transactionCount, vocabularySize,
            tau, topK, parallelizationMode, supportCalculatorType, coresUsed,
            getTotalTimeMillis(), getPhase1TimeMillis(), getPhase2TimeMillis(), getPhase3TimeMillis(),
            getSupportCalcTimeMillis(), getClosureCheckTimeMillis(),
            candidatesExamined, frequentItemsetsFound, closedItemsetsFound,
            itemsetsPruned, cacheHits, cacheMisses, getCacheHitRate(),
            maxItemsetSize, minSupportThreshold,
            avgTransactionLength, databaseDensity
        );
    }
}
