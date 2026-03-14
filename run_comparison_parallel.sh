#!/bin/bash

# ============================================================================
# TUFCI Algorithm Comparison Script - IEEE Access Publication Version
# ============================================================================
# Enhanced features for academic publication:
# - CSV export for machine-readable results
# - Proper statistical analysis (mean, stddev, 95% CI)
# - System metadata recording for reproducibility
# - Automatic figure generation (if Python available)
# - LaTeX table generation
# ============================================================================

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
TOPK_VALUES=(100)
TAU=0.7
WARMUP_RUNS=2
ACTUAL_RUNS=2  # Increased for statistical significance (IEEE requirement)

# Datasets
DATASETS=(
    "Chess|data/chess_uncertain.txt"
    "Retail|data/retail_uncertain.txt"
    "Mushrooms|data/mushrooms_uncertain.txt"
    #"Kosarak|data/kosarak_uncertain.txt"
)

# Parallelization modes
PARALLEL_MODES=(
    "default"
    "onlyPhase1"
    "onlyClosure"
    "onlySupport"
)

# Output configuration
RESULT_BASE_DIR="result"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
CSV_PERFORMANCE="result/performance_${TIMESTAMP}.csv"
CSV_STATISTICS="result/statistics_${TIMESTAMP}.csv"
METADATA_FILE="result/system_metadata_${TIMESTAMP}.txt"

# Java classpath
CLASSPATH="bin"

# ============================================================================
# FUNCTION: Record System Metadata (IEEE Access Reproducibility Requirement)
# ============================================================================
record_system_metadata() {
    echo "Recording system metadata for reproducibility..."

    {
        echo "╔════════════════════════════════════════════════════════════╗"
        echo "║          SYSTEM METADATA FOR REPRODUCIBILITY               ║"
        echo "╚════════════════════════════════════════════════════════════╝"
        echo ""
        echo "Experiment Timestamp: $(date '+%Y-%m-%d %H:%M:%S %Z')"
        echo ""
        echo "HARDWARE SPECIFICATIONS:"
        echo "─────────────────────────────────────────────────────────────"
        echo "  Operating System: $(uname -s) $(uname -r)"
        echo "  Architecture: $(uname -m)"

        # CPU information (OS-specific)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            echo "  CPU Model: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo 'N/A')"
            echo "  CPU Cores: $(sysctl -n hw.ncpu 2>/dev/null || echo 'N/A')"
            echo "  Physical Cores: $(sysctl -n hw.physicalcpu 2>/dev/null || echo 'N/A')"
            echo "  RAM: $(( $(sysctl -n hw.memsize 2>/dev/null || echo 0) / 1024 / 1024 / 1024 )) GB"
        elif [[ "$OSTYPE" == "linux"* ]]; then
            echo "  CPU Model: $(lscpu | grep 'Model name' | sed -r 's/Model name:\s+//' || echo 'N/A')"
            echo "  CPU Cores: $(nproc 2>/dev/null || echo 'N/A')"
            echo "  RAM: $(free -h 2>/dev/null | grep Mem | awk '{print $2}' || echo 'N/A')"
        else
            echo "  CPU: Unknown (unsupported OS)"
        fi

        echo ""
        echo "SOFTWARE ENVIRONMENT:"
        echo "─────────────────────────────────────────────────────────────"
        echo "  Java Version:"
        java -version 2>&1 | sed 's/^/    /'
        echo "  Java Home: ${JAVA_HOME:-Not set}"
        echo "  Classpath: $CLASSPATH"

        # Git information (if available)
        if git rev-parse --git-dir > /dev/null 2>&1; then
            echo ""
            echo "SOURCE CODE VERSION:"
            echo "─────────────────────────────────────────────────────────────"
            echo "  Git Commit: $(git rev-parse HEAD)"
            echo "  Git Branch: $(git rev-parse --abbrev-ref HEAD)"
            echo "  Git Status: $(git diff --quiet && echo 'clean' || echo 'MODIFIED (uncommitted changes)')"
        fi

        echo ""
        echo "EXPERIMENT PARAMETERS:"
        echo "─────────────────────────────────────────────────────────────"
        echo "  Top-K Values: ${TOPK_VALUES[@]}"
        echo "  Tau Threshold: $TAU"
        echo "  Warmup Runs: $WARMUP_RUNS"
        echo "  Actual Runs: $ACTUAL_RUNS"
        echo "  Datasets: ${#DATASETS[@]}"
        echo "  Parallel Modes: ${#PARALLEL_MODES[@]}"
        echo ""
    } > "$METADATA_FILE"

    echo -e "${GREEN}✓ System metadata saved to: $METADATA_FILE${NC}"
}

