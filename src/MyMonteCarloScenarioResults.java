package btbcluster;

import broadwick.graph.Edge;
import broadwick.montecarlo.MonteCarloResults;
import broadwick.statistics.Samples;
import broadwick.statistics.distributions.IntegerDistribution;
import broadwick.statistics.distributions.MultinomialDistribution;
import broadwick.graph.DirectedGraph;
import broadwick.utils.CloneUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Utility class for holding the results of each scenario run.
 */
@Slf4j
public class MyMonteCarloScenarioResults implements MonteCarloResults {

    /**
     * Create an object to store the results from the Monte Carlo run. If the observed SNP distance distribution is
     * given then a likelihood can be calculated for the computed SNP distance distribution.
     * @param observedSnpDiffDistrib the observed SNP distance distribution.
     */
    public MyMonteCarloScenarioResults(final IntegerDistribution observedSnpDiffDistrib) {
        this.reset();

        this.observedPairwiseDistancesDistribution = new IntegerDistribution();
        this.observedPairwiseDistancesDistribution.add(observedSnpDiffDistrib);
        
        final double[] probabilities = new double[observedSnpDiffDistrib.getNumBins()];
        final int[] x = new int[observedSnpDiffDistrib.getNumBins()];
        final int total = observedSnpDiffDistrib.getSumCounts();

        int i = 0;
        for (Integer bin : observedSnpDiffDistrib.getBins()) {
            x[i] = observedSnpDiffDistrib.getFrequency(bin);
            probabilities[i] = (1.0 * x[i]) / total;
            i++;
        }

        dist = new MultinomialDistribution(total, probabilities);
    }

    @Override
    public final double getExpectedValue() {     
        return expectedValue.getSize() == 0 ? MIN_VALUE : expectedValue.getMean();
    }

    @Override
    public final void reset() {
        this.outbreakContainedCount = 0;
        this.expectedValue = new Samples();
        this.numInfectedCowsAtDeath = new Samples();
        this.numInfectedCowsMoved = new Samples();
        this.numInfectedBadgersAtDeath = new Samples();
        this.numInfectedBadgersMoved = new Samples();
        this.outbreakSize = new Samples();
        this.cowBadgerTransmissions = new Samples();
        this.cowCowTransmissions = new Samples();
        this.badgerCowTransmissions = new Samples();
        this.badgerBadgerTransmissions = new Samples();
        this.rejectedScenarioCount = 0;
        this.scenarioCount = 0;
        this.pairwiseDistancesDistribution = new IntegerDistribution();
        this.reactorsAtBreakdownDistribution = new IntegerDistribution();
        this.sampledLikelihoods = new ArrayList();
        this.transmissionTree = new DirectedGraph<>();
        this.observedTransmissionTree = new DirectedGraph<>();

        this.herdsUnderRestrictionTimeSeries = new StringBuilder();
        this.infectedHerdsTimeSeries = new StringBuilder();
        this.infectedCowsTimeSeries = new StringBuilder();
        this.sampledCowsTimeSeries = new StringBuilder();
        
        this.infectedReservoirsTimeSeries = new StringBuilder();
        this.infectedBadgersTimeSeries = new StringBuilder();
        
        // DB: [OutInf]
        this.infectedCows = new HashMap();
        this.infectedBadgers = new HashMap();
        this.allInfectedCows = new HashMap();
        this.allInfectedBadgers = new HashMap();
        
        this.cattleTest = new ArrayList();
        this.allCattleTests = new HashMap();
        
        this.recordedBadgers = new ArrayList();
        this.allRecordedBadgers = new HashMap();
        
        this.herdTests = new ArrayList();
        this.allHerdTests = new HashMap();
        
        this.initialSizes = new ArrayList();
        this.allInitialSizes = new HashMap();
        
        this.initialInfStates = new ArrayList();
        this.allInitialInfStates = new HashMap();
        
        this.initialRestrictions = new ArrayList();
        this.allInitialRestrictions = new HashMap();
        
        this.recordedMovements = new ArrayList();
        this.allRecordedMovements = new HashMap();
    }

    /**
     * Calculate the percentage of scenarios that were run and rejected.
     * @return the percentage.
     */
    public double getPercentageOfRejectedScenarios() {
        return (100.0 * rejectedScenarioCount) / scenarioCount;
    }

