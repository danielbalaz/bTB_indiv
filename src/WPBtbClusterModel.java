/*
 * Copyright 2013 University of Glasgow.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package btbcluster;

import broadwick.BroadwickConstants;
import broadwick.BroadwickException;
import broadwick.config.generated.Prior;
import broadwick.io.FileInput;
import broadwick.io.FileInputIterator;
import broadwick.model.Model;
import broadwick.montecarlo.MonteCarloResults;
import broadwick.montecarlo.MonteCarloScenario;
import broadwick.montecarlo.acceptor.MonteCarloAcceptor;
import broadwick.montecarlo.markovchain.MarkovStepGenerator;
import broadwick.montecarlo.markovchain.SequentialMonteCarlo;
import broadwick.montecarlo.markovchain.controller.MarkovChainMaxNumStepController;
import broadwick.rng.RNG;
import broadwick.statistics.distributions.IntegerDistribution;
import broadwick.utils.Pair;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

/**
 *
 */
@Slf4j
public class WPBtbClusterModel extends Model {

    @Override
    public void init() {

        try {
            final String dateFormat = this.getParameterValue("dateFormat");
            settings = new ProjectSettings();
            settings.setStartDate(BroadwickConstants.getDate(this.getParameterValue("startDate"), dateFormat))
                    .setEndDate(BroadwickConstants.getDate(this.getParameterValue("endDate"), dateFormat))
                    .setStartDateMovements(BroadwickConstants.getDate(this.getParameterValue("startDateMovements"), dateFormat))
                    .setEndDateMovements(BroadwickConstants.getDate(this.getParameterValue("endDateMovements"), dateFormat))
                    .setStepSize(this.getParameterValueAsInteger("stepSize"))
                    .setIncludeReservoir(this.getParameterValueAsBoolean("includeReservoir"))
                    .setActiveReservoir(this.getParameterValueAsBoolean("activeReservoir"))
                    .setStopWithBreakdownDetected(this.getParameterValueAsBoolean("stopWithBreakdownDetected"))
                    .setMaxInfectedCows(this.getParameterValueAsInteger("maxInfectedCows"))
                    .setMaxInfectedBadgers(this.getParameterValueAsInteger("maxInfectedBadgers"))
                    .setHerdSizeFlex(this.getParameterValueAsBoolean("herdSizeFlexible"))
                    .setReservoirSizeFlex(this.getParameterValueAsBoolean("reservoirSizeFlexible"))
                    .setInitialInfectionStates(this.getParameterValue("initialInfectionStates"))
                    .setInitMutationsPerClade(this.getParameterValueAsInteger("initMutClade"))
                    .setInitBadgersFromCows(this.getParameterValueAsBoolean("initBadgersFromCows"))
                    .setTestIntervalInYears(this.getParameterValueAsDouble("testIntervalInYears"))
                    .setNumInitialRestrictedHerds(this.getParameterValueAsInteger("numHerdsUnderRestrictionInitially"))
                    .setFilterShortMovements(this.getParameterValueAsBoolean("filterShortEpidemics"))
                    .setHerdSizeDistribution(readDistribution(this.getParameterValue("initialHerdSizes")))
                    .setReservoirSizeDistribution(readDistribution(this.getParameterValue("initialReservoirSizes")))
                    .setCattleDeathDistribution(readBirthDeathDistributions(this.getParameterValue("CattleDeathDistribution")))
                    .setBadgerDeathRate(this.getParameterValueAsDouble("badgerDeathRate"))
                    .setSigma(this.getParameterValueAsDouble("sigma"))
                    .setCattleSamplingRate(readSamplingRatesPerYear(this.getParameterValue("CattleSamplingRatesPerYear")))
                    .setBadgerSamplingRate(readSamplingRatesPerYear(this.getParameterValue("BadgerSamplingRatesPerYear")))
                    .setObservedPairwiseDistanceDistribution(readDistribution(this.getParameterValue("observedPairwiseDistanceFile")))
                    .setSeed(this.getParameterValueAsInteger("seed"));

            if (settings.getStartDate() > settings.getEndDate()) {
                throw new IllegalArgumentException(String.format("Simulation start date (%s) after end date (%s)",
                                                                 this.getParameterValue("startDate"), this.getParameterValue("endDate")));
            }

            if (settings.getStartDateMovements()> settings.getEndDateMovements()) {
                throw new IllegalArgumentException(String.format("Movements: start date (%s) after end date (%s)",
                                                                 this.getParameterValue("startDateMovements"), this.getParameterValue("endDateMovements")));
            }

            // We create lookups of farm-infected cattle, reservoir-infected badgers which will be cloned and given to 
            // each scenario.
            farmInfections = new HashMap<>();
            reservoirInfections = new HashMap<>();
            infectedCows = new HashMap<>();
            infectedBadgers = new HashMap<>();
            connectedReservoirs = new HashMap<>();

            readFarmData(); // assign to farms
            log.trace("Farms {}", farms.toString());

            // Read the list of movements and update the cattle movement distribution for each farm.
            // DB: [numM] record the return value, pass to scenario
            numCattleMovements = readMovementFrequencies("Cattle");
            log.trace("Cattle Movements {}", settings.getCattleMovementFrequencies().toString());

            
            readReservoirData(); // assign to reservoirs
            log.trace("Reservoirs {}", reservoirs.toString());
            
            // Read the list of movements and update the badger movement distribution for each reservoir.
            // DB: [numM] record the return value, pass to scenario
            numBadgerMovements = readMovementFrequencies("Badger");
            log.trace("Badger Movements {}", settings.getBadgerMovementFrequencies().toString());

            // Read the reservoir to farm network
            readReservoirDefinitions(this.getParameterValue("reservoirLocations"));
//            settings.setReservoirData(reservoirs);
            settings.setFarmReservoirs(connectedReservoirs);
            
        } catch (IllegalArgumentException | BroadwickException ex) {
            log.error("Error initialising BtbIbmClusterDynamics. {}", ex.getLocalizedMessage());
            throw (new BroadwickException(ex));
        }
    }

