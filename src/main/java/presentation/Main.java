package presentation;

import domain.observer.PhaseTimingObserver;
import infrastructure.factory.MinerFactory;
import infrastructure.metrics.PerformanceMetrics;
import domain.model.FrequentItemset;
import infrastructure.persistence.UncertainDatabase;
import domain.mining.TUFCI;
import application.config.ParallelizationMode;
import application.config.SupportCalculatorType;

import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.File;
import java.util.List;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * Main entry point for TUFCI mining algorithm.
 *
 * This class orchestrates the mining process by:
 * 1. Parsing command-line arguments
 * 2. Loading the database
 * 3. Configuring and executing the mining algorithm
 * 4. Displaying and exporting results
 */
public class Main {

    private static final String SEPARATOR = "─".repeat(65);
    private static final int RUN_ID_MODULO = 100000;

    public static void main(String[] args) {
        try {
            if (shouldShowUsage(args)) {
                Usage.printUsage();
                return;
            }

            CommandLineConfig config = parseConfiguration(args);
            executeMiningPipeline(config);

        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Use --help for usage information");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Executes the complete mining pipeline.
     */
    private static void executeMiningPipeline(CommandLineConfig config) throws IOException {
        UncertainDatabase database = loadDatabase(config);
        TUFCI miner = configureMiner(config, database);

        MiningResult result = executeMining(miner, config);

        displayResults(config, database, result);
        exportResults(config, database, result);
    }

    /**
     * Loads and validates the database from file.
     */
    private static UncertainDatabase loadDatabase(CommandLineConfig config) throws IOException {
        printBanner();
        printConfiguration(config);

        System.out.println("Loading database...");
        UncertainDatabase database = UncertainDatabase.loadFromFile(config.databaseFile);

        System.out.printf("  Transactions : %d%n", database.size());
        System.out.printf("  Vocabulary   : %d unique items%n", database.getVocabulary().size());
        System.out.println();

        return database;
    }

    /**
     * Configures the TUFCI miner with appropriate settings.
     */
    private static TUFCI configureMiner(CommandLineConfig config, UncertainDatabase database) {
        System.out.println("Creating TUFCI miner...");

        TUFCI miner = MinerFactory.createMiner(
            database,
            config.tau,
            config.k,
            config.parallelMode,
            config.supportType
        );

        String datasetName = extractDatasetName(config.databaseFile);
        int runId = generateRunId();

        String actualCalculatorName = getActualCalculatorName(miner);
        miner.setMetricsInfo(datasetName, runId);
        miner.setCalculatorType(actualCalculatorName);

        printMinerConfiguration(config, actualCalculatorName);

        return miner;
    }

    /**
     * Executes the mining algorithm and collects metrics.
     */
    private static MiningResult executeMining(TUFCI miner, CommandLineConfig config) {
        System.out.println("Starting mining process...");
        System.out.println(SEPARATOR);

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();

        // ============================================================================
        // Enhanced GC with Verification (IEEE Access Compliant)
        // ============================================================================

        long gcCountBefore = getGCCount();

        // Enhanced GC: Multiple calls increase likelihood of actual execution
        runtime.gc();
        try {
            Thread.sleep(100); // Allow GC to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        runtime.gc(); // Second call for additional cleanup

        try {
            Thread.sleep(50); // Additional time for cleanup
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify GC actually executed
        long gcCountAfter = getGCCount();
        if (gcCountAfter <= gcCountBefore) {
            System.err.println("WARNING: Garbage collection may not have executed!");
            System.err.println("  GC count before: " + gcCountBefore);
            System.err.println("  GC count after: " + gcCountAfter);
        }

        // ============================================================================
        // Memory Baseline Establishment (Peak Tracking - IEEE Access Compliant)
        // ============================================================================

        // Reset peak memory tracking for all heap pools
        // This establishes baseline so we measure only algorithm memory consumption
        resetPeakMemoryUsage();
        long baselineMemory = getCurrentHeapMemory();

        long startTime = System.nanoTime();

        // Execute mining algorithm
        List<FrequentItemset> patterns = miner.mine();
        PerformanceMetrics metrics = miner.getMetrics();

        long endTime = System.nanoTime();
        long executionTimeMs = (endTime - startTime) / 1_000_000;

        // ============================================================================
        // Peak Memory Measurement (IEEE Access Compliant)
        // ============================================================================

        // Get ACTUAL peak heap memory usage during execution using MemoryPoolMXBean
        // This tracks the maximum memory used, even if later freed by GC
        long peakMemoryUsed = getPeakHeapMemory();

        // Also get current heap info for validation
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long currentMemory = heapUsage.getUsed();
        long peakMemoryCommitted = heapUsage.getCommitted();
        long maxMemory = heapUsage.getMax();

        // Calculate memory consumed by algorithm (peak - baseline)
        long memoryUsed = peakMemoryUsed - baselineMemory;

        // Validation: Ensure memory measurement is reasonable
        if (memoryUsed < 0) {
            System.err.println("WARNING: Negative memory usage detected!");
            System.err.println("  Baseline: " + (baselineMemory / (1024.0 * 1024.0)) + " MB");
            System.err.println("  Peak: " + (peakMemoryUsed / (1024.0 * 1024.0)) + " MB");
            System.err.println("  Current: " + (currentMemory / (1024.0 * 1024.0)) + " MB");
            memoryUsed = peakMemoryUsed; // Fallback to absolute peak
        }

        // IEEE Access: Validation that peak >= current (sanity check)
        if (peakMemoryUsed < currentMemory) {
            System.err.println("WARNING: Peak memory less than current memory!");
            System.err.println("  This indicates a measurement error.");
        }

        // IEEE Access: Log memory measurement details for reproducibility
        // (Enable this for debugging memory measurements)
        // System.out.println("\nMemory Measurement Details:");
        // System.out.printf("  Baseline Memory:  %,.2f MB%n", baselineMemory / (1024.0 * 1024.0));
        // System.out.printf("  Peak Memory:      %,.2f MB%n", peakMemoryUsed / (1024.0 * 1024.0));
        // System.out.printf("  Committed Heap:   %,.2f MB%n", peakMemoryCommitted / (1024.0 * 1024.0));
        // System.out.printf("  Max Heap:         %,.2f MB%n", maxMemory / (1024.0 * 1024.0));
        // System.out.printf("  Algorithm Usage:  %,.2f MB%n", memoryUsed / (1024.0 * 1024.0));

        // Set peak memory into metrics for ResultExporter
        if (metrics != null) {
            metrics.setPeakMemoryBytes(peakMemoryUsed);
        }

        System.out.println(SEPARATOR);
        System.out.println("Mining completed!");
        System.out.println();

        return new MiningResult(patterns, metrics, executionTimeMs, memoryUsed);
    }

    /**
     * Displays mining results to console.
     */
    private static void displayResults(CommandLineConfig config, UncertainDatabase database, MiningResult result) {
        if (config.quietMode) {
            printQuietModeOutput(config, database, result);
        } else {
            printVerboseOutput(config, database, result);
        }
    }

    /**
     * Exports results to specified output files.
     */
    private static void exportResults(CommandLineConfig config, UncertainDatabase database, MiningResult result) throws IOException {
        if (config.csvOutput != null) {
            exportMetricsToCSV(result.metrics, config.csvOutput);
            System.out.println("Performance metrics exported to: " + config.csvOutput);
        }

        if (config.latexOutput != null) {
            exportLaTeXTable(config, database, result);
            System.out.println("LaTeX table exported to: " + config.latexOutput);
        }

        if (config.patternsOutput != null) {
            ResultExporter.exportPatternsCSV(config.patternsOutput, result.patterns, config.k);
            System.out.println("Patterns exported to: " + config.patternsOutput);
        }
    }

    // ==================== Configuration Parsing ====================

    private static boolean shouldShowUsage(String[] args) {
        return args.length < 1 || hasFlag(args, "--help") || hasFlag(args, "-h");
    }

    private static CommandLineConfig parseConfiguration(String[] args) {
        return new CommandLineConfig(
            args[0],
            parseDouble(args, 1, 0.7),
            parseInt(args, 2, 5),
            parseParallelizationMode(args),
            parseSupportCalculatorType(args),
            parseStringFlag(args, "--output-csv"),
            parseStringFlag(args, "--output-latex"),
            parseStringFlag(args, "--output-patterns"),
            hasFlag(args, "--quiet")
        );
    }

    private static double parseDouble(String[] args, int index, double defaultValue) {
        return args.length > index ? Double.parseDouble(args[index]) : defaultValue;
    }

    private static int parseInt(String[] args, int index, int defaultValue) {
        return args.length > index ? Integer.parseInt(args[index]) : defaultValue;
    }

    private static ParallelizationMode parseParallelizationMode(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--parallel")) {
                String value = getNextArgument(args, i);
                return parseParallelMode(value);
            }
        }
        return ParallelizationMode.DEFAULT;
    }

    private static ParallelizationMode parseParallelMode(String value) {
        if (value == null) {
            return ParallelizationMode.ONLY_CLOSURE;
        }

        try {
            return ParallelizationMode.fromCliValue(value);
        } catch (IllegalArgumentException e) {
            System.err.println("Warning: " + e.getMessage());
            System.err.println("Defaulting to onlyClosure parallelization mode.");
            return ParallelizationMode.ONLY_CLOSURE;
        }
    }

    private static SupportCalculatorType parseSupportCalculatorType(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--support")) {
                String value = getNextArgument(args, i);
                return parseSupportType(value);
            }
        }
        return SupportCalculatorType.AUTO;
    }

