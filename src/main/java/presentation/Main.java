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

            MiningConfiguration config = parseConfiguration(args);
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
    private static void executeMiningPipeline(MiningConfiguration config) throws IOException {
        UncertainDatabase database = loadDatabase(config);
        TUFCI miner = configureMiner(config, database);

        MiningResult result = executeMining(miner, config);

        displayResults(config, database, result);
        exportResults(config, database, result);
    }

    /**
     * Loads and validates the database from file.
     */
    private static UncertainDatabase loadDatabase(MiningConfiguration config) throws IOException {
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
    private static TUFCI configureMiner(MiningConfiguration config, UncertainDatabase database) {
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
    private static MiningResult executeMining(TUFCI miner, MiningConfiguration config) {
        System.out.println("Starting mining process...");
        System.out.println(SEPARATOR);

        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest GC for accurate memory measurement

        long memoryBefore = getUsedMemory(runtime);
        long startTime = System.nanoTime();

        List<FrequentItemset> patterns = miner.mine();
        PerformanceMetrics metrics = miner.getMetrics();

        long endTime = System.nanoTime();
        long executionTimeMs = (endTime - startTime) / 1_000_000;
        long memoryUsed = getUsedMemory(runtime) - memoryBefore;

        System.out.println(SEPARATOR);
        System.out.println("Mining completed!");
        System.out.println();

        return new MiningResult(patterns, metrics, executionTimeMs, memoryUsed);
    }

    /**
     * Displays mining results to console.
     */
    private static void displayResults(MiningConfiguration config, UncertainDatabase database, MiningResult result) {
        if (config.quietMode) {
            printQuietModeOutput(config, database, result);
        } else {
            printVerboseOutput(config, database, result);
        }
    }

    /**
     * Exports results to specified output files.
     */
    private static void exportResults(MiningConfiguration config, UncertainDatabase database, MiningResult result) throws IOException {
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

    private static MiningConfiguration parseConfiguration(String[] args) {
        return new MiningConfiguration(
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

    private static void printConfiguration(MiningConfiguration config) {
        System.out.println("Configuration:");
        System.out.printf("  Database file     : %s%n", config.databaseFile);
        System.out.printf("  Tau (threshold)   : %.2f%n", config.tau);
        System.out.printf("  K (top patterns)  : %d%n", config.k);
        System.out.printf("  Parallelization   : %s%n", config.parallelMode.getDescription());
        System.out.printf("  Support Calculator: %s%n", describeSupportCalculator(config.supportType));
        System.out.println();
    }

    private static void printMinerConfiguration(MiningConfiguration config, String actualCalculator) {
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

    private static void printVerboseOutput(MiningConfiguration config, UncertainDatabase database, MiningResult result) {
        PhaseTimingObserver observer = new PhaseTimingObserver();

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
            result.patterns.size()
        );

        Usage.printResults(result.patterns, config.k);
    }

    private static void printQuietModeOutput(MiningConfiguration config, UncertainDatabase database, MiningResult result) {
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

    private static void exportLaTeXTable(MiningConfiguration config, UncertainDatabase database, MiningResult result) throws IOException {
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

    private static long getUsedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
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
     * Immutable configuration object for mining parameters.
     */
    private static class MiningConfiguration {
        final String databaseFile;
        final double tau;
        final int k;
        final ParallelizationMode parallelMode;
        final SupportCalculatorType supportType;
        final String csvOutput;
        final String latexOutput;
        final String patternsOutput;
        final boolean quietMode;

        MiningConfiguration(String databaseFile, double tau, int k,
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
