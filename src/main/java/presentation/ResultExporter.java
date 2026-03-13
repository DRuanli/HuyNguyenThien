package presentation;

import domain.model.FrequentItemset;
import domain.observer.PhaseTimingObserver;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Exports mining results in CSV format for academic publication and analysis.
 * Generates structured output suitable for tables and figures in research papers.
 *
 * Output Formats:
 * 1. Performance Summary CSV: Execution time breakdown, memory, throughput
 * 2. Pattern Results CSV: Top-K patterns with support and probability
 * 3. IEEE Access Table Format: Ready for paper inclusion
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public class ResultExporter {

    /**
     * Export performance metrics to CSV file for analysis and publication.
     *
     * CSV Format:
     * timestamp,dataset,transactions,vocabulary,tau,k,parallel_mode,support_calculator,
     * phase1_ms,phase2_ms,phase3_ms,total_ms,memory_mb,patterns_found,
     * throughput_trans_sec,throughput_patterns_sec
     *
     * @param outputFile Path to output CSV file
     * @param dbFile Database filename
     * @param transactionCount Number of transactions
     * @param vocabularySize Vocabulary size
     * @param tau Probability threshold
     * @param k Top-K parameter
     * @param parallelMode Parallelization mode
     * @param supportCalc Support calculator type
     * @param observer Phase timing observer
     * @param totalTime Total execution time in milliseconds
     * @param memoryUsed Memory consumed in bytes
     * @param patternCount Number of patterns found
     */
    public static void exportPerformanceCSV(String outputFile,
                                           String dbFile,
                                           int transactionCount,
                                           int vocabularySize,
                                           double tau,
                                           int k,
                                           String parallelMode,
                                           String supportCalc,
                                           PhaseTimingObserver observer,
                                           long totalTime,
                                           long memoryUsed,
                                           int patternCount) throws IOException {

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true))) {
            // Extract dataset name from file path
            String datasetName = extractDatasetName(dbFile);

            // Current timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            // Phase timings
            long phase1Time = observer.getPhase1Time();
            long phase2Time = observer.getPhase2Time();
            long phase3Time = observer.getPhase3Time();

            // Memory in MB
            double memoryMB = memoryUsed / (1024.0 * 1024.0);

            // Throughput metrics
            double throughputTransSec = totalTime > 0 ? (transactionCount * 1000.0) / totalTime : 0;
            double throughputPatternsSec = totalTime > 0 ? (patternCount * 1000.0) / totalTime : 0;

            // Write CSV row
            writer.printf("%s,%s,%d,%d,%.2f,%d,%s,%s,%d,%d,%d,%d,%.2f,%d,%.2f,%.2f%n",
                timestamp,
                datasetName,
                transactionCount,
                vocabularySize,
                tau,
                k,
                parallelMode,
                supportCalc,
                phase1Time,
                phase2Time,
                phase3Time,
                totalTime,
                memoryMB,
                patternCount,
                throughputTransSec,
                throughputPatternsSec
            );
        }
    }

    /**
     * Export top-K patterns to CSV file for analysis.
     *
     * CSV Format:
     * rank,itemset_size,itemset,support,probability
     *
     * @param outputFile Path to output CSV file
     * @param results List of frequent itemsets
     * @param k Top-K parameter
     */
    public static void exportPatternsCSV(String outputFile,
                                        List<FrequentItemset> results,
                                        int k) throws IOException {

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write CSV header
            writer.println("rank,itemset_size,itemset,support,probability");

            // Write top-K patterns
            int count = Math.min(results.size(), k);
            for (int i = 0; i < count; i++) {
                FrequentItemset fi = results.get(i);

                writer.printf("%d,%d,\"%s\",%d,%.4f%n",
                    i + 1,                          // Rank (1-based)
                    fi.size(),                      // Itemset size
                    fi.toStringWithCodec(),         // Itemset as string
                    fi.getSupport(),                // Support
                    fi.getProbability()             // Probability
                );
            }
        }
    }

    /**
     * Export results in LaTeX table format ready for IEEE Access paper.
     * Generates a properly formatted LaTeX table with all key metrics.
     *
     * @param outputFile Path to output .tex file
     * @param dbFile Database filename
     * @param transactionCount Number of transactions
     * @param vocabularySize Vocabulary size
     * @param tau Probability threshold
     * @param k Top-K parameter
     * @param parallelMode Parallelization mode
     * @param observer Phase timing observer
     * @param totalTime Total execution time in milliseconds
     * @param memoryUsed Memory consumed in bytes
     * @param patternCount Number of patterns found
     */
    public static void exportLaTeXTable(String outputFile,
                                       String dbFile,
                                       int transactionCount,
                                       int vocabularySize,
                                       double tau,
                                       int k,
                                       String parallelMode,
                                       PhaseTimingObserver observer,
                                       long totalTime,
                                       long memoryUsed,
                                       int patternCount) throws IOException {

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            String datasetName = extractDatasetName(dbFile);

            long phase1Time = observer.getPhase1Time();
            long phase2Time = observer.getPhase2Time();
            long phase3Time = observer.getPhase3Time();
            double memoryMB = memoryUsed / (1024.0 * 1024.0);

            // Write LaTeX table
            writer.println("\\begin{table}[htbp]");
            writer.println("\\centering");
            writer.println("\\caption{Experimental Results for " + datasetName + " Dataset}");
            writer.println("\\label{tab:results_" + datasetName.toLowerCase() + "}");
            writer.println("\\begin{tabular}{ll}");
            writer.println("\\hline");
            writer.println("\\textbf{Metric} & \\textbf{Value} \\\\");
            writer.println("\\hline");
            writer.println("Dataset & " + datasetName + " \\\\");
            writer.println("Transactions & " + String.format("%,d", transactionCount) + " \\\\");
            writer.println("Vocabulary Size & " + String.format("%,d", vocabularySize) + " \\\\");
            writer.println("Tau (\\(\\tau\\)) & " + tau + " \\\\");
            writer.println("K & " + k + " \\\\");
            writer.println("Parallelization Mode & " + parallelMode + " \\\\");
            writer.println("\\hline");
            writer.println("Phase 1 Time (ms) & " + String.format("%,d", phase1Time) + " \\\\");
            writer.println("Phase 2 Time (ms) & " + String.format("%,d", phase2Time) + " \\\\");
            writer.println("Phase 3 Time (ms) & " + String.format("%,d", phase3Time) + " \\\\");
            writer.println("Total Time (ms) & " + String.format("%,d", totalTime) + " \\\\");
            writer.println("\\hline");
            writer.println("Memory Usage (MB) & " + String.format("%.2f", memoryMB) + " \\\\");
            writer.println("Patterns Found & " + String.format("%,d", patternCount) + " \\\\");
            writer.println("\\hline");
            writer.println("\\end{tabular}");
            writer.println("\\end{table}");
        }
    }

    /**
     * Export aggregate statistics from multiple runs for publication.
     * Computes mean, standard deviation, min, max for statistical reporting.
     *
     * This is useful when running the same configuration multiple times
     * to report statistically significant results with confidence intervals.
     *
     * CSV Format:
     * dataset,k,parallel_mode,runs,mean_ms,stddev_ms,min_ms,max_ms,cv_percent
     *
     * @param outputFile Path to output CSV file
     * @param datasetName Dataset name
     * @param k Top-K parameter
     * @param parallelMode Parallelization mode
     * @param executionTimes Array of execution times from multiple runs
     */
    public static void exportAggregateStatistics(String outputFile,
                                                String datasetName,
                                                int k,
                                                String parallelMode,
                                                long[] executionTimes) throws IOException {

        if (executionTimes.length == 0) return;

        // Calculate statistics
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (long time : executionTimes) {
            sum += time;
            if (time < min) min = time;
            if (time > max) max = time;
        }

        double mean = sum / (double) executionTimes.length;

        // Calculate standard deviation
        double variance = 0;
        for (long time : executionTimes) {
            double diff = time - mean;
            variance += diff * diff;
        }
        variance /= executionTimes.length;
        double stddev = Math.sqrt(variance);

        // Coefficient of variation (percentage)
        double cv = mean > 0 ? (stddev / mean) * 100 : 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true))) {
            writer.printf("%s,%d,%s,%d,%.2f,%.2f,%d,%d,%.2f%n",
                datasetName,
                k,
                parallelMode,
                executionTimes.length,
                mean,
                stddev,
                min,
                max,
                cv
            );
        }
    }

    /**
     * Print summary suitable for console and log files.
     * This is a condensed version focusing on key metrics for quick review.
     */
    public static void printPublicationSummary(String dbFile,
                                              int transactionCount,
                                              int vocabularySize,
                                              double tau,
                                              int k,
                                              String parallelMode,
                                              String supportCalc,
                                              PhaseTimingObserver observer,
                                              long totalTime,
                                              long memoryUsed,
                                              int patternCount) {

        String datasetName = extractDatasetName(dbFile);

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                    EXPERIMENTAL RESULTS                    ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();

        System.out.println("DATASET CHARACTERISTICS:");
        System.out.println("  Dataset          : " + datasetName);
        System.out.println("  Transactions     : " + String.format(Locale.US, "%,d", transactionCount));
        System.out.println("  Vocabulary       : " + String.format(Locale.US, "%,d", vocabularySize));
        System.out.println();

        System.out.println("ALGORITHM PARAMETERS:");
        System.out.println("  Tau (τ)          : " + tau);
        System.out.println("  K (top patterns) : " + k);
        System.out.println("  Parallelization  : " + parallelMode);
        System.out.println("  Support Calc     : " + supportCalc);
        System.out.println();

        System.out.println("EXECUTION TIME:");
        System.out.printf(Locale.US, "  Total Time       : %,10d ms%n", totalTime);
        System.out.println();

        System.out.println("RESOURCE CONSUMPTION:");
        System.out.printf(Locale.US, "  Memory Usage     : %,.2f MB%n", memoryUsed / (1024.0 * 1024.0));
        System.out.printf(Locale.US, "  Patterns Found   : %,d%n", patternCount);
        System.out.println();

        if (totalTime > 0) {
            System.out.println("THROUGHPUT METRICS:");
            System.out.printf(Locale.US, "  Transaction Rate : %,.2f trans/sec%n",
                (transactionCount * 1000.0) / totalTime);
            System.out.printf(Locale.US, "  Pattern Rate     : %,.2f patterns/sec%n",
                (patternCount * 1000.0) / totalTime);
            System.out.println();
        }

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
    }

    /**
     * Helper method to extract dataset name from file path.
     * Example: "data/retail_uncertain.txt" → "Retail"
     */
    private static String extractDatasetName(String filePath) {
        String filename = filePath;

        // Extract filename from path
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            filename = filePath.substring(lastSlash + 1);
        }

        // Remove _uncertain.txt suffix
        filename = filename.replace("_uncertain.txt", "");
        filename = filename.replace(".txt", "");

        // Capitalize first letter
        if (filename.length() > 0) {
            filename = filename.substring(0, 1).toUpperCase() + filename.substring(1);
        }

        return filename;
    }

    /**
     * Initialize CSV file with header if it doesn't exist.
     * Call this before first exportPerformanceCSV to ensure header is present.
     */
    public static void initializePerformanceCSV(String outputFile) throws IOException {
        java.io.File file = new java.io.File(outputFile);
        if (!file.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.println("timestamp,dataset,transactions,vocabulary,tau,k,parallel_mode," +
                             "support_calculator,phase1_ms,phase2_ms,phase3_ms,total_ms," +
                             "memory_mb,patterns_found,throughput_trans_sec,throughput_patterns_sec");
            }
        }
    }

    /**
     * Initialize aggregate statistics CSV file with header.
     */
    public static void initializeAggregateCSV(String outputFile) throws IOException {
        java.io.File file = new java.io.File(outputFile);
        if (!file.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.println("dataset,k,parallel_mode,runs,mean_ms,stddev_ms,min_ms,max_ms,cv_percent");
            }
        }
    }
}
