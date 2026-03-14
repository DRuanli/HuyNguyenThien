#!/bin/bash

# ============================================================================
# TUFCI Support Calculator Comparison Script
# ============================================================================
# This script tests different support calculator variants:
# - All runs use --parallel default (sequential baseline for fair comparison)
# - Only the support calculator type varies:
#   * direct (DirectConvolutionSupport) - O(n²) DP
#   * recursive (RecursiveConvolutionSupport) - O(n² log n) sequential
#   * parallel (ParallelRecursiveConvolution) - O(n² log n / p)
#   * fft (FFTConvolutionSupport) - O(n log² n) sequential
#   * parallelfft (ParallelFFTConvolution) - O(n log² n / p)
#
# Results for all support variants are stored in a single file per dataset/k combination:
# result_support/{Dataset}/k{topk}/result_{timestamp}.txt
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
TOPK_VALUES=(200 400 600 800 1000)
TAU=0.7  # Default tau value, can be adjusted
WARMUP_RUNS=3  # Number of warmup runs (discarded)
ACTUAL_RUNS=30  # Number of actual runs to record (increased for statistical significance)
PARALLEL_MODE="default"  # Fixed parallelization mode (sequential baseline for fair support calculator comparison)

# Dataset configurations: name|file_path
DATASETS=(
    "Chess|processed_data/chess_uncertain.txt"
    "Mushrooms|processed_data/mushrooms_uncertain.txt"
    "Retail|processed_data/retail_uncertain.txt"
    "Liquor|processed_data/liquor_11frequent_uncertain.txt"
)

# Support calculator variants to test
SUPPORT_VARIANTS=(
    "direct"
    "recursive"
    "parallel"
    "fft"
    "parallelfft"
)

# Base directory for results
RESULT_BASE_DIR="result_support"

# Java classpath (compiled classes)
CLASSPATH="bin"

# Check if compiled classes exist
if [ ! -d "$CLASSPATH" ]; then
    echo -e "${RED}Error: bin directory not found. Please compile the project first.${NC}"
    echo "Attempting to compile..."

    # Create bin directory if it doesn't exist
    mkdir -p bin

    # Compile all Java files
    find src/main/java -name "*.java" -print | javac -d bin @/dev/stdin

    if [ $? -ne 0 ]; then
        echo -e "${RED}Compilation failed. Please fix compilation errors and try again.${NC}"
        exit 1
    fi

    echo -e "${GREEN}Compilation successful!${NC}"
fi

# Function to run a single iteration and extract execution time
run_single_iteration() {
    local dataset_file=$1
    local k=$2
    local support_variant=$3
    local temp_output=$4

    # Run the algorithm with specified parallelization mode and support variant
    java -cp "$CLASSPATH" presentation.Main "$dataset_file" "$TAU" "$k" \
        --parallel "$PARALLEL_MODE" --support "$support_variant" > "$temp_output" 2>&1

    local exit_code=$?

    # Extract execution time in milliseconds
    local exec_time_ms=""
    if [ $exit_code -eq 0 ]; then
        # Extract execution time using sed (compatible with macOS/BSD)
        # Format: "Total execution time           :      405 ms"
        exec_time_ms=$(grep -i "Total execution time" "$temp_output" | sed -E 's/.*:[[:space:]]*([0-9]+)[[:space:]]*ms.*/\1/' | head -1)

        # If not found, try alternative pattern
        if [ -z "$exec_time_ms" ]; then
            exec_time_ms=$(grep -i "execution time" "$temp_output" | sed -E 's/.*:[[:space:]]*([0-9]+).*/\1/' | head -1)
        fi
    fi

    echo "$exit_code|$exec_time_ms"
}