    @Override
    public final String toCsv() {
        double likelihood = this.expectedValue.getMean();
        if (this.expectedValue.getSize() == 0) {
            likelihood = MIN_VALUE;
        }
        int numTransmissionEvents = cowCowTransmissions.getSize() + cowBadgerTransmissions.getSize() +
                badgerCowTransmissions.getSize() + badgerBadgerTransmissions.getSize();

        return String.format("%f,%f,%f,%f,%f,%f,%d,%f,%f,%f,%f,%f", likelihood,
                             numInfectedCowsAtDeath.getMean(),
                             numInfectedCowsMoved.getMean(),
                             numInfectedBadgersAtDeath.getMean(),
                             numInfectedBadgersMoved.getMean(),
                             outbreakSize.getMean(),
                             outbreakContainedCount,
                             getPercentageOfRejectedScenarios(),
                             (100.0 * badgerCowTransmissions.getSize()) / numTransmissionEvents,
                             (100.0 * cowCowTransmissions.getSize()) / numTransmissionEvents,
                             (100.0 * cowBadgerTransmissions.getSize()) / numTransmissionEvents,
                             (100.0 * badgerBadgerTransmissions.getSize()) / numTransmissionEvents);
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Scenarios counted = ").append(scenarioCount).append("\n");
        if (scenarioCount > 0) {
            sb.append("Percentage of rejected samples = ").append(getPercentageOfRejectedScenarios()).append("\n");
        }
        //sb.append("LogLikelihood = ").append(getScore(false)).append("\n");
        sb.append("LogLikelihood = ").append(expectedValue.getMean()).append("\n");
        sb.append("Num reactors at breakdown = ").append(reactorsAtBreakdownDistribution.getSumCounts()).append("\n");
        sb.append("Num cows carying infection at time of death = ").append(numInfectedCowsAtDeath.getMean());
        sb.append(" \u00B1 ").append(numInfectedCowsAtDeath.getStdDev()).append("\n");
        sb.append("Num infected cows moved off a farm = ").append(numInfectedCowsMoved.getMean());
        sb.append(" \u00B1 ").append(numInfectedCowsMoved.getStdDev()).append("\n");
        sb.append("Num badgers carying infection at time of death = ").append(numInfectedBadgersAtDeath.getMean());
        sb.append(" \u00B1 ").append(numInfectedBadgersAtDeath.getStdDev()).append("\n");
        sb.append("Num infected badgers moved off a farm = ").append(numInfectedBadgersMoved.getMean());
        sb.append(" \u00B1 ").append(numInfectedBadgersMoved.getStdDev()).append("\n");
        sb.append("Outbreak size = ").append(outbreakSize.getMean());
        sb.append(" \u00B1 ").append(outbreakSize.getStdDev()).append("\n");
        sb.append("Num outbreaks that died out = ").append(this.outbreakContainedCount).append("\n");
        sb.append("Pairwise SNP distance distribution = ").append(pairwiseDistancesDistribution).append("\n");

        int numTransmissionEvents =
                cowCowTransmissions.getSize() + cowBadgerTransmissions.getSize() +
                badgerCowTransmissions.getSize() + badgerBadgerTransmissions.getSize();
        sb.append("% badger -> cow transmissions = ").append((100.0 * badgerCowTransmissions.getSize()) / numTransmissionEvents).append("\n");
        sb.append("% cow -> cow transmissions = ").append((100.0 * cowCowTransmissions.getSize()) / numTransmissionEvents).append("\n");
        sb.append("% cow -> badger transmissions = ").append((100.0 * cowBadgerTransmissions.getSize()) / numTransmissionEvents).append("\n");
        sb.append("% badger -> badger transmissions = ").append((100.0 * badgerBadgerTransmissions.getSize()) / numTransmissionEvents).append("\n");

        return sb.toString();
    }

    @Override
    public Samples getSamples() {
        return expectedValue;
    }

