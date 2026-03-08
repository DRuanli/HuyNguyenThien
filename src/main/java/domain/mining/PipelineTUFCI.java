package domain.mining;

import domain.support.SupportCalculator;
import infrastructure.persistence.UncertainDatabase;
import domain.model.*;
import domain.support.ParallelRecursiveConvolutionSupportCalculator;
import application.config.ParallelizationMode;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PipelineTUFCI: Pipeline Parallelism Implementation (v2)
 *
 * <p>This class implements a multi-stage pipeline architecture to overlap different phases
 * of the mining process. While traditional parallelization (v1) parallelizes within each phase,
 * pipeline parallelism allows different candidates to be at different stages simultaneously.</p>
 *
 * <p><b>Pipeline Architecture:</b></p>
 * <pre>
 *   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
 *   │ Stage 1 │ -> │ Stage 2 │ -> │ Stage 3 │ -> │ Stage 4 │
 *   │Producer │    │ Support │    │ Closure │    │Consumer │
 *   └─────────┘    └─────────┘    └─────────┘    └─────────┘
 *       |              |              |              |
 *    PQ Poll      Compute Sup    Check Close    Add to Top-K
 *                                Gen Extensions  Update PQ
 * </pre>
 *
 * <p><b>Key Benefits:</b></p>
 * <ul>
 *   <li><b>Overlapped Execution:</b> While one candidate is having its support computed,
 *       another can be checked for closure, and a third can be added to Top-K</li>
 *   <li><b>Better CPU Utilization:</b> Reduces idle time by keeping all cores busy</li>
 *   <li><b>Improved Throughput:</b> Process multiple candidates concurrently at different stages</li>
 *   <li><b>Scalability:</b> Each stage can have multiple worker threads</li>
 * </ul>
 *
 * <p><b>Research Value:</b></p>
 * <p>This implementation represents a novel parallelization strategy for uncertain frequent
 * itemset mining that differs from traditional data parallelism approaches. It demonstrates
 * that pipeline parallelism can achieve competitive or superior performance by exploiting
 * stage-level concurrency.</p>
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public class PipelineTUFCI extends TUFCI {

    // ==================== Pipeline Configuration ====================

    /** Number of worker threads per stage (tune based on CPU cores) */
    private static final int STAGE_THREADS = 2;

    /** Queue capacity between stages (prevents memory overflow) */
    private static final int QUEUE_CAPACITY = 1000;

    /** Batch size for processing (reduces synchronization overhead) */
    private static final int BATCH_SIZE = 10;

    /** Poison pill to signal pipeline shutdown */
    private static final FrequentItemset POISON_PILL = new FrequentItemset(
        new Itemset(null), -1, -1.0
    );

    // ==================== Pipeline Components ====================

    /** Thread pool executor for pipeline stages */
    private ExecutorService executor;

    /** Queue between Stage 1 (Producer) and Stage 2 (Support) */
    private BlockingQueue<FrequentItemset> stage1To2Queue;

    /** Queue between Stage 2 (Support) and Stage 3 (Closure) */
    private BlockingQueue<FrequentItemset> stage2To3Queue;

    /** Queue between Stage 3 (Closure) and Stage 4 (Consumer) */
    private BlockingQueue<ClosureResult> stage3To4Queue;

    /** Thread-safe priority queue for new candidates */
    private PriorityBlockingQueue<FrequentItemset> threadSafePQ;

    /** Shutdown signal */
    private AtomicBoolean shutdownSignal;

    // ==================== Constructor ====================

    /**
     * Constructs a PipelineTUFCI miner with pipeline parallelism.
     *
     * @param database The uncertain database to mine
     * @param tau The probability threshold
     * @param k The number of top patterns to find
     */
    public PipelineTUFCI(UncertainDatabase database, double tau, int k) {
        super(database, tau, k, ParallelizationMode.PIPELINE);

        // Initialize pipeline components
        this.stage1To2Queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.stage2To3Queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.stage3To4Queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.shutdownSignal = new AtomicBoolean(false);
    }

    // ==================== Phase 3: Pipeline Execution ====================

    /**
     * Overrides Phase 3 to use pipeline parallelism instead of sequential or data parallelism.
     *
     * <p>Creates a 4-stage pipeline where different candidates are processed concurrently
     * at different stages. This overlaps computation and reduces idle time.</p>
     *
     * @param frequent1itemsets The list of 1-itemsets (not used in pipeline)
     */
    @Override
    protected void executePhase3(List<FrequentItemset> frequent1itemsets) {
        // Convert regular PQ to thread-safe PriorityBlockingQueue
        this.threadSafePQ = new PriorityBlockingQueue<>(1000, FrequentItemset::compareForPriorityQueue);

        // Transfer all items from regular PQ to thread-safe PQ
        java.lang.reflect.Field pqField;
        try {
            pqField = TUFCI.class.getDeclaredField("pq");
            pqField.setAccessible(true);
            PriorityQueue<FrequentItemset> regularPQ = (PriorityQueue<FrequentItemset>) pqField.get(this);

            while (!regularPQ.isEmpty()) {
                threadSafePQ.add(regularPQ.poll());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to access priority queue", e);
        }

        // Create thread pool for pipeline stages
        this.executor = Executors.newFixedThreadPool(STAGE_THREADS * 4);

        // Start all pipeline stages
        List<Future<?>> stageFutures = new ArrayList<>();

        // Stage 1: Producer (poll from PQ)
        for (int i = 0; i < STAGE_THREADS; i++) {
            stageFutures.add(executor.submit(this::stage1Producer));
        }

        // Stage 2: Support calculation (already computed, just pass through)
        for (int i = 0; i < STAGE_THREADS; i++) {
            stageFutures.add(executor.submit(this::stage2Support));
        }

        // Stage 3: Closure check and extension generation
        for (int i = 0; i < STAGE_THREADS; i++) {
            stageFutures.add(executor.submit(this::stage3Closure));
        }

        // Stage 4: Consumer (add to Top-K and update PQ)
        for (int i = 0; i < STAGE_THREADS; i++) {
            stageFutures.add(executor.submit(this::stage4Consumer));
        }

        // Wait for all stages to complete
        for (Future<?> future : stageFutures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Pipeline stage failed", e);
            }
        }

        // Shutdown executor
        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    // ==================== Pipeline Stage Implementations ====================

    /**
     * Stage 1: Producer - Poll candidates from priority queue.
     *
     * <p>Retrieves candidates from the thread-safe priority queue and forwards them
     * to Stage 2. Applies threshold pruning before forwarding.</p>
     */
    private void stage1Producer() {
        try {
            while (!shutdownSignal.get()) {
                // Poll candidate from priority queue (non-blocking with timeout)
                FrequentItemset candidate = threadSafePQ.poll(100, TimeUnit.MILLISECONDS);

                if (candidate == null) {
                    // Check if PQ is truly empty and no more work coming
                    if (threadSafePQ.isEmpty() && stage3To4Queue.isEmpty()) {
                        // Signal shutdown by sending poison pills
                        for (int i = 0; i < STAGE_THREADS; i++) {
                            stage1To2Queue.put(POISON_PILL);
                        }
                        break;
                    }
                    continue;
                }

                int threshold = getThreshold();

                // Threshold pruning: skip if support too low
                if (candidate.getSupport() < threshold) {
                    // All remaining candidates will also fail threshold
                    for (int i = 0; i < STAGE_THREADS; i++) {
                        stage1To2Queue.put(POISON_PILL);
                    }
                    break;
                }

                // Forward to Stage 2
                stage1To2Queue.put(candidate);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stage 2: Support Calculation (pass-through in current implementation).
     *
     * <p>In the current implementation, support is already computed when candidates
     * enter the PQ, so this stage just forwards candidates to Stage 3.</p>
     *
     * <p>Future optimization: Could recompute support here if needed for caching.</p>
     */
    private void stage2Support() {
        try {
            while (!shutdownSignal.get()) {
                FrequentItemset candidate = stage1To2Queue.poll(100, TimeUnit.MILLISECONDS);

                if (candidate == null) continue;

                if (candidate == POISON_PILL) {
                    // Forward poison pill to next stage
                    stage2To3Queue.put(POISON_PILL);
                    break;
                }

                // Support already computed, just forward
                // (Could add support recomputation logic here if needed)
                stage2To3Queue.put(candidate);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stage 3: Closure Check and Extension Generation.
     *
     * <p>The core computational stage:</p>
     * <ol>
     *   <li>Check if candidate is closed</li>
     *   <li>Generate all valid extensions in canonical order</li>
     *   <li>Forward results to Stage 4</li>
     * </ol>
     */
    private void stage3Closure() {
        try {
            while (!shutdownSignal.get()) {
                FrequentItemset candidate = stage2To3Queue.poll(100, TimeUnit.MILLISECONDS);

                if (candidate == null) continue;

                if (candidate == POISON_PILL) {
                    // Forward poison pill to next stage
                    stage3To4Queue.put(new ClosureResult(null, false, Collections.emptyList()));
                    break;
                }

                // Check closure and generate extensions
                int threshold = getThreshold();
                ClosureCheckResult result = checkClosureAndGenerateExtensions(candidate, threshold);

                // Package result with candidate and forward to Stage 4
                ClosureResult closureResult = new ClosureResult(
                    candidate,
                    result.isClosed(),
                    result.getExtensions()
                );
                stage3To4Queue.put(closureResult);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stage 4: Consumer - Add to Top-K and update priority queue.
     *
     * <p>Final stage that:</p>
     * <ol>
     *   <li>Inserts closed patterns into Top-K heap</li>
     *   <li>Adds extensions back to priority queue for future processing</li>
     *   <li>Maintains threshold for pruning</li>
     * </ol>
     */
    private void stage4Consumer() {
        try {
            int poisonCount = 0;

            while (!shutdownSignal.get()) {
                ClosureResult result = stage3To4Queue.poll(100, TimeUnit.MILLISECONDS);

                if (result == null) continue;

                // Check for poison pill (null candidate)
                if (result.candidate == null) {
                    poisonCount++;
                    if (poisonCount >= STAGE_THREADS) {
                        // All Stage 3 workers have finished
                        shutdownSignal.set(true);
                        break;
                    }
                    continue;
                }

                // Add to Top-K if closed
                if (result.isClosed) {
                    topK.insert(result.candidate);
                }

                // Add extensions to priority queue
                int newThreshold = getThreshold();
                for (FrequentItemset ext : result.extensions) {
                    if (ext.getSupport() >= newThreshold) {
                        threadSafePQ.add(ext);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Helper Classes ====================

    /**
     * Container for closure check results passed between Stage 3 and Stage 4.
     */
    private static class ClosureResult {
        final FrequentItemset candidate;
        final boolean isClosed;
        final List<FrequentItemset> extensions;

        ClosureResult(FrequentItemset candidate, boolean isClosed, List<FrequentItemset> extensions) {
            this.candidate = candidate;
            this.isClosed = isClosed;
            this.extensions = extensions;
        }
    }
}
