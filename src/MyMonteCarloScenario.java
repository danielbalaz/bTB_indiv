package btbcluster;

import broadwick.BroadwickConstants;
import broadwick.graph.DirectedGraph;
import broadwick.graph.Edge;
import broadwick.montecarlo.MonteCarloResults;
import broadwick.montecarlo.MonteCarloScenario;
import broadwick.montecarlo.MonteCarloStep;
import broadwick.rng.RNG;
import broadwick.statistics.distributions.IntegerDistribution;
import broadwick.stochastic.SimulationController;
import broadwick.stochastic.SimulationEvent;
import broadwick.stochastic.SimulationState;
import broadwick.stochastic.StochasticSimulator;
import broadwick.stochastic.TransitionKernel;
import broadwick.stochastic.algorithms.TauLeapingFixedStep;
import broadwick.utils.CloneUtils;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.time.StopWatch;

/**
 * Run a single simulation/scenario on a static network.
 */
@Slf4j
public class MyMonteCarloScenario extends MonteCarloScenario {

    MyMonteCarloScenario(
            final MonteCarloStep step,
            final ProjectSettings settings,
            final Map<String, Farm> farmData,
            final Map<String, Reservoir> reservoirData,
            final Map<String, Collection<InfectedCow>> farmInfections,
            final Map<String, Collection<InfectedBadger>> reservoirInfections,
            final Map<String, InfectedCow> infectedCows,
            final Map<String, InfectedBadger> infectedBadgers,
            final int numCattleMovements,
            final int numBadgerMovements) {

        this.step = step;
        this.settings = settings;
        this.farmData = CloneUtils.deepClone(farmData);
        this.reservoirData = CloneUtils.deepClone(reservoirData);
        this.farmInfections = CloneUtils.deepClone(farmInfections);
        this.reservoirInfections = CloneUtils.deepClone(reservoirInfections);
        this.infectedCows = CloneUtils.deepClone(infectedCows);
        this.infectedBadgers = CloneUtils.deepClone(infectedBadgers);
        // DB: [numM] set numCattleMovements
        this.numCattleMovements = numCattleMovements;
        this.numBadgerMovements = numBadgerMovements;

        this.culledCows = new HashMap();
        this.expiredBadgers = new HashMap();
        results = new MyMonteCarloScenarioResults(settings.getObservedPairwiseDistanceDistribution());

        nextBadgerId = 0;
        nextCowId = 0;

    }