    @Override
    public MonteCarloResults join(MonteCarloResults results) {
        // this is used to join all the 'expectedValues' of each simulation so we add it to the Samples object we've 
        // created
        final MyMonteCarloScenarioResults mcResults = (MyMonteCarloScenarioResults) results;

        double likelihood = mcResults.getScore();
        if (likelihood != MIN_VALUE) {
            this.expectedValue.add(likelihood);
            this.sampledLikelihoods.add(likelihood);
        } else {
            addRejectedScenarioCount(true);
        }

        this.outbreakContainedCount += mcResults.outbreakContainedCount;

        this.numInfectedCowsAtDeath.add(mcResults.getNumInfectedCowsAtDeath());
        this.numInfectedCowsMoved.add(mcResults.getNumInfectedCowsMoved());
        this.numInfectedBadgersAtDeath.add(mcResults.getNumInfectedBadgersAtDeath());
        this.numInfectedBadgersMoved.add(mcResults.getNumInfectedBadgersMoved());
        this.outbreakSize.add(mcResults.getOutbreakSize());

        this.badgerCowTransmissions.add(mcResults.getBadgerCowTransmissions());
        this.cowCowTransmissions.add(mcResults.getCowCowTransmissions());
        this.cowBadgerTransmissions.add(mcResults.getCowBadgerTransmissions());
        this.badgerBadgerTransmissions.add(mcResults.getBadgerBadgerTransmissions());

        this.pairwiseDistancesDistribution.add(mcResults.getPairwiseDistancesDistribution());
        this.reactorsAtBreakdownDistribution.add(mcResults.getReactorsAtBreakdownDistribution());

        // need to join the transmission tree.....
        for (InfectionNode node : mcResults.transmissionTree.getVertices()) {
            if (!this.transmissionTree.getVertices().contains(node)) {
                this.transmissionTree.addVertex(node);
            }
        }
        for (final Edge<InfectionNode> edge : mcResults.transmissionTree.getEdges()) {
            Edge<InfectionNode> e2 = this.transmissionTree.getEdge(edge.getId());
            if (e2 != null) {
                e2.setWeight(e2.getWeight() + 1.0);
            } else {
                InfectionNode source = (InfectionNode) CloneUtils.deepClone(edge.getSource());
                InfectionNode dest = (InfectionNode) CloneUtils.deepClone(edge.getDestination());
                this.transmissionTree.addEdge(new Edge(source, dest, 1.0), source, dest);
            }
        }
        log.debug("mean transmission tree has {} nodes", this.transmissionTree.getVertexCount());

        // DB: [OutInf]
        this.allInfectedCows.put(mcResults.getScenarioId(), mcResults.getInfectedCows().values());
        this.allInfectedBadgers.put(mcResults.getScenarioId(), mcResults.getInfectedBadgers().values());
        
        this.allCattleTests.put(mcResults.getScenarioId(), mcResults.getCattleTest());
        this.allRecordedBadgers.put(mcResults.getScenarioId(), mcResults.getRecordedBadgers());
        
        this.allHerdTests.put(mcResults.getScenarioId(), mcResults.getHerdTests());
        
        this.allInitialSizes.put(mcResults.getScenarioId(), mcResults.getInitialSizes());
        this.allInitialInfStates.put(mcResults.getScenarioId(), mcResults.getInitialInfStates());
        this.allInitialRestrictions.put(mcResults.getScenarioId(), mcResults.getInitialRestrictions());
        
        this.allRecordedMovements.put(mcResults.getScenarioId(), mcResults.getRecordedMovements());
        
        // join the observed transmission tree
        for (InfectionNode node : mcResults.observedTransmissionTree.getVertices()) {
            if (!this.observedTransmissionTree.getVertices().contains(node)) {
                this.observedTransmissionTree.addVertex(node);
            }
        }
        for (final Edge<InfectionNode> edge : mcResults.observedTransmissionTree.getEdges()) {
            Edge<InfectionNode> e2 = this.observedTransmissionTree.getEdge(edge.getId());
            if (e2 != null) {
                e2.setWeight(e2.getWeight() + 1.0);
            } else {
                InfectionNode source = (InfectionNode) CloneUtils.deepClone(edge.getSource());
                InfectionNode dest = (InfectionNode) CloneUtils.deepClone(edge.getDestination());
                this.observedTransmissionTree.addEdge(new Edge(source, dest, 1.0), source, dest);
            }
        }
        log.trace("mean observedTransmissionTree tree has {} nodes", this.observedTransmissionTree.getVertexCount());

        this.herdsUnderRestrictionTimeSeries.append(mcResults.herdsUnderRestrictionTimeSeries).append("\n");
        this.infectedHerdsTimeSeries.append(mcResults.infectedHerdsTimeSeries).append("\n");
        this.infectedCowsTimeSeries.append(mcResults.infectedCowsTimeSeries).append("\n");
        this.sampledCowsTimeSeries.append(mcResults.sampledCowsTimeSeries).append("\n");

        this.infectedReservoirsTimeSeries.append(mcResults.infectedReservoirsTimeSeries).append("\n");
        this.infectedBadgersTimeSeries.append(mcResults.infectedBadgersTimeSeries).append("\n");
        
        //this.infectedCows.append();
        
        this.scenarioCount++;
        return this;
    }