# Function to run experiment for a single support variant (with warmup and multiple runs)
run_support_experiment() {
    local dataset_name=$1
    local dataset_file=$2
    local k=$3
    local support_variant=$4
    local output_file=$5

    echo -e "${CYAN}  Support Variant: ${YELLOW}${support_variant}${NC}"

    # Create temp directory for intermediate outputs
    local temp_dir=$(mktemp -d)
    local temp_output="${temp_dir}/temp_output.txt"

    # Array to store execution times
    local exec_times=()
    local failed=0

    # Warmup runs
    echo -e "${MAGENTA}    Warmup runs (${WARMUP_RUNS})...${NC}"
    for i in $(seq 1 $WARMUP_RUNS); do
        echo -ne "${MAGENTA}      Warmup run ${i}/${WARMUP_RUNS}...${NC}"

        result=$(run_single_iteration "$dataset_file" "$k" "$support_variant" "$temp_output")
        IFS='|' read -r exit_code exec_time_ms <<< "$result"

        if [ $exit_code -eq 0 ]; then
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

            result=$(run_single_iteration "$dataset_file" "$k" "$support_variant" "$temp_output")
            IFS='|' read -r exit_code exec_time_ms <<< "$result"

            if [ $exit_code -eq 0 ]; then
                echo -e " ${GREEN}✓${NC} (${exec_time_ms} ms)"
                exec_times+=("$exec_time_ms")
            else
                echo -e " ${RED}✗ Failed${NC}"
                failed=1
                break
            fi
        done
    fi

    # Write results to output file
    {
        echo "════════════════════════════════════════════════════════════════"
        echo "Support Variant: ${support_variant}"
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

            # Calculate statistics
            if [ ${#exec_times[@]} -gt 0 ]; then
                # Calculate min, max, average
                local min=${exec_times[0]}
                local max=${exec_times[0]}
                local sum=0

                for time in "${exec_times[@]}"; do
                    sum=$((sum + time))
                    if [ $time -lt $min ]; then
                        min=$time
                    fi
                    if [ $time -gt $max ]; then
                        max=$time
                    fi
                done

                local avg=$((sum / ${#exec_times[@]}))

                echo "Statistics:"
                echo "  Min: ${min} ms"
                echo "  Max: ${max} ms"
                echo "  Average: ${avg} ms"
                echo "  Total runs: ${#exec_times[@]}"
            fi
        else
            echo "Status: FAILED"
            echo "Some runs failed to complete successfully."
        fi

        echo ""
        echo ""
    } >> "$output_file"

    # Cleanup
    rm -rf "$temp_dir"

    return $failed
}

# Function to run all support variants for a specific dataset and k value
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

    # Generate timestamp for unique filename
    local timestamp=$(date +"%Y%m%d_%H%M%S")
    local output_file="${output_dir}/result_${timestamp}.txt"

    # Print experiment info
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  Running Support Variant Comparison                       ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo -e "  Dataset       : ${YELLOW}${dataset_name}${NC}"
    echo -e "  Top-K         : ${YELLOW}${k}${NC}"
    echo -e "  Tau           : ${YELLOW}${TAU}${NC}"
    echo -e "  Parallel Mode : ${YELLOW}${PARALLEL_MODE}${NC}"
    echo -e "  Output File   : ${YELLOW}${output_file}${NC}"
    echo -e "  Warmup runs   : ${YELLOW}${WARMUP_RUNS}${NC}"
    echo -e "  Actual runs   : ${YELLOW}${ACTUAL_RUNS}${NC}"
    echo ""

    # Write header to output file
    {
        echo "╔════════════════════════════════════════════════════════════╗"
        echo "║   TUFCI SUPPORT CALCULATOR COMPARISON RESULTS              ║"
        echo "╚════════════════════════════════════════════════════════════╝"
        echo ""
        echo "Dataset: ${dataset_name}"
        echo "Top-K: ${k}"
        echo "Tau threshold: ${TAU}"
        echo "Parallelization mode: ${PARALLEL_MODE} (fixed sequential baseline for fair support calculator comparison)"
        echo "Warmup runs: ${WARMUP_RUNS}"
        echo "Actual runs: ${ACTUAL_RUNS}"
        echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
        echo ""
        echo "Support variants tested:"
        echo "  - direct      : DirectConvolutionSupport (O(n²) DP)"
        echo "  - recursive   : RecursiveConvolutionSupport (O(n² log n) sequential)"
        echo "  - parallel    : ParallelRecursiveConvolution (O(n² log n / p))"
        echo "  - fft         : FFTConvolutionSupport (O(n log² n) sequential)"
        echo "  - parallelfft : ParallelFFTConvolution (O(n log² n / p))"
        echo ""
        echo ""
    } > "$output_file"

    local all_success=0
    local variant_count=0
    local failed_count=0

    # Run each support variant
    for support_variant in "${SUPPORT_VARIANTS[@]}"; do
        variant_count=$((variant_count + 1))

        if run_support_experiment "$dataset_name" "$dataset_file" "$k" "$support_variant" "$output_file"; then
            echo -e "${GREEN}  ✓ Variant ${support_variant} completed successfully${NC}"
        else
            echo -e "${RED}  ✗ Variant ${support_variant} failed${NC}"
            failed_count=$((failed_count + 1))
        fi

        echo ""
    done

    # Write summary to output file
    {
        echo "════════════════════════════════════════════════════════════════"
        echo "SUMMARY"
        echo "════════════════════════════════════════════════════════════════"
        echo "Total variants tested: ${variant_count}"
        echo "Successful: $((variant_count - failed_count))"
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
    echo -e "${BLUE}║   TUFCI SUPPORT CALCULATOR COMPARISON BENCHMARK            ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "Configuration:"
    echo -e "  Top-K values       : ${YELLOW}${TOPK_VALUES[@]}${NC}"
    echo -e "  Tau threshold      : ${YELLOW}${TAU}${NC}"
    echo -e "  Datasets           : ${YELLOW}${#DATASETS[@]}${NC}"
    echo -e "  Parallel mode      : ${YELLOW}${PARALLEL_MODE}${NC} (fixed)"
    echo -e "  Support variants   : ${YELLOW}${SUPPORT_VARIANTS[@]}${NC}"
    echo -e "  Warmup runs        : ${YELLOW}${WARMUP_RUNS}${NC}"
    echo -e "  Actual runs        : ${YELLOW}${ACTUAL_RUNS}${NC}"
    echo ""

    # Calculate total number of experiment groups (dataset + k combinations)
    local total_experiment_groups=$((${#TOPK_VALUES[@]} * ${#DATASETS[@]}))
    local total_runs=$((total_experiment_groups * ${#SUPPORT_VARIANTS[@]} * (WARMUP_RUNS + ACTUAL_RUNS)))

    echo -e "Total experiment groups: ${GREEN}${total_experiment_groups}${NC}"
    echo -e "Total individual runs: ${GREEN}${total_runs}${NC}"
    echo -e "  (Each group tests ${#SUPPORT_VARIANTS[@]} support variants with ${WARMUP_RUNS} warmup + ${ACTUAL_RUNS} actual runs)"
    echo ""
    read -p "Press Enter to start the experiments, or Ctrl+C to cancel..."
    echo ""

    local experiment_count=0
    local successful_experiments=0
    local failed_experiments=0

    # Record overall start time
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

            # Small delay between experiment groups
            sleep 2
        done
    done

    # Record overall end time
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
    echo -e "${CYAN}Support variants tested:${NC}"
    echo -e "  ${GREEN}direct${NC}      - DirectConvolutionSupport (O(n²) DP)"
    echo -e "  ${GREEN}recursive${NC}   - RecursiveConvolutionSupport (O(n² log n) sequential)"
    echo -e "  ${GREEN}parallel${NC}    - ParallelRecursiveConvolution (O(n² log n / p))"
    echo -e "  ${GREEN}fft${NC}         - FFTConvolutionSupport (O(n log² n) sequential)"
    echo -e "  ${GREEN}parallelfft${NC} - ParallelFFTConvolution (O(n log² n / p))"
    echo ""
}

# Run main function
main