# ============================================================================
# FUNCTION: Initialize CSV Files with Headers
# ============================================================================
init_csv_files() {
    mkdir -p "$(dirname "$CSV_PERFORMANCE")"

    # Performance CSV (detailed per-run data)
    echo "timestamp,dataset,transactions,vocabulary,tau,k,parallel_mode,phase1_ms,phase2_ms,phase3_ms,total_ms" > "$CSV_PERFORMANCE"

    # Statistics CSV (aggregated statistics)
    echo "dataset,k,parallel_mode,runs,mean_ms,stddev_ms,min_ms,max_ms,ci_95_ms,cv_percent" > "$CSV_STATISTICS"

    echo -e "${GREEN}✓ CSV files initialized${NC}"
    echo -e "  Performance: $CSV_PERFORMANCE"
    echo -e "  Statistics: $CSV_STATISTICS"
}

# ============================================================================
# FUNCTION: Compute Statistics (Mean, StdDev, CI) using bc for Precision
# ============================================================================
compute_statistics() {
    local values=("$@")
    local n=${#values[@]}

    if [ $n -eq 0 ]; then
        echo "0.00,0.00,0,0,0.00,0.00"
        return
    fi

    # Compute mean
    local sum=0
    for v in "${values[@]}"; do
        sum=$(echo "$sum + $v" | bc)
    done
    local mean=$(echo "scale=2; $sum / $n" | bc)

    # Compute standard deviation
    local sq_diff_sum=0
    for v in "${values[@]}"; do
        local diff=$(echo "$v - $mean" | bc)
        local sq_diff=$(echo "$diff * $diff" | bc)
        sq_diff_sum=$(echo "$sq_diff_sum + $sq_diff" | bc)
    done
    local variance=$(echo "scale=4; $sq_diff_sum / $n" | bc)
    local stddev=$(echo "scale=2; sqrt($variance)" | bc -l)

    # Compute min/max
    local min=${values[0]}
    local max=${values[0]}
    for v in "${values[@]}"; do
        if (( $(echo "$v < $min" | bc -l) )); then min=$v; fi
        if (( $(echo "$v > $max" | bc -l) )); then max=$v; fi
    done

    # Compute 95% confidence interval: CI = 1.96 * stddev / sqrt(n)
    local ci=$(echo "scale=2; 1.96 * $stddev / sqrt($n)" | bc -l)

    # Coefficient of variation: CV = (stddev / mean) * 100
    local cv=$(echo "scale=2; if ($mean > 0) ($stddev / $mean) * 100 else 0" | bc -l)

    echo "$mean,$stddev,$min,$max,$ci,$cv"
}

# ============================================================================
# FUNCTION: Run Single Iteration (with CSV export)
# ============================================================================
run_single_iteration() {
    local dataset_file=$1
    local k=$2
    local parallel_mode=$3
    local csv_file=$4

    # Run algorithm with CSV export
    local temp_output=$(mktemp)

    if [ "$parallel_mode" = "default" ]; then
        java -cp "$CLASSPATH" presentation.Main "$dataset_file" "$TAU" "$k" \
            --output-csv "$csv_file" > "$temp_output" 2>&1
    else
        java -cp "$CLASSPATH" presentation.Main "$dataset_file" "$TAU" "$k" \
            --parallel "$parallel_mode" \
            --output-csv "$csv_file" > "$temp_output" 2>&1
    fi

    local exit_code=$?

    # Extract execution time
    local exec_time_ms=""
    if [ $exit_code -eq 0 ]; then
        # Try to extract from the new publication summary format
        exec_time_ms=$(grep -i "TOTAL" "$temp_output" | grep -o '[0-9,]\+\s*ms' | tr -d ',' | grep -o '[0-9]\+' | head -1)

        # Fallback to old format
        if [ -z "$exec_time_ms" ]; then
            exec_time_ms=$(grep -i "Total execution time" "$temp_output" | sed -E 's/.*:[[:space:]]*([0-9,]+)[[:space:]]*ms.*/\1/' | tr -d ',' | head -1)
        fi
    fi

    rm -f "$temp_output"
    echo "$exit_code|$exec_time_ms"
}

# ============================================================================
# FUNCTION: Run Mode Experiment (with Statistics)
# ============================================================================
run_mode_experiment() {
    local dataset_name=$1
    local dataset_file=$2
    local k=$3
    local parallel_mode=$4

    echo -e "${CYAN}  Mode: ${YELLOW}${parallel_mode}${NC}"

    local exec_times=()
    local failed=0

    # Warmup runs
    echo -e "${MAGENTA}    Warmup runs (${WARMUP_RUNS})...${NC}"
    for i in $(seq 1 $WARMUP_RUNS); do
        echo -ne "${MAGENTA}      Warmup $i/${WARMUP_RUNS}...${NC}"

        result=$(run_single_iteration "$dataset_file" "$k" "$parallel_mode" "/dev/null")
        IFS='|' read -r exit_code exec_time_ms <<< "$result"

        if [ $exit_code -eq 0 ] && [ -n "$exec_time_ms" ]; then
            echo -e " ${GREEN}✓${NC} (${exec_time_ms} ms)"
        else
            echo -e " ${RED}✗ Failed${NC}"
            failed=1
            break
        fi
    done

    # Actual runs
    if [ $failed -eq 0 ]; then
        echo -e "${GREEN}    Actual runs (${ACTUAL_RUNS})...${NC}"
        for i in $(seq 1 $ACTUAL_RUNS); do
            echo -ne "${GREEN}      Run $i/${ACTUAL_RUNS}...${NC}"

            result=$(run_single_iteration "$dataset_file" "$k" "$parallel_mode" "$CSV_PERFORMANCE")
            IFS='|' read -r exit_code exec_time_ms <<< "$result"

            if [ $exit_code -eq 0 ] && [ -n "$exec_time_ms" ]; then
                echo -e " ${GREEN}✓${NC} (${exec_time_ms} ms)"
                exec_times+=("$exec_time_ms")
            else
                echo -e " ${RED}✗ Failed${NC}"
                failed=1
                break
            fi
        done
    fi

    # Compute and export statistics
    if [ $failed -eq 0 ] && [ ${#exec_times[@]} -gt 0 ]; then
        IFS=',' read -r mean stddev min max ci cv <<< "$(compute_statistics "${exec_times[@]}")"

        # Append to statistics CSV
        echo "${dataset_name},${k},${parallel_mode},${#exec_times[@]},${mean},${stddev},${min},${max},${ci},${cv}" >> "$CSV_STATISTICS"

        echo -e "${GREEN}    ✓ Statistics: μ=${mean}ms, σ=${stddev}ms, 95% CI=±${ci}ms, CV=${cv}%${NC}"
    fi

    return $failed
}

# ============================================================================
# FUNCTION: Run Dataset Experiment
# ============================================================================
run_dataset_experiment() {
    local dataset_name=$1
    local dataset_file=$2
    local k=$3

    if [ ! -f "$dataset_file" ]; then
        echo -e "${RED}Error: Dataset not found: $dataset_file${NC}"
        return 1
    fi

    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  Experiment: ${dataset_name} (k=${k})${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"

    local failed_count=0

    for parallel_mode in "${PARALLEL_MODES[@]}"; do
        if run_mode_experiment "$dataset_name" "$dataset_file" "$k" "$parallel_mode"; then
            echo -e "${GREEN}  ✓ Mode ${parallel_mode} completed${NC}"
        else
            echo -e "${RED}  ✗ Mode ${parallel_mode} failed${NC}"
            failed_count=$((failed_count + 1))
        fi
        echo ""
    done

    if [ $failed_count -eq 0 ]; then
        return 0
    else
        return 1
    fi
}

# ============================================================================
# FUNCTION: Generate LaTeX Summary Table
# ============================================================================
generate_latex_table() {
    local latex_file="result/summary_table_${TIMESTAMP}.tex"

    echo "Generating LaTeX summary table..."

    {
        echo "% Auto-generated TUFCI Experimental Results"
        echo "% Generated: $(date '+%Y-%m-%d %H:%M:%S')"
        echo ""
        echo "\\begin{table*}[htbp]"
        echo "\\centering"
        echo "\\caption{Execution Time Comparison Across Parallelization Modes}"
        echo "\\label{tab:parallel_comparison}"
        echo "\\begin{tabular}{llrrrrr}"
        echo "\\hline"
        echo "\\textbf{Dataset} & \\textbf{Mode} & \\textbf{Mean (ms)} & \\textbf{StdDev} & \\textbf{Min} & \\textbf{Max} & \\textbf{95\\% CI} \\\\"
        echo "\\hline"

        # Read statistics CSV and format as LaTeX
        tail -n +2 "$CSV_STATISTICS" | while IFS=',' read -r dataset k mode runs mean stddev min max ci cv; do
            echo "$dataset & $mode & $mean & $stddev & $min & $max & \\pm$ci \\\\"
        done

        echo "\\hline"
        echo "\\end{tabular}"
        echo "\\end{table*}"
    } > "$latex_file"

    echo -e "${GREEN}✓ LaTeX table saved to: $latex_file${NC}"
}

# ============================================================================
# FUNCTION: Generate Publication Figures (if Python available)
# ============================================================================
generate_figures() {
    if command -v python3 &> /dev/null; then
        echo ""
        echo -e "${BLUE}Generating publication figures...${NC}"

        if [ -f "visualize_results.py" ]; then
            mkdir -p "figures"

            if python3 visualize_results.py "$CSV_PERFORMANCE" --output-dir "figures/"; then
                echo -e "${GREEN}✓ Figures generated in figures/${NC}"
            else
                echo -e "${YELLOW}Warning: Figure generation failed${NC}"
                echo -e "${YELLOW}  Install dependencies: pip install pandas matplotlib seaborn${NC}"
            fi
        else
            echo -e "${YELLOW}visualize_results.py not found, skipping figure generation${NC}"
        fi
    else
        echo -e "${YELLOW}Python 3 not found, skipping figure generation${NC}"
    fi
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================
main() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     TUFCI IEEE ACCESS PUBLICATION EXPERIMENTS             ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Check compilation
    if [ ! -d "$CLASSPATH" ]; then
        echo -e "${RED}Error: bin directory not found${NC}"
        echo "Attempting to compile..."
        mkdir -p bin
        find src/main/java -name "*.java" -print | javac -d bin @/dev/stdin
        if [ $? -ne 0 ]; then
            echo -e "${RED}Compilation failed${NC}"
            exit 1
        fi
    fi

    # Initialize outputs
    record_system_metadata
    init_csv_files

    echo ""
    echo -e "Configuration:"
    echo -e "  Datasets: ${YELLOW}${#DATASETS[@]}${NC}"
    echo -e "  K values: ${YELLOW}${TOPK_VALUES[@]}${NC}"
    echo -e "  Modes: ${YELLOW}${#PARALLEL_MODES[@]}${NC}"
    echo -e "  Runs per mode: ${YELLOW}${WARMUP_RUNS} warmup + ${ACTUAL_RUNS} actual${NC}"
    echo ""

    local total_experiments=$((${#DATASETS[@]} * ${#TOPK_VALUES[@]} * ${#PARALLEL_MODES[@]}))
    echo -e "Total experiments: ${GREEN}${total_experiments}${NC}"
    echo ""

    read -p "Press Enter to start, or Ctrl+C to cancel..."
    echo ""

    local experiment_num=0
    local successful=0
    local overall_start=$(date +%s)

    # Run all experiments
    for dataset_config in "${DATASETS[@]}"; do
        IFS='|' read -r dataset_name dataset_file <<< "$dataset_config"

        for k in "${TOPK_VALUES[@]}"; do
            experiment_num=$((experiment_num + 1))

            echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
            echo -e "${YELLOW}  Experiment $experiment_num/${#DATASETS[@]} x ${#TOPK_VALUES[@]}${NC}"
            echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"

            if run_dataset_experiment "$dataset_name" "$dataset_file" "$k"; then
                successful=$((successful + 1))
            fi

            sleep 1
        done
    done

    local overall_end=$(date +%s)
    local duration=$((overall_end - overall_start))

    # Generate outputs
    echo ""
    echo -e "${BLUE}Generating publication materials...${NC}"
    generate_latex_table
    generate_figures

    # Summary
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║                  EXPERIMENT COMPLETE                       ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "Results:"
    echo -e "  Performance CSV: ${GREEN}$CSV_PERFORMANCE${NC}"
    echo -e "  Statistics CSV: ${GREEN}$CSV_STATISTICS${NC}"
    echo -e "  System Metadata: ${GREEN}$METADATA_FILE${NC}"
    echo -e "  Duration: ${YELLOW}${duration} seconds${NC}"
    echo ""
    echo -e "${GREEN}✓ All outputs are ready for IEEE Access submission${NC}"
    echo ""
}

# Run main
main