    @Override
    public void run() {
        log.info("Running for date range {} - {} ", settings.getStartDate(), settings.getEndDate());

        try {
            final double smoothingRatio = this.getParameterValueAsDouble("smoothingRatio");

            // The model runs a Markov Chain Monte Carlo simulation where each step consists of several simulations of 
            // a Btb epidemic on a network of farms in NI. The movements between farms are drawn from movement 
            // distributions obtained by analysing the actual movements and similarly for the whole herd tests.
            final RNG generator = new RNG(RNG.Generator.Well19937c);

//            initialiseOutputFile();
            // Generate the initial step of the Markov chain, if no pathGenerationAlgorithm is specified or if it is
            // AM - Adaptive Metropolis, then we use the Adaptive metropolis algorithm.
            MarkovStepGenerator pathGenerator;
            if (!this.hasParameter("pathGenerationAlgorithm") || "AM".equals(this.getParameterValue("pathGenerationAlgorithm"))) {
                pathGenerator = new MonteCarloAMPathGenerator(this.getPriors(),
                                                              this.getParameterValueAsDouble("percentageDeviation"),
                                                              generator);
//                this.getPriors().get(0);
//                List<Prior> scaledPriors = new ArrayList();
//                for (Prior p: this.getPriors()) {
//                }
                
            } else {
                pathGenerator = new MonteCarloSrwmPathGenerator(this.getPriors(),
                                                                this.getParameterValueAsDouble("percentageDeviation"),
                                                                generator);
            }

            // DB: if hasParameter() gives trouble, simply use AM algorithm no matter what
//            pathGenerator = new MonteCarloAMPathGenerator(this.getPriors(),
//                                                              this.getParameterValueAsDouble("percentageDeviation"),
//                                                              generator);
            // DB: [numM] pass numCattleMovements
            MonteCarloScenario scenario = new MyMonteCarloScenario(null, settings, farms, reservoirs,
                                                                   farmInfections, reservoirInfections,
                                                                   infectedCows, infectedBadgers,
                                                                   numCattleMovements, numBadgerMovements);
            SequentialMonteCarlo smc = new SequentialMonteCarlo(
                    this.getPriors(),
                    this.getParameterValueAsInteger("numParticles"),
                    scenario,
                    this.getParameterValueAsInteger("numScenarios"),
                    new MyMonteCarloScenarioResults(settings.getObservedPairwiseDistanceDistribution()),
                    new MarkovChainMaxNumStepController(this.getParameterValueAsInteger("numMcSteps")),
                    pathGenerator,
                    new MonteCarloAcceptor() {
                        @Override
                        public boolean accept(final MonteCarloResults oldResult, final MonteCarloResults newResult) {
                            
                            // In some situations, the likelihood for the first step may be negative infinity, in this case 
                            // accept the first finite value.
                            if (Double.isInfinite(oldResult.getExpectedValue()) && Double.isFinite(newResult.getExpectedValue())) {
                                return true;
                            }
                            
                            // TODO - oldResults should be the last accepted step - check this is the case and if so then 
                            // I can set MIN_VALUE in the results object to Double.NEGATIVE_INFINITY and
                            // check that both oldResult.getExpectedValue() and newResult.getExpectedValue()
                            // are not infinity.
                            if (Double.isFinite(newResult.getExpectedValue())) {
                                // we are using a log likelihood, so...                    
                                final double ratio = newResult.getExpectedValue() - oldResult.getExpectedValue();
                                log.trace("{}", String.format("Math.log(r) < (%g-%g) [=%g:%g]?",
                                                              newResult.getExpectedValue(), oldResult.getExpectedValue(),
                                                              ratio, ratio / smoothingRatio));

                                return Math.log(generator.getDouble()) < ratio / smoothingRatio;
                            }
                            return false;
                        }
                    });

            MyMarkovChainObserver myMcObserver = new MyMarkovChainObserver(this.getParameterValue("transmissionNetworkFile"),
                                                                           this.getParameterValue("observedTransmissionNetworkFile"),
                                                                           this.getParameterValue("snpDistanceDistributionFile"));
            smc.addParticleObserver(myMcObserver);
            smc.run();

        } catch (NumberFormatException e) {
            log.error("Found error running scenarios. {}", Throwables.getStackTraceAsString(e));
        }
    }