    @Override
    public MonteCarloResults run(int seed) {
        // override the random seed with one from parameter file
        // makes sense only if a single simulation is run
        // otherwise all simulations in a calibration would be the same
        if (settings.getSeed() > 0) {
            seed = settings.getSeed();
        }
        
        log.info("Seed: {}", seed);
        generator.seed(seed);

        final StopWatch sw = new StopWatch();
        sw.start();

        int numRejectedScenarios = 0;
        do {
            init();
            log.debug("Running scenario with {} movements at step {}", numCattleMovements, step);
            simulator.run();
            if (settings.isFilterShortEpidemics() && finishedPrematurely) {
                numRejectedScenarios++;
            }
        } while (settings.isFilterShortEpidemics() && finishedPrematurely);

        // Now we sample from the transmission tree to generate a (observed transmission) phylogenetic tree.
        // We do this by copying the transmision tree to the observed transmission tree and selectively removing nodes.
        log.debug("Sampling from transmission tree [{}] to generate phylogenetic tree.",
                results.getTransmissionTree().getVertexCount());

        results.setObservedTransmissionTree(results.getTransmissionTree());

        log.trace("Copied transmission tree has [{}] nodes before sampling.", results.getObservedTransmissionTree().getVertexCount());
        log.trace("Copied transmission tree has [{}] edges before sampling.", results.getObservedTransmissionTree().getEdges().size());

        for (InfectionNode node : results.getTransmissionTree().getVertices()) {
            // because we're iterating over the 'wrong' tree we need to get the actual node in this tree.
            InfectionNode obsNode = results.getObservedTransmissionTree().getVertex(node.getId());

            // remove this node if it is not the root and is not sampled.
            // If we are using the actual dates of the tests then we should include only those animals whose sampleDate 
            // is not null otherwise we say that animals are more likely to be detected later in the epidemic.
            log.trace("Checking node {}", obsNode.getId());
            if (obsNode.getId().equals(ROOT_ID) || includeNodeInPhylogeneticTree(obsNode)) {
                log.debug("Adding node {} {} to the phylogenetic tree.", obsNode, obsNode.getDetectionDate());
            } else {
                if (obsNode.getDetectionDate() == null) {
                    log.trace("Removing node {} from the phylogenetic tree. null", obsNode);
                } else {
                    log.trace("Removing node {} from the phylogenetic tree. {}", obsNode,
                            (int) Math.floor((obsNode.getDetectionDate()) / 365.0) + BroadwickConstants.getZERO_DATE().getYear()
                    );
                }
                Collection<Edge<InfectionNode>> inEdges = results.getObservedTransmissionTree().getInEdges(obsNode);
                Collection<Edge<InfectionNode>> outEdges = results.getObservedTransmissionTree().getOutEdges(obsNode);

                for (Edge<InfectionNode> inEdge : inEdges) {
                    InfectionNode source = inEdge.getSource();
                    for (Edge<InfectionNode> outEdge : outEdges) {
                        InfectionNode destination = outEdge.getDestination();
                        results.getObservedTransmissionTree().addEdge(new Edge<>(source, destination), source, destination);
                    }
                }
                results.getObservedTransmissionTree().removeVertex(obsNode);
                obsNode = null;
            }
        }
        log.trace("Observed transmission tree has [{}] nodes", results.getObservedTransmissionTree().getVertexCount());

        // Calculate measureables and update results object
        results.getNumInfectedCowsAtDeath().add(numInfectedCowsAtDeath);
        results.getNumInfectedCowsMoved().add(numInfectedCowsMoved);
        results.getNumInfectedBadgersAtDeath().add(numInfectedBadgersAtDeath);
        results.getNumInfectedBadgersMoved().add(numInfectedBadgersMoved);
        results.getPairwiseDistancesDistribution().add(calculatePairwiseDistances(results.getObservedTransmissionTree())); //<- this defines likelihood
        results.getOutbreakSize().add(infectedCows.size());

        log.debug("              Finished running scenario in {}", sw);

        results.getInfectedCows().putAll(culledCows);
        results.getInfectedCows().putAll(infectedCows);
        results.getInfectedBadgers().putAll(expiredBadgers);
        results.getInfectedBadgers().putAll(infectedBadgers);
        
        results.getCattleTest().addAll(cattleTest);
        results.getRecordedBadgers().addAll(recordedBadgers);
        results.getHerdTests().addAll(herdTests);
        
        results.getInitialSizes().addAll(initialSizes);
        results.getInitialInfStates().addAll(initialInfStates);
        results.getInitialRestrictions().addAll(initialRestrictions);
        
        results.getRecordedMovements().addAll(recordedMovements);

        // NOTE: temporary change, uncomment!
        // results.setScenarioId(this.id);


        Runtime.getRuntime().gc();
        return results;
    }