    /**
     * Update the count of rejected scenarios. The counter only gets updated if the argument is true.
     * @param isScenarioRejected if true update the internal count else ignore.
     * @return the updated number of rejected scenarios.
     */
    public final int addRejectedScenarioCount(final boolean isScenarioRejected) {
        if (isScenarioRejected) {
            this.rejectedScenarioCount++;
        }
        return rejectedScenarioCount;
    }

    /**
     * Increment the number of scenarios that have died out. In general, the outbreak died count for each scenario will
     * either be 0,1 and these are summed over all scenarios when joining results.
     */
    void incrementOutbreakContainedCount() {
        outbreakContainedCount++;
    }

    /**
     * Calculate the Monte Carlo score (the log-likelihood) for the simulation.
     * @return the log-likelihood.
     */
    public final double getScore() {
        if (dist != null) {
            final int expectedCount = observedPairwiseDistancesDistribution.getSumCounts();
            final IntegerDistribution generatedPairwiseDistancesDistribution = new IntegerDistribution(0, observedPairwiseDistancesDistribution.getNumBins() - 1);

            for (int bin : observedPairwiseDistancesDistribution.getBins()) {
                Integer data = pairwiseDistancesDistribution.getFrequency(bin);
                if (data == null) {
                    data = 0;
                }
                generatedPairwiseDistancesDistribution.setFrequency(bin, data);
            }
            

            if (generatedPairwiseDistancesDistribution.getSumCounts() == 0) {
                log.warn("Could not calculate likelihood : {}", generatedPairwiseDistancesDistribution.toCsv());
                return MIN_VALUE;
            }

            final int[] bins = generatedPairwiseDistancesDistribution.normaliseBins(expectedCount).toArray();

            // We want the log-likelihood here so we copy most of the code from MultinomialDistribution but  
            // neglecting the final Math.exp() call.
            double sumXFact = 0.0;
            int sumX = 0;
            double sumPX = 0.0;
            final double[] p = dist.getP();

            for (int i = 0; i < p.length; i++) {
                sumX += bins[i];
                sumXFact += broadwick.math.Factorial.lnFactorial(bins[i]);
                if (p[i] > 1E-15) {
                    // just in case probabilities[i] == 0.0
                    sumPX += (bins[i] * Math.log(p[i]));
                }
            }

            if (sumX != dist.getN()) {
                log.error("Cannot calculate the Monte Carlo score for snp distance distribution {}; compared to {}",
                          generatedPairwiseDistancesDistribution.toCsv(), observedPairwiseDistancesDistribution.toCsv());
                throw new IllegalArgumentException(String.format("Multinomial distribution error: Sum_x [%d] != number of samples [%d].", sumX, dist.getN()));
            } else {
                final double logLikelihood = broadwick.math.Factorial.lnFactorial(dist.getN()) - sumXFact + sumPX;
                log.debug("logLikelihood : {}", logLikelihood);
                return logLikelihood;
            }
        }
        return MIN_VALUE;
    }

    @Override
    protected void finalize() throws Throwable {
        sampledLikelihoods.clear();
        numInfectedCowsAtDeath.clear();
        numInfectedCowsMoved.clear();
        numInfectedBadgersAtDeath.clear();
        numInfectedBadgersMoved.clear();
        outbreakSize.clear();
        cowCowTransmissions.clear();
        cowBadgerTransmissions.clear();
        badgerCowTransmissions.clear();
        badgerBadgerTransmissions.clear();
        
        transmissionTree.getVertices().clear();
        transmissionTree.getEdges().clear();
        observedTransmissionTree.getVertices().clear();
        observedTransmissionTree.getEdges().clear();

        sampledLikelihoods = null;
        numInfectedCowsAtDeath = null;
        numInfectedCowsMoved = null;
        numInfectedBadgersAtDeath = null;
        numInfectedBadgersMoved = null;
        outbreakSize = null;
        cowCowTransmissions = null;
        cowBadgerTransmissions = null;
        badgerCowTransmissions = null;
        badgerBadgerTransmissions = null;
        
        pairwiseDistancesDistribution = null;
        reactorsAtBreakdownDistribution = null;

        transmissionTree = null;
        observedTransmissionTree = null;
        
        // DB [OutInf]
        infectedCows = null;
        infectedBadgers = null;
        allInfectedCows = null;
        allInfectedBadgers = null;
        
        cattleTest = null;
        allCattleTests = null;
        
        recordedBadgers = null;
        allRecordedBadgers = null;
        
        herdTests = null;
        allHerdTests = null;
        
        initialSizes = null;
        allInitialSizes = null;
        
        initialInfStates = null;
        allInitialInfStates = null;
        
        initialRestrictions = null;
        allInitialRestrictions = null;
        
        recordedMovements = null;
        allRecordedMovements = null;
        
        super.finalize();
    }