    @Override
    public void finalise() {
    }

    /**
     * Read the farm data. Right now it is just a list of the ids of the farms in the simulation.
     * @return a lookup table of farm id to the farm object.
     */
    private void readFarmData() {
        farms = new HashMap<>();
        try {
            log.info("Reading Farm data from {}", this.getParameterValue("FarmData"));
            final FileInputIterator fle = new FileInput(this.getParameterValue("FarmData")).iterator();
            while (fle.hasNext()) {
                final String[] split = fle.next().split(",");
                final String id = split[0].trim();
                farms.put(id, new Farm(id));
                farmInfections.put(id, new ArrayList<>());
                connectedReservoirs.put(id, new ArrayList<>());
            }
        } catch (IOException e) {
            log.error("Could not create farm lookup table {}", Throwables.getStackTraceAsString(e));
        }
    }

    /**
     * Read the reservoir data. Right now it is just a list of the ids of the reservoirs in the simulation.
     * @return a lookup table of farm id to the farm object.
     */
    private void readReservoirData() {
        reservoirs = new HashMap<>();
        try {
            log.info("Reading Reservoir data from {}", this.getParameterValue("ReservoirData"));
            final FileInputIterator fle = new FileInput(this.getParameterValue("ReservoirData")).iterator();
            while (fle.hasNext()) {
                final String[] split = fle.next().split(",");
                final String id = split[0].trim();
//                final String id = String.format("RESERVOIR_%03d", Integer.parseInt(split[0].trim()));
                reservoirs.put(id, new Reservoir(id));
                reservoirInfections.put(id, new ArrayList<>());
            }
        } catch (IOException e) {
            log.error("Could not create farm lookup table {}", Throwables.getStackTraceAsString(e));
        }
    }