    private static SupportCalculatorType parseSupportType(String value) {
        if (value == null) {
            return SupportCalculatorType.AUTO;
        }

        try {
            return SupportCalculatorType.fromString(value);
        } catch (IllegalArgumentException e) {
            System.err.println("Warning: " + e.getMessage());
            System.err.println("Defaulting to AUTO mode.");
            return SupportCalculatorType.AUTO;
        }
    }

    private static String parseStringFlag(String[] args, String flagName) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(flagName)) {
                String value = getNextArgument(args, i);
                if (value == null) {
                    System.err.println("Warning: " + flagName + " requires a value");
                }
                return value;
            }
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flagName) {
        for (String arg : args) {
            if (arg.equals(flagName)) {
                return true;
            }
        }
        return false;
    }

    private static String getNextArgument(String[] args, int currentIndex) {
        int nextIndex = currentIndex + 1;
        if (nextIndex < args.length && !args[nextIndex].startsWith("--")) {
            return args[nextIndex];
        }
        return null;
    }

    // ==================== Output Formatting ====================

    private static void printBanner() {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║          TUFCI: Top-K Uncertain Frequent Closed           ║");
        System.out.println("║          " +
                "    Itemset Mining Algorithm                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printConfiguration(CommandLineConfig config) {
        System.out.println("Configuration:");
        System.out.printf("  Database file     : %s%n", config.databaseFile);
        System.out.printf("  Tau (threshold)   : %.2f%n", config.tau);
        System.out.printf("  K (top patterns)  : %d%n", config.k);
        System.out.printf("  Parallelization   : %s%n", config.parallelMode.getDescription());
        System.out.printf("  Support Calculator: %s%n", describeSupportCalculator(config.supportType));
        System.out.println();
    }

    private static void printMinerConfiguration(CommandLineConfig config, String actualCalculator) {
        System.out.println("Mining Configuration Details:");
        System.out.println("─".repeat(65));
        System.out.printf("  %-25s : %s%n", "Algorithm", "TUFCI (Top-K Uncertain Frequent Closed Itemsets)");
        System.out.printf("  %-25s : %s%n", "Parallelization Mode", config.parallelMode.name());
        System.out.println();

        System.out.println("  Phase Execution Modes:");
        System.out.printf("    %-23s : %s%n", "Phase 1 (Singletons)",
            describeParallelMode(config.parallelMode.isPhase1Parallel()));
        System.out.printf("    %-23s : %s%n", "Phase 2 (Closure Check)",
            describeParallelMode(config.parallelMode.isPhase2ClosureCheckParallel()));
        System.out.printf("    %-23s : %s%n", "Phase 3 (Extensions)",
            describeParallelMode(config.parallelMode.isPhase3ExtensionGenerationParallel()));
        System.out.println();

        System.out.println("  Support Calculator:");
        System.out.printf("    %-23s : %s%n", "Type", actualCalculator);
        System.out.printf("    %-23s : %s%n", "Parallelized",
            describeYesNo(config.parallelMode.isSupportCalculatorParallel()));
        System.out.printf("    %-23s : %s%n", "Requested",
            config.supportType == SupportCalculatorType.AUTO ? "AUTO" : config.supportType.name());

        if (config.supportType == SupportCalculatorType.AUTO) {
            System.out.printf("    %-23s : %s%n", "Auto-Selected",
                getAutoSelectionReason(config.parallelMode));
        }

        System.out.println("─".repeat(65));
        System.out.println();
    }

    private static void printVerboseOutput(CommandLineConfig config, UncertainDatabase database, MiningResult result) {
        // Create observer from metrics
        PhaseTimingObserver observer = new PhaseTimingObserver();
        // Observer doesn't have setters, so we use metrics directly in printPublicationSummary

        ResultExporter.printPublicationSummary(
            config.databaseFile,
            database.size(),
            database.getVocabulary().size(),
            config.tau,
            config.k,
            config.parallelMode.name(),
            describeSupportCalculator(config.supportType),
            observer,
            result.executionTimeMs,
            result.memoryUsed,
            result.patterns.size(),
            result.metrics  // Pass metrics for phase-level data
        );

        Usage.printResults(result.patterns, config.k);
    }

    private static void printQuietModeOutput(CommandLineConfig config, UncertainDatabase database, MiningResult result) {
        PhaseTimingObserver observer = new PhaseTimingObserver();

        System.out.printf("%s,%d,%d,%d,%d,%d,%d,%d%n",
            config.databaseFile,
            database.size(),
            database.getVocabulary().size(),
            config.k,
            observer.getPhase1Time(),
            observer.getPhase2Time(),
            observer.getPhase3Time(),
            result.executionTimeMs
        );
    }

    private static String describeSupportCalculator(SupportCalculatorType type) {
        switch (type) {
            case DIRECT:
                return "Direct Convolution (O(n²) DP)";
            case RECURSIVE:
                return "Recursive Convolution (O(n² log n) Sequential)";
            case PARALLEL:
                return "Parallel Recursive Convolution (O(n² log n / p))";
            case FFT:
                return "FFT Convolution (O(n log² n))";
            case PARALLEL_FFT:
                return "Parallel FFT Convolution (O(n log² n / p))";
            case AUTO:
                return "Auto (based on parallelization mode)";
            default:
                return type.toString();
        }
    }

    private static String describeMode(boolean isParallel) {
        return isParallel ? "Parallel" : "Sequential";
    }

    private static String describeParallelMode(boolean isParallel) {
        return isParallel ? "Parallel (multi-threaded)" : "Sequential (single-threaded)";
    }

    private static String describeYesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String getAutoSelectionReason(ParallelizationMode mode) {
        if (mode.isSupportCalculatorParallel()) {
            return "ParallelRecursive (mode requires parallel support)";
        } else {
            return "Recursive (mode requires sequential support)";
        }
    }

    private static String getActualCalculatorName(TUFCI miner) {
        String calculatorClassName = miner.getSupportCalculatorClassName();

        switch (calculatorClassName) {
            case "DirectConvolutionSupportCalculator":
                return "Direct Convolution (O(n²) DP)";
            case "RecursiveConvolutionSupportCalculator":
                return "Recursive Convolution (O(n² log n) D&C)";
            case "ParallelRecursiveConvolutionSupportCalculator":
                return "Parallel Recursive Convolution (O(n² log n / p) Fork/Join)";
            case "FFTConvolutionSupportCalculator":
                return "FFT Convolution (O(n log² n) Cooley-Tukey)";
            case "ParallelFFTConvolutionSupportCalculator":
                return "Parallel FFT Convolution (O(n log² n / p) Fork/Join)";
            default:
                return calculatorClassName;
        }
    }

    // ==================== Export Utilities ====================

    private static void exportMetricsToCSV(PerformanceMetrics metrics, String csvFilePath) {
        try {
            File csvFile = new File(csvFilePath);
            boolean fileExists = csvFile.exists();

            ensureParentDirectoryExists(csvFile);

            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, true))) {
                if (!fileExists) {
                    writer.println(PerformanceMetrics.getCSVHeader());
                }
                writer.println(metrics.toCSV());
            }

        } catch (IOException e) {
            System.err.println("Error exporting metrics to CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void exportLaTeXTable(CommandLineConfig config, UncertainDatabase database, MiningResult result) throws IOException {
        PhaseTimingObserver observer = new PhaseTimingObserver();

        ResultExporter.exportLaTeXTable(
            config.latexOutput,
            config.databaseFile,
            database.size(),
            database.getVocabulary().size(),
            config.tau,
            config.k,
            config.parallelMode.name(),
            observer,
            result.executionTimeMs,
            result.memoryUsed,
            result.patterns.size()
        );
    }

    // ==================== Helper Methods ====================

    /**
     * Gets the total number of garbage collection events across all GC algorithms.
     * Used to verify that System.gc() actually executed.
     *
     * @return Total GC count across all collectors
     */
    private static long getGCCount() {
        long count = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long gcCount = gc.getCollectionCount();
            if (gcCount > 0) {
                count += gcCount;
            }
        }
        return count;
    }

    /**
     * Resets peak memory usage tracking for all heap memory pools.
     * This establishes a baseline for measuring algorithm memory consumption.
     *
     * IEEE Access Requirement: Peak memory tracking must be reset before measurement
     * to isolate algorithm memory from JVM overhead and pre-existing allocations.
     */
    private static void resetPeakMemoryUsage() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                pool.resetPeakUsage();
            }
        }
    }

    /**
     * Gets PEAK heap memory usage across all heap pools.
     * This tracks the maximum memory used since last reset or JVM start.
     *
     * IEEE Access Compliant: Measures actual peak, including temporary allocations
     * that may have been freed by GC before measurement completes.
     *
     * @return Peak heap memory used in bytes (sum across all heap pools)
     */
    private static long getPeakHeapMemory() {
        long totalPeak = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage peakUsage = pool.getPeakUsage();
                totalPeak += peakUsage.getUsed();
            }
        }
        return totalPeak;
    }

    /**
     * Gets CURRENT heap memory usage across all heap pools.
     * This provides a snapshot of memory at the current moment.
     *
     * @return Current heap memory used in bytes (sum across all heap pools)
     */
    private static long getCurrentHeapMemory() {
        long totalCurrent = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage usage = pool.getUsage();
                totalCurrent += usage.getUsed();
            }
        }
        return totalCurrent;
    }

    private static String extractDatasetName(String filePath) {
        return new File(filePath).getName().replaceAll("\\.txt$", "");
    }

    private static int generateRunId() {
        return (int) (System.currentTimeMillis() % RUN_ID_MODULO);
    }

    private static void ensureParentDirectoryExists(File file) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Immutable configuration object for CLI execution parameters.
     * Includes both mining parameters and output/formatting options.
     *
     * Distinct from application.config.MiningConfiguration which contains only
     * core algorithm parameters.
     */
    private static class CommandLineConfig {
        final String databaseFile;
        final double tau;
        final int k;
        final ParallelizationMode parallelMode;
        final SupportCalculatorType supportType;
        final String csvOutput;
        final String latexOutput;
        final String patternsOutput;
        final boolean quietMode;

        CommandLineConfig(String databaseFile, double tau, int k,
                          ParallelizationMode parallelMode,
                          SupportCalculatorType supportType,
                          String csvOutput, String latexOutput,
                          String patternsOutput, boolean quietMode) {
            this.databaseFile = databaseFile;
            this.tau = tau;
            this.k = k;
            this.parallelMode = parallelMode;
            this.supportType = supportType;
            this.csvOutput = csvOutput;
            this.latexOutput = latexOutput;
            this.patternsOutput = patternsOutput;
            this.quietMode = quietMode;
        }
    }

    /**
     * Container for mining execution results.
     */
    private static class MiningResult {
        final List<FrequentItemset> patterns;
        final PerformanceMetrics metrics;
        final long executionTimeMs;
        final long memoryUsed;

        MiningResult(List<FrequentItemset> patterns, PerformanceMetrics metrics,
                    long executionTimeMs, long memoryUsed) {
            this.patterns = patterns;
            this.metrics = metrics;
            this.executionTimeMs = executionTimeMs;
            this.memoryUsed = memoryUsed;
        }
    }
}