    @Override
    protected void finalize() throws Throwable {

        if (log.isDebugEnabled() || log.isTraceEnabled()) {
            int numSamplesTaken = 0;
            for (Map.Entry<String, InfectedCow> entry : infectedCows.entrySet()) {
                if (entry.getValue().getDateSampleTaken() != -1) {
                    ++numSamplesTaken;
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Finalising scenario, ");
//            sb.append("Finalising scenario, ").append(this.getId()).append("] ");
            sb.append("found ").append(infectedCows.size()).append(" infected cows ");
            sb.append("(").append(numSamplesTaken).append(" were sampled), ");
            sb.append(infectedBadgers.size()).append(" infected badgers with ");
            sb.append(restrictedHerds.size()).append(" herds under restriction.");
            sb.append("\nResults:\n").append(results.toString());

            log.debug("{}", sb.toString());
        }

        farmData.clear();
        reservoirData.clear();
        restrictedHerds.clear();
        farmInfections.clear();
        reservoirInfections.clear();
        infectedCows.clear();
        infectedBadgers.clear();
        
        cattleTest.clear();
        recordedBadgers.clear();
        herdTests.clear();
        
        initialSizes.clear();
        initialInfStates.clear();
        initialRestrictions.clear();
        
        recordedMovements.clear();

        farmData = null;
        reservoirData = null;
        restrictedHerds = null;
        farmInfections = null;
        reservoirInfections = null;
        infectedCows = null;
        infectedBadgers = null;
        
        cattleTest = null;
        recordedBadgers = null;
        herdTests = null;
        
        initialSizes = null;
        initialInfStates = null;
        initialRestrictions = null;
        
        recordedMovements = null;

        simulator = null;
        results = null;

        super.finalize();
    }

    @Override
    public MonteCarloScenario copyOf() {
        return new MyMonteCarloScenario(step, settings, farmData, reservoirData, farmInfections, reservoirInfections,
                infectedCows, infectedBadgers, numCattleMovements, numBadgerMovements);
    }

    /**
     * Initialise the scenario by creating the stochastic simulator, transition
     * kernel etc.
     */
    private void init() {
        final StopWatch sw = new StopWatch();
        sw.start();
        try {
            finishedPrematurely = false;

            // Initialise the amount manager, results object etc
            final MyAmountManager amountManager = new MyAmountManager(this);
            results = new MyMonteCarloScenarioResults(settings.getObservedPairwiseDistanceDistribution());
            restrictedHerds = new HashMap();
            
            this.cattleTest = new ArrayList();
            this.recordedBadgers = new ArrayList();
            this.herdTests = new ArrayList();
            
            this.initialSizes = new ArrayList();
            this.initialInfStates = new ArrayList();
            this.initialRestrictions = new ArrayList();
            
            this.recordedMovements = new ArrayList();

            // Set herd adn reservoir sizes,
            // remove movements off each herd that include a greater number of animals than exist in the unit.
            initialiseHerdSizes();
            initialiseReservoirSizes();
            
            // each scenario initialises its own infection seeds
            initialiseInfections();
            log.debug("Seeding scenario with {} infected cows", infectedCows.size());

            // We need to initially set some herds under movement restriction.
            initialiseMovementRestrictions();
            
            // Generate the test events for the simulation
            log.debug("Setting dates of last clear tests");
            setFarmLastTestDate(settings.getStartDate());

            // update/create the transmission tree.
            createTransmissionTree();
            
            // some cows are seeded with infection, but we are not putting them under restriction because
            // we assume that we don't know these cows are infected!
            //for (Map.Entry<String, InfectedCow> entry : infectedCows.entrySet()) {
            //    restrictedHerds.put(entry.getValue().getFarmId(), 0);
            //    final int nextTestDate = settings.getStartDate()+generator.getInteger(1, 59);
            //    settings.getThetaEvents().put(nextTestDate, new Test("", entry.getValue().getFarmId(), 
            //            entry.getValue().getFarmId(), nextTestDate, null, null));
            //}
            
            // Create the kernel, simulator, observers and controller
            createSimulator(amountManager);
            
            // Add a controller
            final SimulationController controller = createController();
            simulator.setController(controller);

            // finally create the transition kernel
            updateKernel();

        } catch (Exception ex) {
            log.error("Could not create scenario. {}", Throwables.getStackTraceAsString(ex));
        }

        log.debug("          Finished initialising scenario in {}.", sw);
    }

    
    /**
     * Set the initial herd sizes of cattle.
     * 
     */
    private void initialiseHerdSizes() {
        settings.getHerdSizeDistribution().setGenerator(generator);
        for (Map.Entry<String, Farm> entry : farmData.entrySet()) {
            int unitSize = settings.getHerdSizeDistribution().getRandomBin();
            entry.getValue().setHerdSize(unitSize);
            
            // DB: [HS] Checking whether a selected move can be realized,
            //     i.e. if the herd size is larger than the move.
            //     If herdSize is constant, do here, otherwise
            //     it will be done on the fly in scenarioObserver.doMovements()
            if (!settings.isHerdSizeFlex()) {
                IntegerDistribution offMoveSizes = new IntegerDistribution();
                for (int offMoveSize : entry.getValue().getOffMovementDistribution().getBins()) {
                    if (offMoveSize <= unitSize) {
                        offMoveSizes.setFrequency(offMoveSize,
                                entry.getValue().getOffMovementDistribution().getFrequency(offMoveSize));
                    }
                }
                entry.getValue().getOffMovementDistribution().clear();
                entry.getValue().getOffMovementDistribution().add(offMoveSizes);
            }
            
            // TODO: maybe add off movement distribution in the file
            recordInitialSize("farm", entry.getKey(), unitSize);
        }
    }
    
    /**
     * Set the initial herd sizes of cattle.
     * 
     */
    private void initialiseReservoirSizes() {
        settings.getReservoirSizeDistribution().setGenerator(generator);
        for (Map.Entry<String, Reservoir> entry : reservoirData.entrySet()) {
            int unitSize = settings.getReservoirSizeDistribution().getRandomBin();
            entry.getValue().setReservoirSize(unitSize);
            
            // DB: [HS] Checking whether a selected move can be realized,
            //     i.e. if the reservoir size is larger than the move.
            //     If reservoirSize is constant, do here, otherwise
            //     it will be done on the fly in scenarioObserver.doMovements()
            if (!settings.isReservoirSizeFlex()) {
                IntegerDistribution offMoveSizes = new IntegerDistribution();
                for (int offMoveSize : entry.getValue().getOffMovementDistribution().getBins()) {
                    if (offMoveSize <= unitSize) {
                        offMoveSizes.setFrequency(offMoveSize,
                                entry.getValue().getOffMovementDistribution().getFrequency(offMoveSize));
                    }
                }
                entry.getValue().getOffMovementDistribution().clear();
                entry.getValue().getOffMovementDistribution().add(offMoveSizes);
            }
            
            // TODO: maybe add off movement distribution in the file
            recordInitialSize("reservoir", entry.getKey(), unitSize);
        }
    }
    
    /**
     * Set the initial infection states of the cattle and badgers.
     *
     * @param generator the random number generator to use.
     */
    private void initialiseInfections() {
        // initialise the infected cows and badgers....
        // int badgerId = 0;
        
        Map<String, Set<Integer>> snp_init = new HashMap<>();
        Set<Integer> snps;
        
        int infectionsAdded = 0;
        while (infectionsAdded == 0) {
            // this just makes sure that we're not starting a simulation with NO infections.
            for (String infection : settings.getInitialInfectionStates().split(";")) {
                final String[] split = infection.split(":");
                
                String clade = split[2];
                if (snp_init.containsKey(clade)) {
                    snps = snp_init.get(clade);
                } else {
                    //     Pass negative mutation rate as a flag to force 'day' SNPs
                    snps = ProjectSettings.generateSnp(-1.0, settings.getInitMutClade(), generator, 0);
                    snp_init.put(clade, snps);
                }

                final String species = split[0].split("_")[0];
                
                switch (species) {
                    case "Cow":
                        infectionsAdded += initCow(split[0], split[1], snps, split[3]);
                        break;
                    case "Badger":
                        infectionsAdded += initBadger(split[0], split[1], snps, split[3]);
                        break;
                }
            }
        }
        
        // to accomodate the situations where there may be more seeds than in the seeded herd we update the seeded herd 
        // size here.
//        for (Map.Entry<String, InfectedCow> infected : infectedCows.entrySet()) {
//            String farmId = infected.getValue().getFarmId();
//            farmData.get(farmId).setHerdSize(farmData.get(farmId).getHerdSize() + 1);
//        }
    }

    /**
     * Seed an infectious cow
     * 
     * @param id identifier of the cow
     * @param unitId identifier of the unit (farm) the cow is on
     * @param probs Probabilities of states the cow may be in
     * @return number of seeded infectious animals
     */
    private int initCow(String id, String unitId, Set<Integer> snps, String probs) {
        int infectionsAdded = 0;
        final Double[] probsAsCsv = broadwick.utils.ArrayUtils.toDoubleArray(probs);
        
        InfectionStateCow initialInfectionState = generator.selectOneOf(InfectionStateCow.values(),
                                                                     ArrayUtils.toPrimitive(probsAsCsv));

        recordInitialInfState("cow", id, unitId, String.valueOf(initialInfectionState));
        
        if (initialInfectionState != InfectionStateCow.SUSCEPTIBLE) {
            InfectedCow animal = new InfectedCow(id, unitId, snps,
                                                 settings.getStartDate(), initialInfectionState);
            farmInfections.get(unitId).add(animal);
            infectedCows.put(animal.getId(), animal);
            log.debug("{}", String.format("Seeding infected cow %s (%s) on farm %s",
                                          id, initialInfectionState, unitId));
            infectionsAdded++;

            if (settings.isIncludeReservoir() && settings.isInitBadgersFromCows()) {
                Reservoir reservoir = generator.selectOneOf(settings.getFarmReservoirs().get(unitId));

                // Add an infected badger to this reservoir.
                InfectedBadger badger = new InfectedBadger(String.format("Badger_%s", id), reservoir.getId(),
                                                           snps, settings.getStartDate());
                reservoirInfections.get(reservoir.getId()).add(badger);
                infectedBadgers.put(badger.getId(), badger);
                infectionsAdded++;
            }
        } else {
            log.debug("Not adding animal {} ({})", id, initialInfectionState);
        }

        return (infectionsAdded);
    }
    
    /**
     * Seed an infectious badger
     * 
     * @param id identifier of the cow
     * @param unitId identifier of the unit (farm) the cow is on
     * @param probs Probabilities of states the cow may be in
     * @return number of seeded infectious animals
     */
    private int initBadger(String id, String unitId, Set<Integer> snps, String probs) {
        int infectionsAdded = 0;
        final Double[] probsAsCsv = broadwick.utils.ArrayUtils.toDoubleArray(probs);
        
        InfectionStateBadger initialInfectionState = generator.selectOneOf(InfectionStateBadger.values(),
                                                                           ArrayUtils.toPrimitive(probsAsCsv));

        recordInitialInfState("badger", id, unitId, String.valueOf(initialInfectionState));
        
        if (initialInfectionState != InfectionStateBadger.SUSCEPTIBLE) {
            InfectedBadger animal = new InfectedBadger(id, unitId, snps,
                                                    settings.getStartDate());
            reservoirInfections.get(unitId).add(animal);
            infectedBadgers.put(animal.getId(), animal);
            log.debug("{}", String.format("Seeding infected badger %s (%s) on farm %s",
                                          id, initialInfectionState, unitId));
            infectionsAdded++;
        } else {
            log.debug("Not adding animal {} ({})", id, initialInfectionState);
        }

        return (infectionsAdded);
    }

    
    /**
     * Set initially some herds under movement restriction.
     */
    private void initialiseMovementRestrictions() {
        Set<String> restrictedFarmIds = generator.selectManyOf(farmData.keySet(), settings.getNumInitialRestrictedHerds());
        for (String id : restrictedFarmIds) {
            int cleartests = generator.getInteger(0, 1);
            int lastTestDate = settings.getStartDate() - generator.getInteger(0, 60);
            if (cleartests == 0) {
                farmData.get(id).setLastClearTestDate(-1);
                farmData.get(id).setLastPositiveTestDate(lastTestDate);
            } else {
                farmData.get(id).setLastClearTestDate(lastTestDate);
                farmData.get(id).setLastPositiveTestDate(-1);
            }
            restrictedHerds.put(id, cleartests);
            
            // TODO: maybe add off movement distribution in the file
            recordInitialRestriction(id, cleartests, lastTestDate);
        }
        log.debug("Seeding scenario with {} restricted herds", restrictedHerds.size());
    }
    
    /**
     * Create the transmission tree.
     */
    private void createTransmissionTree() {
        final InfectionNode root = new InfectionNode(ROOT_ID, ROOT_ID, new HashSet<>(), null, null, false);
            results.getTransmissionTree().addVertex(root);

            for (Map.Entry<String, InfectedCow> cow : infectedCows.entrySet()) {
                InfectionNode node = new InfectionNode(cow.getKey(), cow.getValue().getFarmId(), cow.getValue().getSnps(), null, null, true);
                results.getTransmissionTree().addVertex(node);
                results.getTransmissionTree().addEdge(new Edge<>(root, node), root, node);
            }
            for (Map.Entry<String, InfectedBadger> badger : infectedBadgers.entrySet()) {
                InfectionNode node = new InfectionNode(badger.getKey(), badger.getValue().getReservoirId(), badger.getValue().getSnps(), null, null, false);
                results.getTransmissionTree().addVertex(node);
                results.getTransmissionTree().addEdge(new Edge<>(root, node), root, node);
            }
            log.debug("Initialised results, with transmission tree containing {} vertices", results.getTransmissionTree().getVertexCount());
    }
    
    /**
     * Create the kernel, simulator, observers and controller.
     * 
     * @param amountManager the MyAmountManager to use to create the kernel.
     */
    private void createSimulator(final MyAmountManager amountManager) {
        final TransitionKernel kernel = new TransitionKernel();
        simulator = new TauLeapingFixedStep(amountManager, kernel, settings.getStepSize());
        simulator.setRngSeed(generator.getInteger(0, Integer.MAX_VALUE));
        simulator.setStartTime(settings.getStartDate());

        simulator.getObservers().clear();
        final MyMonteCarloScenarioObserver observer = new MyMonteCarloScenarioObserver(simulator, this,
                settings.getStartDateMovements(),
                settings.getEndDateMovements(),
                settings.isIncludeReservoir(),
                generator);
        simulator.addObserver(observer);
    }
    
    private SimulationController createController() {
        final SimulationController controller = new SimulationController() {
                @Override
                public boolean goOn(final StochasticSimulator simulator) {

//                    final boolean noBreakdownsDetected = !(settings.isStopWithBreakdownDetected()
//                                                           && results.getReactorsAtBreakdownDistribution().size() > 0);
                    final double currentTime = simulator.getCurrentTime();
                    final boolean isDateValid = currentTime <= settings.getEndDate()
                                                && currentTime != Double.NEGATIVE_INFINITY
                                                && currentTime != Double.POSITIVE_INFINITY;
                    final boolean hasMoreTransitions = !simulator.getTransitionKernel().getCDF().isEmpty();
                    final boolean isLargerThanMaxEpidemicSize = infectedCows.size() > settings.getMaxInfectedCows() || infectedBadgers.size() > settings.getMaxInfectedBadgers();

                    // startDate is after (chronologically) the endDate and the scenario has not been rejected.
                    if (!isDateValid) {
                        log.debug("Terminating simulation time = {}", currentTime);
                    }
                    if (!hasMoreTransitions) {
                        log.debug("Terminating simulation (no more possible transitions)");
                        finishedPrematurely = true;
                    }
                    if (isLargerThanMaxEpidemicSize) {
                        log.debug("Terminating simulation (maximum outbreak [{}] exceeds max size)", infectedCows.size());
                        log.info("Terminating simulation (infected cows [{}], infected badgers [{}])", infectedCows.size(), infectedBadgers.size());
                    }

                    boolean outbreakDiedOut = !hasMoreTransitions;
                    if (outbreakDiedOut) {
                        results.incrementOutbreakContainedCount();
                    }

                    log.trace("Controller: go on = {}", isDateValid && !isLargerThanMaxEpidemicSize && !outbreakDiedOut);
                    return isDateValid && !isLargerThanMaxEpidemicSize && !outbreakDiedOut;
                }
            };

        return controller;
    }
    
    
    /**
     * Update the transition kernel.
     *
     * @return the updated Kernel.
     */
    protected final TransitionKernel updateKernel() {
        TransitionKernel transitionKernel = simulator.getTransitionKernel();

        // need to clean up the cloned final states properly here else we get memory leaks.
        transitionKernel.getTransitionEvents().forEach(new java.util.function.Consumer<SimulationEvent>() {

            @Override
            public void accept(final SimulationEvent event) {
                SimulationState finalState = event.getFinalState();
                finalState = null;
            }
        });
        // DB: [DoNotAccumulateRates]
        transitionKernel.clear();

        final StopWatch sw = new StopWatch();
        sw.start();

//        for (Map.Entry<String, Collection<InfectedCow>> sourceFarm : farmInfections.entrySet()) {
//            log.trace("{}", String.format("Updating transition kernel for farm %s (num susceptibles = %d, numInfected = %d)",
//                                          farmId, numSusInHerd, entry.getValue().size()));
        for (InfectedCow cow : infectedCows.values()) {
            String farmId = cow.getFarmId();
            final int numSusInHerd = farmData.get(farmId).getHerdSize() -
                    farmInfections.get(farmId).size();
//            log.trace("{}", String.format("Updating transition kernel for farm %s (num susceptibles = %d, numInfected = %d)",
//                                          farmId, numSusInHerd, entry.getValue().size()));
            InfectedCow finalState;
            switch (cow.getInfectionStatus()) {
                case EXPOSED:
                    // Add E->T event
                    finalState = new InfectedCow(cow.getId(), farmId, cow.getSnps(),
                                                 cow.getLastSnpGeneration(),
                                                 InfectionStateCow.TESTSENSITIVE);
                    transitionKernel.addToKernel(new SimulationEvent(cow, finalState),
                            // SIGMA
                            settings.getSigma());
//                            step.getCoordinates().get("sigma"));
                    break;
                case TESTSENSITIVE:
                    // Add T->I event
                    finalState = new InfectedCow(cow.getId(), farmId, cow.getSnps(),
                                                 cow.getLastSnpGeneration(),
                                                 InfectionStateCow.INFECTIOUS);
                    transitionKernel.addToKernel(new SimulationEvent(cow, finalState),
                                                 step.getCoordinates().get("gamma"));
                    break;
                case INFECTIOUS:
                    // Cattle -> Cattle transmission
                    // Add S->E event, since the newly infected cow does not have an id (we're not tracking 
                    // susceptible animals) we will give it an empty one and let the event handler deal with it.
                    finalState = new InfectedCow("", farmId, cow.getSnps(),
                                                 cow.getLastSnpGeneration(),
                                                 InfectionStateCow.EXPOSED);
                    transitionKernel.addToKernel(new SimulationEvent(cow, finalState),
                                                 numSusInHerd * step.getCoordinates().get("beta_CC"));

                    if (settings.isIncludeReservoir()) {
                        // Cattle -> Badger transmission
                        for (Reservoir reservoir : settings.getFarmReservoirs().get(farmId)) {
                            // since the newly infected badger does not have an id (we're not tracking 
                            // susceptible animals) we will give it an empty one and let the event handler deal with it.
                            final String reservoirId = reservoir.getId();
                            InfectedBadger infectedBadger = new InfectedBadger("", reservoirId, cow.getSnps(),
                                                                               cow.getLastSnpGeneration());
                            // NOTE: 'reservoirData.get(reservoirId)' instead of 'reservoir'
                            // because reservoir.getReservoirSize() = -1 for some reason.
                            // investigate!
                            // either make sure it will hold the correct value or get rid of,
                            // as it seems to not be necessary
                            final int numSusInReservoir = reservoirData.get(reservoirId).getReservoirSize() - reservoirInfections.get(reservoirId).size();
                            transitionKernel.addToKernel(new SimulationEvent(cow, infectedBadger),
                                                         numSusInReservoir * step.getCoordinates().get("beta_CB"));
                        }
                    }
                    break;
            }
        }

        if (settings.isIncludeReservoir()) {
            for (InfectedBadger badger : infectedBadgers.values()) {
                String reservoirId = badger.getReservoirId();
                Reservoir reservoir = reservoirData.get(reservoirId);

                final int numSusInReservoir = reservoirData.get(reservoirId).getReservoirSize() -
                reservoirInfections.get(reservoirId).size();

                // Badger -> Badger transmission
                InfectedBadger infectedBadger = new InfectedBadger("", reservoirId, badger.getSnps(),
                                                                    badger.getLastSnpGeneration());
                transitionKernel.addToKernel(new SimulationEvent(badger, infectedBadger),
                                             numSusInReservoir * step.getCoordinates().get("beta_BB"));
                    
                // Badger -> Cattle transmission
                for (String farmId : reservoir.getConnectedFarms()) {
                    InfectedCow infectedCow = new InfectedCow("", farmId, badger.getSnps(),
                                                               badger.getLastSnpGeneration(),
                                                                InfectionStateCow.EXPOSED);

                    final int numSusInHerd = farmData.get(farmId).getHerdSize() - farmInfections.get(farmId).size();
                    transitionKernel.addToKernel(new SimulationEvent(badger, infectedCow),
                                                    numSusInHerd * step.getCoordinates().get("beta_BC"));
                }
            }
        }
        
        sw.stop();
        log.trace("Updated kernel in {}.", sw.toString());
        if (log.isTraceEnabled()) {
            log.trace("transmission kernel \n{}", transitionKernel.toString());
        }
        return transitionKernel;
    }

    public int getNextCowId() {
        return ++nextCowId;
    }

    public int getNextBadgerId() {
        return ++nextBadgerId;
    }

    /**
     * Determine whether or not to include a node from the transmission tree in
     * the phylogenetic tree. Nodes are preferentially picked towards the end of
     * the outbreak rather than the start.
     *
     * @param node the node to be tested.
     * @return true if the node should be included, false otherwise.
     */
    private boolean includeNodeInPhylogeneticTree(final InfectionNode node) {
        if (node != null) {
            final Integer detectionDate = node.getDetectionDate();
            if (detectionDate != null) {
                Double probabilityOfGettingSample;
                int detectionYear = (int) Math.floor((detectionDate) / 365.0) + BroadwickConstants.getZERO_DATE().getYear();
                if (node.isCow()) {
                    probabilityOfGettingSample = settings.getCattleSamplingRate().get(detectionYear);
                } else {
                    probabilityOfGettingSample = settings.getBadgerSamplingRate().get(detectionYear);
                }
                
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

    /**
     * Calculate all the pairwise distances in a phylogenetic tree (i.e. the
     * nuber of unique SNPs in each pair of samples).
     *
     * @param phylogeneticTree the tree in which we will obtain our samples.
     * @return an IntegerDistribution of the pairwise distances.
     */
    private IntegerDistribution calculatePairwiseDistances(final DirectedGraph<InfectionNode, Edge<InfectionNode>> phylogeneticTree) {
        final IntegerDistribution distanceDist = new IntegerDistribution();
        Collection<InfectionNode> vertices = phylogeneticTree.getVertices();
        log.trace("Calculating pairwise snp distances on {} vertices", vertices.size());

//        if (vertices.size() > 1) {
//            for (InfectionNode nodeA : vertices) {
//                for (InfectionNode nodeB : vertices) {
//                    if (!nodeA.getId().equals(nodeB.getId())) {
//                        Set<Integer> nodeASnps = new HashSet(nodeA.getSnp());
//                        Set<Integer> nodeBSnps = new HashSet(nodeB.getSnp());
//                        distanceDist.setData(SetOperations.symDifference(nodeASnps, nodeBSnps).size());
//                        nodeASnps = null;
//                        nodeBSnps = null;
//                    }
//                }
//            }
//        }
        if (vertices.size() > 1) {
            for (InfectionNode nodeA : vertices) {
                for (InfectionNode nodeB : vertices) {
                    if (!nodeA.getId().equals(nodeB.getId())) {
                        int snpDiff = 0;
                        for (int snpA : nodeA.getSnp()) {
                            if (!nodeB.getSnp().contains(snpA)) {
                                snpDiff++;
                            }
                        }
                        for (int snpB : nodeB.getSnp()) {
                            if (!nodeA.getSnp().contains(snpB)) {
                                snpDiff++;
                            }
                        }
                        distanceDist.setFrequency(snpDiff);

                    }
                }
            }
        }
        log.debug("Pairwise snp distances for {} vertices = {}", vertices.size(), distanceDist);
        return distanceDist;
    }

    /**
     * Set the date of the last scheduled WHT on the farm.
     *
     * @param startDate the simulation start date (the earliest test possible).
     */
    private void setFarmLastTestDate(final int startDate) {
        for (Map.Entry<String, Farm> entry : farmData.entrySet()) {
            // pick a random number between 1 and TstIntervalInYears*365 and subtract that from startDate, this will be the date
            // the farm last had a clear test and will be used to schedule the next test.
            if (!restrictedHerds.containsKey(entry.getKey())) {
                int lastTestDate = generator.getInteger(0, ((int) Math.round((settings.getTestIntervalInYears() * 365) - 1)));
                entry.getValue().setLastClearTestDate(startDate - lastTestDate);
            }
        }
    }

    private void recordInitialSize(final String species, final String unitId, final int size) {
        final RecordInitialSize initialSize = new RecordInitialSize(species, unitId, size);
        this.initialSizes.add(initialSize);
    }
    
    private void recordInitialInfState(final String species, final String animalId, final String unitId, final String state) {
        final RecordInitialInfState initialInfState = new RecordInitialInfState(species, animalId, unitId, state);
        this.initialInfStates.add(initialInfState);
    }
    
    private void recordInitialRestriction(final String unitId, final int cleartest, final int lastTestDate) {
        final RecordInitialRestrictions initialRestriction = new RecordInitialRestrictions(unitId, cleartest, lastTestDate);
        this.initialRestrictions.add(initialRestriction);
    }
    
    
    @Getter
    @Setter
    private int numInfectedCowsMoved;
    @Getter
    @Setter
    private int numInfectedCowsAtDeath;
    @Getter
    @Setter
    private int numInfectedBadgersMoved;
    @Getter
    @Setter
    private int numInfectedBadgersAtDeath;
    @Getter
    private final int numCattleMovements;
    @Getter
    private final int numBadgerMovements;
//    @Getter
//    private final MonteCarloStep step;
    @Getter
    private final ProjectSettings settings;
    @Getter
    private Map<String, Integer> restrictedHerds;     // farm id and the number of clear tests.
    @Getter
    private Map<String, Collection<InfectedCow>> farmInfections;
    @Getter
    private Map<String, Collection<InfectedBadger>> reservoirInfections;
    @Getter
    private Map<String, InfectedCow> infectedCows;
    @Getter
    private final Map<String, InfectedCow> culledCows;
    @Getter
    private Map<String, InfectedBadger> infectedBadgers;
    @Getter
    private final Map<String, InfectedBadger> expiredBadgers;
    @Getter
    private Collection<CattleTest> cattleTest;
    @Getter
    private Collection<RecordBadger> recordedBadgers;
    @Getter
    private Collection<HerdTest> herdTests;
    @Getter
    private Collection<RecordInitialSize> initialSizes;
    @Getter
    private Collection<RecordInitialInfState> initialInfStates;
    @Getter
    private Collection<RecordInitialRestrictions> initialRestrictions;
    @Getter
    private Collection<RecordMovement> recordedMovements;
    @Getter
    private final RNG generator = new RNG(RNG.Generator.Well19937c);
    private boolean finishedPrematurely;
    @Getter
    private StochasticSimulator simulator;
    @Getter
    private MyMonteCarloScenarioResults results;
    @Getter
    private Map<String, Farm> farmData;
    @Getter
    private Map<String, Reservoir> reservoirData;
    private int nextBadgerId;
    private int nextCowId;
    private static final String ROOT_ID = "ROOT";
}
