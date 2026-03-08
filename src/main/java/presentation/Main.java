package presentation;

import domain.observer.PhaseTimingObserver;
import infrastructure.factory.MinerFactory;
import domain.model.FrequentItemset;
import infrastructure.persistence.UncertainDatabase;
import presentation.Usage;
import domain.mining.TUFCI;
import application.config.ParallelizationMode;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1){
            Usage.printUsage();
        }

        // Parse positional arguments
        String dbFile = args[0];
        double tau = args.length > 1 ? Double.parseDouble(args[1]) : 0.7;
        int k = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        // Parse --parallel flag
        ParallelizationMode parallelMode = parseParallelizationMode(args);

        // Print configuration
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║          TUFCI: Top-K Uncertain Frequent Closed           ║");
        System.out.println("║              Itemset Mining Algorithm                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Database file     : " + dbFile);
        System.out.println("  Tau (threshold)   : " + tau);
        System.out.println("  K (top patterns)  : " + k);
        System.out.println("  Parallelization   : " + parallelMode.getDescription());
        System.out.println();

        System.out.println("Loading database...");
        UncertainDatabase database = UncertainDatabase.loadFromFile(dbFile);

        System.out.println("  Transactions : " + database.size());
        System.out.println("  Vocabulary   : " + database.getVocabulary().size() + " unique items");
        System.out.println();


        System.out.println("Creating TUFCI miner...");
        TUFCI miner = MinerFactory.createMiner(database, tau, k, parallelMode);

        PhaseTimingObserver observer = new PhaseTimingObserver();
        System.out.println("  Algorithm           : TUFCI");
        System.out.println("  Phase 1 Mode        : " + (parallelMode.isPhase1Parallel() ? "Parallel" : "Sequential"));
        System.out.println("  Support Calc Mode   : " + (parallelMode.isSupportCalculatorParallel() ? "Parallel" : "Sequential"));
        System.out.println("  Phase 2 Closure     : " + (parallelMode.isPhase2ClosureCheckParallel() ? "Parallel" : "Sequential"));
        System.out.println("  Phase 3 Extensions  : " + (parallelMode.isPhase3ExtensionGenerationParallel() ? "Parallel" : "Sequential"));
        System.out.println();

        System.out.println("Starting mining process...");
        System.out.println("─".repeat(65));

        // Get memory usage before mining
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection for more accurate measurement
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Record start time to measure performance
        long startTime = System.nanoTime();

        // Execute the mining algorithm
        List<FrequentItemset> results = miner.mine();

        long endTime = System.nanoTime();
        long executionTime = (endTime - startTime) / 1_000_000; // Convert to ms
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        System.out.println("─".repeat(65));
        System.out.println("Mining completed!");
        System.out.println();

        // ==================== Step 5: Display Performance Metrics ====================

        Usage.printPerformanceMetrics(observer, executionTime, memoryUsed,
                               database.size(), results.size());

        // ==================== Step 6: Display Results ====================

        Usage.printResults(results, k);

    }

    /**
     * Parse --parallel flag from command-line arguments.
     *
     * Supported formats:
     * - (no flag)                 → ParallelizationMode.DEFAULT (fully sequential)
     * - --parallel                → ParallelizationMode.FULL_PARALLEL (everything parallel)
     * - --parallel default        → ParallelizationMode.DEFAULT
     * - --parallel onlyPhase1     → ParallelizationMode.ONLY_PHASE1
     * - --parallel onlyPhase2     → ParallelizationMode.ONLY_PHASE2 (closure + support in Phase 2)
     * - --parallel onlyPhase3     → ParallelizationMode.ONLY_PHASE3 (extensions + support in Phase 3)
     * - --parallel onlyClosure    → ParallelizationMode.ONLY_CLOSURE (closure in Phase 2 & 3)
     * - --parallel onlySupport    → ParallelizationMode.ONLY_SUPPORT (support calculator only)
     * - --parallel fullParallel   → ParallelizationMode.FULL_PARALLEL
     *
     * @param args command-line arguments
     * @return parsed ParallelizationMode
     */
    private static ParallelizationMode parseParallelizationMode(String[] args) {
        // Search for --parallel flag
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--parallel")) {
                // Check if there's a value after the flag
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    // --parallel <value>
                    String value = args[i + 1];
                    try {
                        return ParallelizationMode.fromCliValue(value);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Error: " + e.getMessage());
                        System.err.println("Defaulting to full parallelization mode.");
                        return ParallelizationMode.FULL_PARALLEL;
                    }
                } else {
                    // --parallel (no value) → default to FULL_PARALLEL
                    return ParallelizationMode.FULL_PARALLEL;
                }
            }
        }

        // No --parallel flag found → default to DEFAULT (fully sequential)
        return ParallelizationMode.DEFAULT;
    }

}
