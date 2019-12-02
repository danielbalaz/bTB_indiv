package btbcluster;

import broadwick.BroadwickConstants;
import broadwick.graph.writer.EdgeList;
import broadwick.io.FileOutput;
import broadwick.montecarlo.markovchain.observer.MarkovChainObserver;
import broadwick.rng.RNG;
import com.google.common.base.Joiner;
import java.util.Collection;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Observer for the Markov Chain Monte Carlo.
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class MyMarkovChainObserver extends MarkovChainObserver {

    MyMarkovChainObserver(final String transmissionNetworkFileName,
                          final String observedTransmissionNetworkFileName,
                          final String snpDistanceFileName) {
        super();

        this.transmissionNetworkFileName = transmissionNetworkFileName;
        this.observedTransmissionNetworkFileName = observedTransmissionNetworkFileName;
        this.snpDistanceFileName = snpDistanceFileName;
    }

    @Override
    public void started() {
        this.transmissionNetworkFileName = String.format("%s.%d.edgeList", transmissionNetworkFileName, this.monteCarlo.getId());
        this.observedTransmissionNetworkFileName = String.format("%s.%d.edgeList", observedTransmissionNetworkFileName, this.monteCarlo.getId());
        this.snpDistanceFileName = String.format("%s.%d.dat", snpDistanceFileName, this.monteCarlo.getId());

        this.timeSeriesHerdsUnderRestriction = String.format("TimeSeriesHerdsUnderRestriction.%d.csv", this.monteCarlo.getId());
        this.timeSeriesInfectedHerds = String.format("TimeSeriesInfectedHerds.%d.csv", this.monteCarlo.getId());
        this.timeSeriesInfectedCows = String.format("TimeSeriesInfectedCows.%d.csv", this.monteCarlo.getId());
        this.timeSeriesInfectedReservoirs = String.format("TimeSeriesInfectedReservoirs.%d.csv", this.monteCarlo.getId());
        this.timeSeriesInfectedBadgers = String.format("TimeSeriesInfectedBadgers.%d.csv", this.monteCarlo.getId());
        this.likelihoodsFileName = String.format("Likelihoods.%d.csv", this.monteCarlo.getId());
        
        // DB: [testData]
        this.cattleTestFilename= String.format("cattleTestResults.%d.csv", this.monteCarlo.getId(), false, false);
        this.recordBadgersFilename = String.format("recordedBadgers.%d.csv", this.monteCarlo.getId(), false, false);
        this.herdTestFilename = String.format("herdTestResults.%d.csv", this.monteCarlo.getId(), false, false);
        
        // stochastic events
        this.initialSizesFilename = String.format("initialSizes.%d.csv", this.monteCarlo.getId(), false, false);
        this.initialInfStatesFilename = String.format("initialInfectionStates.%d.csv", this.monteCarlo.getId(), false, false);
        this.initialRestrictionsFilename = String.format("initialRestrictions.%d.csv", this.monteCarlo.getId(), false, false);
        this.recordedMovementFilename = String.format("movements.%d.csv", this.monteCarlo.getId(), false, false);
        
        // DB: [OutInf]
        this.allSequencesFileName = String.format("AllSequences.%d.csv", this.monteCarlo.getId(), false, false);
        
        // DB: [NodeSeq]
        // this file gets overwritten by different scenarios in the same MC chain :-(
        nodeSequencesFile = new FileOutput(String.format("NodeSequences.%d.csv", this.monteCarlo.getId()), false, false);
        nodeSequencesFile.write("Scenario_ID,");
        nodeSequencesFile.write(InfectionNode.Header());
        nodeSequencesFile.flush();
    }

    @Override
    public void step() {
        MyMonteCarloScenarioResults results = ((MyMonteCarloScenarioResults) super.monteCarlo.getConsumer());
        log.info("Rejected {}% of scenarios.", results.getPercentageOfRejectedScenarios());
        log.info("Mean likelihood {}", results.getExpectedValue());

        if (this.monteCarlo.isLastStepAccepted()) {

            try (FileOutput fo = new FileOutput(snpDistanceFileName, false, false)) {
                fo.write(results.getPairwiseDistancesDistribution().toString());
            }

            try (FileOutput fo = new FileOutput(transmissionNetworkFileName, false, false)) {
            //fo.write(GraphViz.toString(results.getTransmissionTree()));
                //fo.write(GraphMl.toString(results.getTransmissionTree(), true));
                fo.write(EdgeList.toString(results.getTransmissionTree()));
            }
            try (FileOutput fo = new FileOutput(observedTransmissionNetworkFileName, false, false)) {
            //fo.write(GraphViz.toString(results.getObservedTransmissionTree()));
                //fo.write(GraphMl.toString(results.getObservedTransmissionTree(), true));
                fo.write(EdgeList.toString(results.getObservedTransmissionTree()));
            }

            //
            // save the timeseries plots to file.....
            try (FileOutput fo = new FileOutput(this.timeSeriesHerdsUnderRestriction, false, false)) {
                fo.write(results.getHerdsUnderRestrictionTimeSeries().toString());
            }
            try (FileOutput fo = new FileOutput(this.timeSeriesInfectedHerds, false, false)) {
                fo.write(results.getInfectedHerdsTimeSeries().toString());
            }
            try (FileOutput fo = new FileOutput(this.timeSeriesInfectedCows, false, false)) {
                fo.write(results.getInfectedCowsTimeSeries().toString());
            }
            try (FileOutput fo = new FileOutput(this.timeSeriesInfectedReservoirs, false, false)) {
                fo.write(results.getInfectedReservoirsTimeSeries().toString());
            }
            try (FileOutput fo = new FileOutput(this.timeSeriesInfectedBadgers, false, false)) {
                fo.write(results.getInfectedBadgersTimeSeries().toString());
            }
            //TODO
//        try (FileOutput fo = new FileOutput("TimeSeriesSampledIndividuals.csv", false, false)) {
//            fo.write(results.getSampledIndividualsTimeSeries().toString());
//        }

            try (FileOutput fo = new FileOutput(this.likelihoodsFileName, false, false)) {
                fo.write(Joiner.on("\n").join(results.getSampledLikelihoods()));
            }

            
        }
    }

    private boolean includeNodeInPhylogeneticTree(final RNG generator,
                                                  final java.util.Map<Integer, Double> samplingRate,
                                                  final InfectionNode node) {

        if (node != null) {
            final Integer detectionDate = node.getDetectionDate();
            if (detectionDate != null) {
                int detectionYear = (int) Math.floor((detectionDate) / 365.0) + BroadwickConstants.getZERO_DATE().getYear();
                Double probabilityOfGettingSample = samplingRate.get(detectionYear);
                if (probabilityOfGettingSample == null) {
                    probabilityOfGettingSample = 0.0;
                }
                log.debug("Including node (year {}) in tree with probability {}", detectionYear, probabilityOfGettingSample);
                return generator.getDouble() <= probabilityOfGettingSample;
            }
            log.trace("Node {} not detected - not being included", node.getId());
            return false;
        }
        return false;
    }

    @Override
    public void takeMeasurements() {
    }

    @Override
    public void finished() {
        MyMonteCarloScenarioResults results = ((MyMonteCarloScenarioResults) super.monteCarlo.getConsumer());
        
        try (FileOutput fo = new FileOutput(this.cattleTestFilename, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(CattleTest.Header());
            for (final Map.Entry<Integer, Collection<CattleTest>> entry : results.getAllCattleTests().entrySet()) {
                for (final CattleTest cattleTest : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(cattleTest.toString()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }
        
        try (FileOutput fo = new FileOutput(this.recordBadgersFilename, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(RecordBadger.Header());
            for (final Map.Entry<Integer, Collection<RecordBadger>> entry : results.getAllRecordedBadgers().entrySet()) {
                for (final RecordBadger recordBadger : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(recordBadger.toString()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }
        
        try (FileOutput fo = new FileOutput(this.herdTestFilename, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(HerdTest.Header());
            for (final Map.Entry<Integer, Collection<HerdTest>> entry : results.getAllHerdTests().entrySet()) {
                for (final HerdTest herdTest : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(herdTest.toString()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }
        
        try (FileOutput fo = new FileOutput(this.initialSizesFilename, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(RecordInitialSize.Header());
            for (final Map.Entry<Integer, Collection<RecordInitialSize>> entry : results.getAllInitialSizes().entrySet()) {
                for (final RecordInitialSize item : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(item.toString()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }
        
        try (FileOutput fo = new FileOutput(this.initialInfStatesFilename, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(RecordInitialInfState.Header());
            for (final Map.Entry<Integer, Collection<RecordInitialInfState>> entry : results.getAllInitialInfStates().entrySet()) {
                for (final RecordInitialInfState item : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(item.toString()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }
        
        try (FileOutput fo = new FileOutput(this.initialRestrictionsFilename, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(RecordInitialRestrictions.Header());
            for (final Map.Entry<Integer, Collection<RecordInitialRestrictions>> entry : results.getAllInitialRestrictions().entrySet()) {
                for (final RecordInitialRestrictions item : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(item.toString()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }
        
        try (FileOutput fo = new FileOutput(this.recordedMovementFilename, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(RecordMovement.Header());
            for (final Map.Entry<Integer, Collection<RecordMovement>> entry : results.getAllRecordedMovements().entrySet()) {
                for (final RecordMovement item : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(item.toString()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }
        
        try (FileOutput fo = new FileOutput(this.allSequencesFileName, false, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scenario_ID,").append(InfectedCow.Header());
            for (final Map.Entry<Integer, Collection<InfectedCow>> entry : results.getAllInfectedCows().entrySet()) {
                for (final InfectedCow cow : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(cow.record()).append("\n");
                }
            }
            for (final Map.Entry<Integer, Collection<InfectedBadger>> entry : results.getAllInfectedBadgers().entrySet()) {
                for (final InfectedBadger badger : entry.getValue()) {
                    sb.append(entry.getKey()).append(",");
                    sb.append(badger.record()).append("\n");
                }
            }
            fo.write(sb.toString());
            fo.flush();
            fo.close();
        }

        // DB: [NodeSeq]
        StringBuilder sb = new StringBuilder();
        for (final InfectionNode node : results.getTransmissionTree().getVertices()) {
            sb.append(results.getScenarioId()).append(",");
            sb.append(node.record()).append("\n");
        }
        nodeSequencesFile.write(sb.toString());
        nodeSequencesFile.flush();
        nodeSequencesFile.close();
    }

    private String snpDistanceFileName;
    private String transmissionNetworkFileName;
    private String observedTransmissionNetworkFileName;
    private String timeSeriesHerdsUnderRestriction;
    private String timeSeriesInfectedHerds;
    private String timeSeriesInfectedCows;
    private String timeSeriesInfectedReservoirs;
    private String timeSeriesInfectedBadgers;
    private String likelihoodsFileName;
    
    // DB: [OutInf] File name for output of all infections' data
    private String allSequencesFileName;
    private FileOutput nodeSequencesFile;
    
    // DB: [testData]
    private String cattleTestFilename;
    private String recordBadgersFilename;
    private String herdTestFilename;
    
    // record stochastic events
    private String initialSizesFilename;
    private String initialInfStatesFilename;
    private String initialRestrictionsFilename;
    private String recordedMovementFilename;
}