    public void setObservedTransmissionTree(final DirectedGraph<InfectionNode, Edge<InfectionNode>> tree) {
        this.observedTransmissionTree = (DirectedGraph<InfectionNode, Edge<InfectionNode>>) CloneUtils.deepClone(tree);
    }

    private int outbreakContainedCount;
    private final MultinomialDistribution dist;
    private Samples expectedValue;
    @Getter
    private Samples numInfectedCowsMoved;
    @Getter
    private Samples numInfectedCowsAtDeath;
    @Getter
    private Samples numInfectedBadgersMoved;
    @Getter
    private Samples numInfectedBadgersAtDeath;
    @Getter
    private Samples outbreakSize;
    @Getter
    private Samples cowCowTransmissions;
    @Getter
    private Samples cowBadgerTransmissions;
    @Getter
    private Samples badgerCowTransmissions;
    @Getter
    private Samples badgerBadgerTransmissions;
    @Getter
    private IntegerDistribution pairwiseDistancesDistribution;
    @Getter
    private IntegerDistribution reactorsAtBreakdownDistribution;
    @Getter
    private final IntegerDistribution observedPairwiseDistancesDistribution;
    @Getter
    private DirectedGraph<InfectionNode, Edge<InfectionNode>> transmissionTree;
    @Getter
    private DirectedGraph<InfectionNode, Edge<InfectionNode>> observedTransmissionTree;
    
    // record test data
    @Getter
    private Collection<CattleTest> cattleTest;
    @Getter
    private Map<Integer, Collection<CattleTest>> allCattleTests;
    
    @Getter
    private Collection<RecordBadger> recordedBadgers;
    @Getter
    private Map<Integer, Collection<RecordBadger>> allRecordedBadgers;
    
    @Getter
    private Collection<HerdTest> herdTests;
    @Getter
    private Map<Integer, Collection<HerdTest>> allHerdTests;
    
    @Getter
    private Collection<RecordInitialSize> initialSizes;
    @Getter
    private Map<Integer, Collection<RecordInitialSize>> allInitialSizes;
    
    @Getter
    private Collection<RecordInitialInfState> initialInfStates;
    @Getter
    private Map<Integer, Collection<RecordInitialInfState>> allInitialInfStates;
    
    @Getter
    private Collection<RecordInitialRestrictions> initialRestrictions;
    @Getter
    private Map<Integer, Collection<RecordInitialRestrictions>> allInitialRestrictions;
    
    @Getter
    private Collection<RecordMovement> recordedMovements;
    @Getter
    private Map<Integer, Collection<RecordMovement>> allRecordedMovements;
    
    // DB: [OutInf]
    @Getter
    private Map<String, InfectedCow> infectedCows; // all infected and culled cows in that scenario are in this list
    @Getter
    private Map<String, InfectedBadger> infectedBadgers;
    @Getter
    private Map<Integer, Collection<InfectedCow>> allInfectedCows;
    @Getter
    private Map<Integer, Collection<InfectedBadger>> allInfectedBadgers;

    // Some time series plots of various measureables that are updated in the observer.step() method.
    @Getter
    private StringBuilder herdsUnderRestrictionTimeSeries;
    @Getter
    private StringBuilder infectedHerdsTimeSeries;
    @Getter
    private StringBuilder infectedCowsTimeSeries;
    @Getter
    private StringBuilder infectedReservoirsTimeSeries;
    @Getter
    private StringBuilder infectedBadgersTimeSeries;
    @Getter
    private StringBuilder sampledCowsTimeSeries;

    // these variables are only used in this class, the join method keeps these statistics.
    @Getter
    private int rejectedScenarioCount;
    @Getter
    private int scenarioCount;
    
    // give each scenario a unique ID
    // not implemented yet?
    @Getter
    @Setter
    private int scenarioId;
    
    @Getter
    private Collection<Double> sampledLikelihoods;
    
    private final static Double MIN_VALUE = Double.NEGATIVE_INFINITY; //-Double.MAX_VALUE;
}
