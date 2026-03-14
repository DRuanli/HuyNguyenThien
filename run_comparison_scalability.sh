#!/bin/bash

# ============================================================================
# TUFCI Scalability Analysis Script (Strong Scaling)
# ============================================================================
# This script performs strong scaling experiments:
# - Fixed dataset size and parameters
# - Varying number of cores: 1, 2, 4, 8, 16 (up to available)
# - Fixed parallelization mode: onlySupport (avoids nested parallelism)
# - Fixed support calculator: parallel (ParallelRecursiveConvolution)
#   Using Parallel Divide & Conquer with O(n² log n / p) complexity
#
# Metrics calculated:
# - Speedup: S(p) = T(1) / T(p)
# - Parallel Efficiency: E(p) = S(p) / p × 100%
# - Scalability curve
# - Amdahl's Law analysis
#
# Results saved to: result_scalability/{Dataset}/k{topk}/result_{timestamp}.txt
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
TOPK_VALUES=(200 400 600)  # Moderate k values for scalability test
TAU=0.7
WARMUP_RUNS=3
ACTUAL_RUNS=30  # Increased for statistical significance
PARALLEL_MODE="onlySupport"  # Parallelize only support calculator (avoids nested parallelism)
SUPPORT_CALCULATOR="parallel"  # ParallelRecursiveConvolution (Parallel Divide & Conquer)

# Dataset configurations: name|file_path
# NOTE: For scalability, typically test on larger datasets
DATASETS=(
    "Mushrooms|processed_data/mushrooms_uncertain.txt"
    "Retail|processed_data/retail_uncertain.txt"
    "Liquor|processed_data/liquor_11frequent_uncertain.txt"
)

# Detect available CPU cores
AVAILABLE_CORES=$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
echo "Detected ${AVAILABLE_CORES} CPU cores"

# Generate core counts to test: 1, 2, 4, 8, 16, ... up to available
CORE_COUNTS=()
for p in 1 2 4 8 16 32; do
    if [ $p -le $AVAILABLE_CORES ]; then
        CORE_COUNTS+=($p)
    fi
done

