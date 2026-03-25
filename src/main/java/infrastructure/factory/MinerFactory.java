package infrastructure.factory;

import application.config.MiningConfiguration;
import application.config.ParallelizationMode;
import application.config.SupportCalculatorType;
import domain.mining.AbstractMiner;
import domain.mining.TUFCI;
import domain.support.SupportCalculator;
import infrastructure.persistence.UncertainDatabase;

/**
 * Factory for creating TUFCI Miner instances.
 *
 * Currently implements Best-First Search strategy, which is optimal for Top-K mining.
 * Future work may include DFS/BFS variants for comparative analysis.
 *
 * @author Dang Nguyen Le, Gia Huy Vo
 */
public class MinerFactory {

    /**
     * Create TUFCI Miner with Best-First Search (default, fully sequential).
     *
     * @param database uncertain database to mine
     * @param tau probability threshold (0 < tau <= 1)
     * @param k number of top itemsets to return
     * @return configured TUFCI instance
     */
    public static TUFCI createMiner(
        UncertainDatabase database,
        double tau,
        int k
    ) {
        return new TUFCI(database, tau, k, ParallelizationMode.DEFAULT);
    }

    /**
     * Create TUFCI Miner with specified parallelization mode.
     *
     * @param database uncertain database to mine
     * @param tau probability threshold (0 < tau <= 1)
     * @param k number of top itemsets to return
     * @param mode parallelization mode
     * @return configured TUFCI instance
     */
    public static TUFCI createMiner(
        UncertainDatabase database,
        double tau,
        int k,
        ParallelizationMode mode
    ) {
        return new TUFCI(database, tau, k, mode);
    }

    /**
     * Create TUFCI Miner with specified parallelization mode and support calculator type.
     *
     * @param database uncertain database to mine
     * @param tau probability threshold (0 < tau <= 1)
     * @param k number of top itemsets to return
     * @param mode parallelization mode
     * @param supportType support calculator type
     * @return configured TUFCI instance
     */
    public static TUFCI createMiner(
        UncertainDatabase database,
        double tau,
        int k,
        ParallelizationMode mode,
        SupportCalculatorType supportType
    ) {
        return new TUFCI(database, tau, k, mode, supportType);
    }

    /**
     * Create miner with custom support calculator.
     *
     * @param database uncertain database to mine
     * @param tau probability threshold
     * @param k number of top itemsets to return
     * @param calculator custom support calculation strategy
     * @return configured TUFCI instance
     */
    public static TUFCI createMiner(
        UncertainDatabase database,
        double tau,
        int k,
        SupportCalculator calculator
    ) {
        return new TUFCI(database, tau, k, calculator);
    }

    /**
     * Create miner from full MiningConfiguration.
     *
     * @param database uncertain database to mine
     * @param config complete mining configuration
     * @return configured TUFCI instance
     */
    public static TUFCI createMiner(
        UncertainDatabase database,
        MiningConfiguration config
    ) {
        return new TUFCI(database, config.getTau(), config.getK(),
                        config.getParallelizationMode());
    }
}