    /**
     * Read the movement frequencies. The movement frequency data contains the ids of the farms and the number of
     * animals moved in each movement, the data is in the format departure-destination n1,n2,n3,n4,n5 etc
     */
    private int readMovementFrequencies(final String species) {
        int numMoves = 0; // the total number of animals moved
        try {
            log.info("Reading {} movement frequencies from {}", species, this.getParameterValue(String.format("%sMovementDistribution", species)));
            final StopWatch sw = new StopWatch();
            sw.start();
            int numMovementEvents = 0; // the number of movements (each movement can contain several animals).
            int numMovesToSelf = 0;
            final FileInputIterator fle = new FileInput(this.getParameterValue(String.format("%sMovementDistribution", species))).iterator();
            while (fle.hasNext()) {
                final String[] split = fle.next().split(" ");
                final String[] unitIds = split[0].split("-");
                final Integer[] numAnimalsMoved = broadwick.utils.ArrayUtils.toIntegerArray(split[1]);
                if (!unitIds[0].equals(unitIds[1])) {
                    // ignore self moves - if they are legitimate moves then they would probably be
                    // covered by CTS links so would not be tested!
                    if (species.equals("Cattle")) {
                        settings.getCattleMovementFrequencies().add(new Pair<>(unitIds[0], unitIds[1]));
                        for (int num : numAnimalsMoved) {
                            farms.get(unitIds[0]).getOffMovementDistribution().setFrequency(num);
                            numMoves += num;
                        }
                    } else if (species.equals("Badger")) {
                        settings.getBadgerMovementFrequencies().add(new Pair<>(unitIds[0], unitIds[1]));
                        for (int num : numAnimalsMoved) {
                            reservoirs.get(unitIds[0]).getOffMovementDistribution().setFrequency(num);
                            numMoves += num;
                        }
                    }
                    
                    ++numMovementEvents;
                } else {
                    ++numMovesToSelf;
                }
            }
            log.debug("{}", String.format("Read %d movements in %s (ignoring %d moves to self)", numMovementEvents, sw, numMovesToSelf));

        } catch (IOException e) {
            log.error("Could not setup movement distributions. {}", Throwables.getStackTraceAsString(e));
        }
        return numMoves;
    }

    /**
     * Read a distribution from a file containing a discrete (frequency) distribution in the form x:frequency(x). // *
     * Read the SNP distances from the supplied file in the form "sampleA,herdA,yrA,sampleB,herdB,yrB,SNPs".
     * @param distFileName the name of the file containing the pairwise SNP differences
     * @return the distribution of pairwise snp differences.
     */
    private IntegerDistribution readDistribution(final String distFileName) {
        log.debug("Reading distribution from  {} ", distFileName);

        final IntegerDistribution distrib = new IntegerDistribution();
        try {
            final FileInputIterator fle = new FileInput(distFileName).iterator();
            while (fle.hasNext()) {
                final String line = fle.next();
                if (line != null) {
                    final String[] split = line.split(":");
                    final int x = Integer.valueOf(split[0].trim());
                    final int frequency = Integer.valueOf(split[1].trim());
                    distrib.setFrequency(x, frequency);
                }
            }
        } catch (IOException e) {
            log.error("Could not read distribution from {}", distFileName);
        }
        return distrib;
    }