# If we have odd number of cores (e.g., 6, 12), add them too
# Get last element using bash 3 compatible syntax
LAST_CORE_COUNT=${CORE_COUNTS[${#CORE_COUNTS[@]}-1]}
if [ $AVAILABLE_CORES -gt 1 ] && [ $AVAILABLE_CORES -ne $LAST_CORE_COUNT ]; then
    # Check if AVAILABLE_CORES is not already in the list
    FOUND=0
    for c in "${CORE_COUNTS[@]}"; do
        if [ $c -eq $AVAILABLE_CORES ]; then
            FOUND=1
            break
        fi
    done
    if [ $FOUND -eq 0 ]; then
        CORE_COUNTS+=($AVAILABLE_CORES)
    fi
fi

# Sort core counts (bash 3 compatible)
SORTED_CORES=($(printf '%s\n' "${CORE_COUNTS[@]}" | sort -n))
CORE_COUNTS=("${SORTED_CORES[@]}")

# Base directory for results
RESULT_BASE_DIR="result_scalability"

# Java classpath
CLASSPATH="bin"

# Check if compiled classes exist
if [ ! -d "$CLASSPATH" ]; then
    echo -e "${RED}Error: bin directory not found. Please compile the project first.${NC}"
    echo "Attempting to compile..."

    mkdir -p bin
    find src/main/java -name "*.java" -print | javac -d bin @/dev/stdin

    if [ $? -ne 0 ]; then
        echo -e "${RED}Compilation failed. Please fix compilation errors and try again.${NC}"
        exit 1
    fi

    echo -e "${GREEN}Compilation successful!${NC}"
fi

# Function to run a single iteration with specified core count
run_single_iteration() {
    local dataset_file=$1
    local k=$2
    local cores=$3
    local temp_output=$4

    # Run with JVM property to control Fork/Join parallelism
    java -Djava.util.concurrent.ForkJoinPool.common.parallelism=$cores \
         -cp "$CLASSPATH" presentation.Main "$dataset_file" "$TAU" "$k" \
         --parallel "$PARALLEL_MODE" --support "$SUPPORT_CALCULATOR" > "$temp_output" 2>&1

    local exit_code=$?

    # Extract execution time in milliseconds
    local exec_time_ms=""
    if [ $exit_code -eq 0 ]; then
        exec_time_ms=$(grep -i "Total execution time" "$temp_output" | sed -E 's/.*:[[:space:]]*([0-9]+)[[:space:]]*ms.*/\1/' | head -1)

        if [ -z "$exec_time_ms" ]; then
            exec_time_ms=$(grep -i "execution time" "$temp_output" | sed -E 's/.*:[[:space:]]*([0-9]+).*/\1/' | head -1)
        fi
    fi

    echo "$exit_code|$exec_time_ms"
}

# Function to run experiment for a specific core count
run_core_count_experiment() {
    local dataset_name=$1
    local dataset_file=$2
    local k=$3
    local cores=$4
    local output_file=$5

    echo -e "${CYAN}  Cores: ${YELLOW}${cores}${NC}"

    # Create temp directory
    local temp_dir=$(mktemp -d)
    local temp_output="${temp_dir}/temp_output.txt"

    # Array to store execution times
    local exec_times=()
    local failed=0

    # Warmup runs
    echo -e "${MAGENTA}    Warmup runs (${WARMUP_RUNS})...${NC}"
    for i in $(seq 1 $WARMUP_RUNS); do
        echo -ne "${MAGENTA}      Warmup run ${i}/${WARMUP_RUNS}...${NC}"

        result=$(run_single_iteration "$dataset_file" "$k" "$cores" "$temp_output")
        IFS='|' read -r exit_code exec_time_ms <<< "$result"

        if [ $exit_code -eq 0 ] && [ -n "$exec_time_ms" ]; then
            echo -e " ${GREEN}✓${NC} (${exec_time_ms} ms)"
        else
            echo -e " ${RED}✗ Failed${NC}"
            failed=1
            break
        fi
    done

    if [ $failed -eq 0 ]; then
        # Actual runs
        echo -e "${GREEN}    Actual runs (${ACTUAL_RUNS})...${NC}"
        for i in $(seq 1 $ACTUAL_RUNS); do
            echo -ne "${GREEN}      Run ${i}/${ACTUAL_RUNS}...${NC}"

            result=$(run_single_iteration "$dataset_file" "$k" "$cores" "$temp_output")
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

    # Calculate average
    local avg=0
    local sum=0
    local min=0
    local max=0

    if [ $failed -eq 0 ] && [ ${#exec_times[@]} -gt 0 ]; then
        min=${exec_times[0]}
        max=${exec_times[0]}

        for time in "${exec_times[@]}"; do
            sum=$((sum + time))
            if [ $time -lt $min ]; then
                min=$time
            fi
            if [ $time -gt $max ]; then
                max=$time
            fi
        done

        avg=$((sum / ${#exec_times[@]}))
    fi

    # Write results to output file
    {
        echo "════════════════════════════════════════════════════════════════"
        echo "Cores: ${cores}"
        echo "════════════════════════════════════════════════════════════════"
        echo ""

        if [ $failed -eq 0 ]; then
            echo "Status: SUCCESS"
            echo "Number of runs: ${#exec_times[@]}"
            echo ""
            echo "Execution times (ms):"
            for i in "${!exec_times[@]}"; do
                echo "  Run $((i+1)): ${exec_times[$i]} ms"
            done
            echo ""
            echo "Statistics:"
            echo "  Min: ${min} ms"
            echo "  Max: ${max} ms"
            echo "  Average: ${avg} ms"
            echo "  Total runs: ${#exec_times[@]}"
        else
            echo "Status: FAILED"
            echo "Some runs failed to complete successfully."
        fi

        echo ""
        echo ""
    } >> "$output_file"

    # Cleanup
    rm -rf "$temp_dir"

    # Return average time
    echo "$avg"
}

# Function to run all core counts for a specific dataset and k value
run_dataset_k_experiment() {
    local dataset_name=$1
    local dataset_file=$2
    local k=$3

    # Check if dataset file exists
    if [ ! -f "$dataset_file" ]; then
        echo -e "${RED}Error: Dataset file not found: $dataset_file${NC}"
        return 1
    fi

    # Create output directory
    local output_dir="${RESULT_BASE_DIR}/${dataset_name}/k${k}"
    mkdir -p "$output_dir"

    # Generate timestamp
    local timestamp=$(date +"%Y%m%d_%H%M%S")
    local output_file="${output_dir}/result_${timestamp}.txt"

    # Print experiment info
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  Scalability Analysis (Strong Scaling)                    ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo -e "  Dataset              : ${YELLOW}${dataset_name}${NC}"
    echo -e "  Top-K                : ${YELLOW}${k}${NC}"
    echo -e "  Tau                  : ${YELLOW}${TAU}${NC}"
    echo -e "  Parallel Mode        : ${YELLOW}${PARALLEL_MODE}${NC}"
    echo -e "  Support Calculator   : ${YELLOW}${SUPPORT_CALCULATOR}${NC}"
    echo -e "  Core counts to test  : ${YELLOW}${CORE_COUNTS[@]}${NC}"
    echo -e "  Output File          : ${YELLOW}${output_file}${NC}"
    echo ""

    # Write header
    {
        echo "╔════════════════════════════════════════════════════════════╗"
        echo "║        TUFCI SCALABILITY ANALYSIS RESULTS                  ║"
        echo "║             (Strong Scaling Experiment)                    ║"
        echo "╚════════════════════════════════════════════════════════════╝"
        echo ""
        echo "Dataset: ${dataset_name}"
        echo "Top-K: ${k}"
        echo "Tau threshold: ${TAU}"
        echo "Parallelization mode: ${PARALLEL_MODE}"
        echo "Support calculator: ${SUPPORT_CALCULATOR}"
        echo "Available CPU cores: ${AVAILABLE_CORES}"
        echo "Core counts tested: ${CORE_COUNTS[@]}"
        echo "Warmup runs: ${WARMUP_RUNS}"
        echo "Actual runs: ${ACTUAL_RUNS}"
        echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
        echo ""
        echo ""
    } > "$output_file"

    # Arrays to store results (parallel arrays instead of associative)
    local core_list=()
    local time_list=()
    local failed_count=0
    local baseline_time=0

    # Run experiments for each core count
    for cores in "${CORE_COUNTS[@]}"; do
        avg_time=$(run_core_count_experiment "$dataset_name" "$dataset_file" "$k" "$cores" "$output_file")

        core_list+=($cores)
        time_list+=($avg_time)

        if [ $cores -eq 1 ]; then
            baseline_time=$avg_time
        fi

        if [ "$avg_time" = "0" ] || [ -z "$avg_time" ]; then
            echo -e "${RED}  ✗ Cores ${cores} failed${NC}"
            failed_count=$((failed_count + 1))
        else
            echo -e "${GREEN}  ✓ Cores ${cores} completed: ${avg_time} ms average${NC}"
        fi

        echo ""
    done

    # Calculate and write scalability analysis
    {
        echo "════════════════════════════════════════════════════════════════"
        echo "SCALABILITY ANALYSIS"
        echo "════════════════════════════════════════════════════════════════"
        echo ""
        echo "Strong Scaling Results:"
        echo "----------------------"
        printf "%-8s %-12s %-12s %-12s\n" "Cores" "Time(ms)" "Speedup" "Efficiency(%)"
        echo "----------------------------------------------------------------"

        for i in "${!core_list[@]}"; do
            local cores=${core_list[$i]}
            local time=${time_list[$i]}

            if [ "$time" != "0" ] && [ -n "$time" ] && [ "$baseline_time" != "0" ] && [ -n "$baseline_time" ]; then
                # Calculate speedup: S(p) = T(1) / T(p)
                local speedup=$(awk "BEGIN {printf \"%.2f\", $baseline_time / $time}")

                # Calculate efficiency: E(p) = S(p) / p × 100%
                local efficiency=$(awk "BEGIN {printf \"%.2f\", ($baseline_time / $time) / $cores * 100}")

                printf "%-8s %-12s %-12s %-12s\n" "$cores" "$time" "$speedup" "$efficiency"
            else
                printf "%-8s %-12s %-12s %-12s\n" "$cores" "$time" "N/A" "N/A"
            fi
        done

        echo ""
        echo "Definitions:"
        echo "  - Speedup S(p) = T(1) / T(p)"
        echo "    where T(1) is time with 1 core, T(p) is time with p cores"
        echo "  - Efficiency E(p) = S(p) / p × 100%"
        echo "  - Ideal speedup is linear: S(p) = p (efficiency = 100%)"
        echo "  - Super-linear speedup: S(p) > p (efficiency > 100%) - rare, usually cache effects"
        echo "  - Sub-linear speedup: S(p) < p (efficiency < 100%) - common due to overhead"
        echo ""

        # Amdahl's Law analysis
        if [ "$baseline_time" != "0" ] && [ -n "$baseline_time" ]; then
            echo "Amdahl's Law Analysis:"
            echo "---------------------"

            # Get last core count and time
            local max_cores=${core_list[${#core_list[@]}-1]}
            local max_time=${time_list[${#time_list[@]}-1]}

            if [ "$max_time" != "0" ] && [ -n "$max_time" ]; then
                local actual_speedup=$(awk "BEGIN {printf \"%.2f\", $baseline_time / $max_time}")

                # Estimate parallel fraction: f ≈ (p*S - p) / (S*(p-1))
                local parallel_fraction=$(awk "BEGIN {
                    p = $max_cores
                    s = $actual_speedup
                    f = (p * s - p) / (s * (p - 1))
                    if (f < 0) f = 0
                    if (f > 1) f = 1
                    printf \"%.4f\", f
                }")

                local parallel_pct=$(awk "BEGIN {printf \"%.2f\", $parallel_fraction * 100}")
                local serial_pct=$(awk "BEGIN {printf \"%.2f\", (1 - $parallel_fraction) * 100}")

                echo "  Estimated parallel fraction: ${parallel_pct}%"
                echo "  Estimated serial fraction: ${serial_pct}%"
                echo "  Maximum theoretical speedup (p→∞): $(awk "BEGIN {printf \"%.2f\", 1 / (1 - $parallel_fraction)}")"
            fi
        fi

        echo ""
        echo ""
    } >> "$output_file"

    # Write summary
    {
        echo "════════════════════════════════════════════════════════════════"
        echo "SUMMARY"
        echo "════════════════════════════════════════════════════════════════"
        echo "Total core configurations tested: ${#CORE_COUNTS[@]}"
        echo "Successful: $((${#CORE_COUNTS[@]} - failed_count))"
        echo "Failed: ${failed_count}"
        echo ""
    } >> "$output_file"

    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}Experiment complete! Results saved to: ${output_file}${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    if [ $failed_count -eq 0 ]; then
        return 0
    else
        return 1
    fi
}

# Main execution
main() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║      TUFCI SCALABILITY ANALYSIS BENCHMARK                  ║${NC}"
    echo -e "${BLUE}║           (Strong Scaling Experiment)                      ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "Configuration:"
    echo -e "  Top-K values          : ${YELLOW}${TOPK_VALUES[@]}${NC}"
    echo -e "  Tau threshold         : ${YELLOW}${TAU}${NC}"
    echo -e "  Datasets              : ${YELLOW}${#DATASETS[@]}${NC}"
    echo -e "  Parallel mode         : ${YELLOW}${PARALLEL_MODE}${NC} (fixed)"
    echo -e "  Support calculator    : ${YELLOW}${SUPPORT_CALCULATOR}${NC} (fixed)"
    echo -e "  Core counts           : ${YELLOW}${CORE_COUNTS[@]}${NC}"
    echo -e "  Available CPU cores   : ${YELLOW}${AVAILABLE_CORES}${NC}"
    echo -e "  Warmup runs           : ${YELLOW}${WARMUP_RUNS}${NC}"
    echo -e "  Actual runs           : ${YELLOW}${ACTUAL_RUNS}${NC}"
    echo ""

    local total_experiment_groups=$((${#TOPK_VALUES[@]} * ${#DATASETS[@]}))
    local total_runs=$((total_experiment_groups * ${#CORE_COUNTS[@]} * (WARMUP_RUNS + ACTUAL_RUNS)))

    echo -e "Total experiment groups: ${GREEN}${total_experiment_groups}${NC}"
    echo -e "Total individual runs: ${GREEN}${total_runs}${NC}"
    echo ""

    echo -e "${CYAN}Note: This script controls parallelism using:${NC}"
    echo -e "  ${YELLOW}-Djava.util.concurrent.ForkJoinPool.common.parallelism=N${NC}"
    echo ""

    read -p "Press Enter to start the experiments, or Ctrl+C to cancel..."
    echo ""

    local experiment_count=0
    local successful_experiments=0
    local failed_experiments=0

    local overall_start=$(date +%s)

    # Iterate through all configurations
    for dataset_config in "${DATASETS[@]}"; do
        IFS='|' read -r dataset_name dataset_file <<< "$dataset_config"

        for k in "${TOPK_VALUES[@]}"; do
            experiment_count=$((experiment_count + 1))

            echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
            echo -e "${YELLOW}  Experiment Group ${experiment_count}/${total_experiment_groups}${NC}"
            echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
            echo ""

            if run_dataset_k_experiment "$dataset_name" "$dataset_file" "$k"; then
                successful_experiments=$((successful_experiments + 1))
            else
                failed_experiments=$((failed_experiments + 1))
            fi

            sleep 2
        done
    done

    local overall_end=$(date +%s)
    local overall_duration=$((overall_end - overall_start))

    # Print summary
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║             EXPERIMENT SUMMARY                             ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "Total experiment groups: ${GREEN}${total_experiment_groups}${NC}"
    echo -e "Successful groups: ${GREEN}${successful_experiments}${NC}"
    echo -e "Failed groups: ${RED}${failed_experiments}${NC}"
    echo -e "Total duration: ${YELLOW}${overall_duration} seconds${NC}"
    echo ""
    echo -e "Results are stored in: ${YELLOW}${RESULT_BASE_DIR}${NC}"
    echo -e "  Structure: ${RESULT_BASE_DIR}/{Dataset}/k{topk}/result_{timestamp}.txt"
    echo ""
    echo -e "${CYAN}Metrics calculated:${NC}"
    echo -e "  - ${GREEN}Speedup${NC}: S(p) = T(1) / T(p)"
    echo -e "  - ${GREEN}Efficiency${NC}: E(p) = S(p) / p × 100%"
    echo -e "  - ${GREEN}Amdahl's Law${NC} analysis with parallel fraction estimation"
    echo ""
}

# Run main function
main