    /**
     * Read the file containing the distribution of dates farms move animals to slaughter (this file contains also
     * known animals date of death in the cases this isn't a slaughter - no account is made of the difference).
     * @param distributionFilename the name of the file containing the distribution.
     * @return the distribution (date:Collection(farm ids)).
     */
    private Map<Integer, Collection<String>> readBirthDeathDistributions(final String distributionFilename) {
        log.debug("Reading B/D distribution from  {} ", distributionFilename);

        final Map<Integer, Collection<String>> dist = new HashMap<>();
        try {
            final FileInputIterator distribIterator = (new FileInput(distributionFilename)).iterator();
            while (distribIterator.hasNext()) {
                final String line = distribIterator.next();
                if (!line.startsWith("#")) {
                    final String[] tokens = line.split(":");
                    int date = Integer.parseInt(tokens[0].trim());
                    if (date >= settings.getStartDate() && date <= settings.getEndDate()) {
                        List<String> unitIds = Arrays.asList(tokens[1].trim().split(","));
                        dist.put(date, unitIds);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Could not read distribution from {}", distributionFilename);
        }
        return dist;
    }

    /**
     * Read the rate at which animals are sampled.
     * @param samplingRateFilename the name of the file containing the year and the number of samples taken that year.
     * @return a SimpleRegression object containing all the sampling rates in the file.
     */
    private Map<Integer, Double> readSamplingRatesPerYear(final String samplingRateFilename) {
        log.debug("Reading Sampling Rates per Year {} ", samplingRateFilename);

        Map<Integer, Double> grownSamplesDist = new HashMap<>();
        try (FileInput rates = new FileInput(samplingRateFilename)) {
            final FileInputIterator iterator = rates.iterator();
            while (iterator.hasNext()) {
                final String line = iterator.next();
                if (!line.startsWith("#") && !line.isEmpty()) {
                    final String[] lineTokens = StringUtils.split(line, ',');
                    final int year = Integer.parseInt(lineTokens[0]);
                    final double percentageSamplesGrown = Double.parseDouble(lineTokens[1]);

                    grownSamplesDist.put(year, percentageSamplesGrown);
                }
            }
        } catch (IOException ex) {
            log.error("Could not read sampling rates {}", ex.getLocalizedMessage());
        }
        log.debug("Distribution of grown samples {}", grownSamplesDist.toString());
        return grownSamplesDist;
    }

    /**
     * Read the reservoir definitions file. It contains a reservoir id a colon and a csv string of farm ids that are
     * within range of this reservoir.
     * @param network the name of the file containing the connectedReservoirs.
     * @return a map of a farm to all its neighbours.
     */
    private void readReservoirDefinitions(final String network) {
        log.info("Reading Reservoir <-> Farm contact network {} ", network);
        try {
            final FileInputIterator fle = new FileInput(network).iterator();
            while (fle.hasNext()) {
                final String[] split = fle.next().split(":");
                final String reservoirId = Integer.toString(Integer.parseInt(split[0].split("_")[1]));
                String[] connectedFarms = split[1].split(",");
                Reservoir reservoir = reservoirs.get(reservoirId);
                reservoir.getConnectedFarms().addAll(Arrays.asList(connectedFarms));
                for (String connectedFarm : connectedFarms) {
                    connectedReservoirs.get(connectedFarm.trim()).add(reservoir);
                }
            }
        } catch (IOException e) {
            log.error("Could not read reservoir definitions from {}", network);
        }
    }
    
    /**
     * Read the reservoir definitions file. It contains a reservoir id a colon and a csv string of farm ids that are
     * within range of this reservoir.
     * @param network the name of the file containing the connectedReservoirs.
     * @return a map of a farm to all its neighbours.
     */
    private void readReservoirDefinitions_AOH(final String network) {
        log.info("Reading reservoir definition from  {} ", network);
        reservoirs = new HashMap<>();
        try {
            final FileInputIterator fle = new FileInput(network).iterator();
            while (fle.hasNext()) {

                final String[] split = fle.next().split(":");
                final String reservoirId = split[0];
                Reservoir newReservoir = new Reservoir(reservoirId);
                String[] connectedFarms = split[1].split(",");
                newReservoir.getConnectedFarms().addAll(Arrays.asList(connectedFarms));
                for (String connectedFarm : connectedFarms) {
                    connectedReservoirs.get(connectedFarm.trim()).add(newReservoir);
                }
                reservoirInfections.put(reservoirId, new ArrayList<>());
                // and create the list of infected badgers in this reservoir
                reservoirs.put(reservoirId, newReservoir);
            }

            // Now add connectedReservoirs to those farms without one.
            int reservoirId = 0;
            for (Map.Entry<String, Collection<Reservoir>> entry : connectedReservoirs.entrySet()) {
                if (connectedReservoirs.get(entry.getKey()).isEmpty()) {
                    final String id = String.format("RESERVOIR_X%07d", ++reservoirId);
                    Reservoir newReservoir = new Reservoir(id);
                    newReservoir.getConnectedFarms().add(entry.getKey());
                    // and create the list of infected badgers in this reservoir
                    reservoirInfections.put(id, new ArrayList<InfectedBadger>());
                    reservoirs.put(id, newReservoir);
                    connectedReservoirs.get(entry.getKey()).add(newReservoir);
                }
            }

            // all farms should now have a connected reservoir
        } catch (IOException e) {
            log.error("Could not read reservoir definitions from {}", network);
        }
    }

    private ProjectSettings settings;
    private Map<String, Farm> farms;
    private Map<String, Reservoir> reservoirs;
    private Map<String, Collection<InfectedCow>> farmInfections;
    private Map<String, Collection<InfectedBadger>> reservoirInfections;
    private Map<String, InfectedCow> infectedCows;
    private Map<String, InfectedBadger> infectedBadgers;
    private Map<String, Collection<Reservoir>> connectedReservoirs;   // Map<farmId, Collection<connected connectedReservoirs>>
    // DB: [numM] numCattleMovements
    private int numCattleMovements;
    private int numBadgerMovements;
}
