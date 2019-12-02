package btbcluster;

import broadwick.BroadwickException;
import broadwick.data.Test;
import broadwick.rng.RNG;
import broadwick.statistics.distributions.HypergeometricDistribution;
import broadwick.statistics.distributions.IntegerDistribution;
import broadwick.stochastic.Observer;
import broadwick.stochastic.SimulationEvent;
import broadwick.stochastic.StochasticSimulator;
import broadwick.utils.Pair;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
//import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;

@Slf4j
public class MyMonteCarloScenarioObserver extends Observer {

    public MyMonteCarloScenarioObserver(StochasticSimulator simulator,
                                        MyMonteCarloScenario scenario,
                                        int startDate, int endDate,
                                        boolean includeReservoir,
                                        final RNG rng) {
        super(simulator);
        this.scenario = scenario;
        // DB_comment: this is not used anywhere, even though passed through arguments
//        this.includeReservoir = includeReservoir;
        this.numCattleMovementsForPeriod = (scenario.getNumCattleMovements() * scenario.getSettings().getStepSize())
                / (endDate - startDate);
        this.numBadgerMovementsForPeriod = (scenario.getNumBadgerMovements() * scenario.getSettings().getStepSize())
                / (endDate - startDate);
        
        this.generator = rng;
    }

    @Override
    public void started() {
        log.debug("Started observer at {}", getProcess().getCurrentTime());
    }

    @Override
    public void step() {
        final double currentTime = getProcess().getCurrentTime();
//        final MyAmountManager amountManager = (MyAmountManager) getProcess().getAmountManager();
        log.debug("Observing step at time {}", currentTime);

        // Register thetas
        log.debug("Registering theta events time {}", currentTime);
        registerThetaEvents(currentTime);

        // Perform the movements/births/deaths for this step.
        doCattleMovements();
        doBadgerMovements();

        // update transitions
        scenario.updateKernel();

        // Update some measureables in the results
        int infectedHerds = Iterables.size(Iterables.filter(scenario.getFarmInfections().entrySet(), new Predicate<Map.Entry<String, Collection<InfectedCow>>>() {

            @Override
            public boolean apply(Map.Entry<String, Collection<InfectedCow>> input) {
                return input.getValue().size() > 0;
            }
        }));
        int infectedReservoirs = Iterables.size(Iterables.filter(scenario.getReservoirInfections().entrySet(), new Predicate<Map.Entry<String, Collection<InfectedBadger>>>() {

            @Override
            public boolean apply(Map.Entry<String, Collection<InfectedBadger>> input) {
                return input.getValue().size() > 0;
            }
        }));
        this.scenario.getResults().getInfectedHerdsTimeSeries().append(infectedHerds).append(",");
        this.scenario.getResults().getHerdsUnderRestrictionTimeSeries().append(scenario.getRestrictedHerds().size()).append(",");
        this.scenario.getResults().getInfectedCowsTimeSeries().append(scenario.getInfectedCows().size()).append(",");
        this.scenario.getResults().getInfectedReservoirsTimeSeries().append(infectedReservoirs).append(",");
        this.scenario.getResults().getInfectedBadgersTimeSeries().append(scenario.getInfectedBadgers().size()).append(",");
    }

    @Override
    public void finished() {
        log.debug("Finished observer at {}", getProcess().getCurrentTime());
    }

    @Override
    public void theta(double time, Collection<Object> events) {
        log.debug("Observing {} tests at {} ", events.size(), time);
        boolean needToUpdateKernel = false;

        for (final Object event : events) {
            // We should only have test events.....
            if (event.getClass() == Test.class) {
                
                final Test testEvent = (Test) event;
                final String herdId = testEvent.getGroup();
                log.trace(String.format("Processing %s event on %s at %d", testEvent.getId(), testEvent.getLocation(), testEvent.getTestDate()));
                final int reactors = performWHT(testEvent.getTestDate(), herdId);
                if (reactors > 0) {
                    log.debug("WHT resulted in {} breakdowns at {}", reactors, testEvent.getTestDate());
                    log.debug("WHT resulted in {} infected animals left on farm {}", scenario.getFarmInfections().get(herdId).size(), herdId);

                    scenario.getResults().getReactorsAtBreakdownDistribution().setFrequency(reactors);
                    needToUpdateKernel = true;

                    // Either place the herd under movement restriction or reset the number of clear tests to zero
                    // and create a theta event for 60 days in the future.
                    scenario.getRestrictedHerds().put(herdId, 0);
                    scenario.getFarmData().get(testEvent.getLocation()).setLastClearTestDate(-1);
                    scenario.getFarmData().get(testEvent.getLocation()).setLastPositiveTestDate(((int) time));

                } else if (scenario.getRestrictedHerds().containsKey(herdId)) {
                    // if it was clear and is a restricted Herd, set another test.
                    Integer clearTests = scenario.getRestrictedHerds().get(herdId) + 1;
                    log.trace("Herd {} has had {} clear tests", herdId, clearTests);
                    if (clearTests >= 2) {
                        log.debug("WHT clear: restriction lifted on {} at {}", herdId, testEvent.getTestDate());
                        // herd is clear to trade - remove from restrictedHerds list and schedule another test
                        scenario.getRestrictedHerds().remove(herdId);
                    } else {
                        log.debug("WHT clear: {} passed {} follow-up tests", herdId, clearTests);
                        scenario.getRestrictedHerds().put(herdId, clearTests);
                    }
                    scenario.getFarmData().get(testEvent.getLocation()).setLastClearTestDate(-1);
                    scenario.getFarmData().get(testEvent.getLocation()).setLastPositiveTestDate(((int) time));
                } else {
                    log.debug("WHT clear: herd {} remains free at {}", herdId, testEvent.getTestDate());
                    scenario.getFarmData().get(testEvent.getLocation()).setLastClearTestDate(((int) time));
                    scenario.getFarmData().get(testEvent.getLocation()).setLastPositiveTestDate(-1);
                }
            }
        }

        if (needToUpdateKernel) {
            scenario.updateKernel();
        }
    }

    @Override
    public void observeEvent(SimulationEvent event, double tau, int times) {
//        log.debug("observing event {} ", event);
    }

    private void registerThetaEvents(final double time) {
        // Register theta events for the coming step.
        double testIntervalInDays = scenario.getSettings().getTestIntervalInYears() * 365;
        for (Map.Entry<String, Farm> entry : scenario.getFarmData().entrySet()) {

            if (entry.getValue().getLastPositiveTestDate() == -1) {
                // the herd is clear to trade (initially all herds are clear to trade so we check this first)
                int nextTestDate = (int) Math.round(entry.getValue().getLastClearTestDate() + testIntervalInDays);
                if ((nextTestDate >= time) && (nextTestDate < (time + scenario.getSettings().getStepSize()))) {
                    getProcess().registerNewTheta(this, nextTestDate, new Test("", entry.getKey(), entry.getKey(),
                                                                               nextTestDate, null, null));
                }
            }
            if (entry.getValue().getLastClearTestDate() == -1) {
                // the herd is under restriction, 
                int nextTestDate = entry.getValue().getLastPositiveTestDate() + 60;
                if ((nextTestDate >= time) && (nextTestDate < (time + scenario.getSettings().getStepSize()))) {
                    getProcess().registerNewTheta(this, nextTestDate, new Test("", entry.getKey(), entry.getKey(),
                                                                               nextTestDate, null, null));
                }
            }
        }
    }
    
    /**
     * Preform the cattle movements for this period. The algorithm is that we randomly pick a movement (which contains a
     * departure-destination farm) farm from which we move animals, if this is not a restricted herd and if it has
     * infected animals we select a subset of that herd to pretest and move.
     */
    private void doCattleMovements() {
        final String reasonOfTest = "pre-move";
        log.debug("Moving {} animals in period", numCattleMovementsForPeriod);
        final StopWatch sw = new StopWatch();
        sw.start();
        final int time = ((int) getProcess().getCurrentTime());

        // The algorithm is to loop through each farm (at random) and for each sample from the off movement distribution
        // to get the number of off moves for the period.
        // Then we loop over the movements (again at random) to select the destination farm and the number of animals 
        // moved to that farm, then premeovement test each animal and deal with the consequences of a positive test.
        int numMovedSoFar = 0;
        int infectedAnimalsMoved = 0;
        final int numKnownMoves = scenario.getSettings().getCattleMovementFrequencies().size();
        while (numMovedSoFar < numCattleMovementsForPeriod) {

            // Find a movement at random between 2 farms, according to the known farm-farm movement distribution.
            int rnd = scenario.getGenerator().getInteger(0, numKnownMoves - 1);
            Pair<String, String> movementData = scenario.getSettings().getCattleMovementFrequencies().get(rnd);

            // set up the movement if (and only if) neither farm is under movement restriction and there are infected
            // animals on the first (departing) farm [we don't track non-infecteds]
            String departureUnitId = movementData.getFirst();
            String destinationUnitId = movementData.getSecond();
            
            // apply if there are restriction rules on this species
            if (scenario.getRestrictedHerds().containsKey(departureUnitId)
                || scenario.getRestrictedHerds().containsKey(destinationUnitId))
                continue;
                
            int numAnimalsToBeMoved = 0;
            Farm departureUnit = scenario.getFarmData().get(departureUnitId);
            if (departureUnit.getOffMovementDistribution().getNumBins() > 0) {
                numAnimalsToBeMoved = departureUnit.getOffMovementDistribution().getRandomBin(scenario.getGenerator());
                // before we move animals make sure there are enough animals on the departure farm
                // if (numAnimalsToBeMoved > departureUnit.getHerdSize()) numAnimalsToBeMoved = 0;
                // but we are keeping the herd size (approximately) constant and so are not really interested in
                // tracking the movement of animals unless they are infected.
            }
            int population = departureUnit.getHerdSize();

            // There is '<' instead of '<=' in the last condition, so at least one animal stays in the unit.
            if (numAnimalsToBeMoved > 0 && (!scenario.getSettings().isHerdSizeFlex() || numAnimalsToBeMoved < population)) {
                // For each of these animalsToBeMoved, pre-movement test them. 
                // We assume that the animals to be moved are totally random and follows a hypergeometric distribution.
                // If any fail the test, we cull them and put the herd under restriction, else we move
                // animals.
                Collection<InfectedCow> infectedAnimalsInUnit = scenario.getFarmInfections().get(departureUnitId);
                log.debug("Cattle off movement dist = {}", departureUnit.getOffMovementDistribution().toCsv());

                int numInfectedAnimalsInUnit = infectedAnimalsInUnit.size();
                log.debug("{}", String.format("Cattle: Moving %d animals from farm %s (N=%d, Infections=%d) to farm %s",
                                              numAnimalsToBeMoved, departureUnitId, population, numInfectedAnimalsInUnit, destinationUnitId));
                
                // NOTE: It should NEVER happen that there are more infected cows on a farm 'numInfectedAnimalsInUnit'
                // than there are cows on that farm 'population'.
                // Controlling if there are susceptible cows on the farm should be handled in
                // MyAmountManager.performEvent()
                // However, it does happen (for badgers, maybe for cows as well...). Therefore:
                // 1. Investigate when having time
                // 2. Temporarily (!) set the population size to that of infected badgers
                // 3. When investigated and solved, remove this solution!
//                if (numInfectedAnimalsInUnit > population) {
//                    population = numInfectedAnimalsInUnit;
//                }
                
                // generator.getInteger(0, Integer.MAX_VALUE)

                final HypergeometricDistribution hyperDist = new HypergeometricDistribution(population,
                                                                                       numAnimalsToBeMoved,
                                                                                       numInfectedAnimalsInUnit);
                hyperDist.reseed(generator.getInteger(0, Integer.MAX_VALUE));
                final int numInfectedAnimalsToBeMoved = hyperDist.sample();
                
                log.debug("Cattle: numInfectedAnimalsToBeMoved = {}", numInfectedAnimalsToBeMoved);
                
                // TODO: it would be useful to also record the animals that were moved!
                recordMovement(time, "cows", departureUnitId, destinationUnitId,
                               population, numAnimalsToBeMoved, numInfectedAnimalsInUnit, numInfectedAnimalsToBeMoved,
                               rnd, -1);

                int numDetectedPreMoves = 0;
                
                // [DB:modif_1.1] The following code needs to be executed only if there are animals to be moved
                if (numInfectedAnimalsToBeMoved > 0) {
                    List<InfectedCow> infectedAnimalsToBeMoved = scenario.getGenerator().selectManyOf(infectedAnimalsInUnit,
                                                                                                      numInfectedAnimalsToBeMoved);
                    // only if there is surveilance of the species
                    for (InfectedCow cow : infectedAnimalsToBeMoved) {
                        // [DB:modif_3.2] 'departureUnitId'
                        if (testAndRemoveIfInfected(cow.getId(), time, departureUnitId, reasonOfTest)) {
                            ++numDetectedPreMoves;
                        }
                    }

                    if (numDetectedPreMoves > numInfectedAnimalsInUnit) {
                        throw new BroadwickException("ERROR: There never should be more 'detected' than 'infected' animals.");
                    }
                    
                    // only if there is surveilance of the species
                    if (numDetectedPreMoves > 0) {
                        // If I detected any cow they are culled and now put the herd under restriction.
                        scenario.getRestrictedHerds().put(departureUnitId, 0);
                        scenario.getFarmData().get(departureUnitId).setLastClearTestDate(-1);
                        scenario.getFarmData().get(departureUnitId).setLastPositiveTestDate(time);
                        log.trace("{}", String.format("Cattle: Moving %d animals from %s to %s (%d of whom were infected)",
                                                      numAnimalsToBeMoved, departureUnitId, destinationUnitId,
                                                      numInfectedAnimalsToBeMoved));
                        // No animals are moved off this farm.
                        numAnimalsToBeMoved = 0;
                        // [DB:modif_2] As there are detected infectious animals in the herd,
                        //              the transfer will not take place, hence remove the following line:
//                            infectedAnimalsMoved += numInfectedAnimalsToBeMoved;
                    } else {
                        // No animals detected, now move the 'numInfectedAnimalsToBeMoved' infected animals.
                        // [DB:modif_1.2] Due to introducing modif_1.1, the following condition will always be true, hence can be removed
//                            if (infectedAnimalsToBeMoved.size() > 0) {
                        log.trace("{}", String.format("Cattle: Moving %d (%d undetected) infection from %s to %s",
                                                      numAnimalsToBeMoved, numInfectedAnimalsToBeMoved, departureUnitId, destinationUnitId));
                        for (InfectedCow cow : infectedAnimalsToBeMoved) {
                            scenario.getFarmInfections().get(departureUnitId).remove(cow);
                            scenario.getFarmInfections().get(destinationUnitId).add(cow);
                            cow.setFarmId(destinationUnitId);
                            // DB: [RememberFarmIDs]
                            cow.getAllFarmIds().add(destinationUnitId);
                        }

                        final int destinationUnitSize = scenario.getFarmData().get(destinationUnitId).getHerdSize();

                        // we have moved unobserved infections - we may need to update the herd size (in case it
                        // causes an exception in HypergeometricDistribution.sample()
                        // [DB:modif_6] change condition '>=' to '>'
                        if (scenario.getSettings().isHerdSizeFlex()) {
                            scenario.getFarmData().get(departureUnitId).changeSize(-numAnimalsToBeMoved);
                            scenario.getFarmData().get(destinationUnitId).changeSize(numAnimalsToBeMoved);
                        } else if (scenario.getFarmInfections().get(destinationUnitId).size() > destinationUnitSize) {
                                final int newSize = scenario.getFarmInfections().get(destinationUnitId).size();
                                log.trace("{}", String.format("Cattle: We have moved %d undetected animals: updating farm %s size to %d",
                                                              numInfectedAnimalsToBeMoved, destinationUnitId, newSize));
                                scenario.getFarmData().get(destinationUnitId).setHerdSize(newSize);
                        }
//                            }

                        log.trace("{}", String.format("Cattle: Moving %d animals from %s to %s",
                                                      numAnimalsToBeMoved,
                                                      departureUnitId,
                                                      destinationUnitId));
                        infectedAnimalsMoved += numInfectedAnimalsToBeMoved;
                    }
                    // [DB:modif_1.3] end of condition
                }
                
                recordHerdTest(time, departureUnitId, numInfectedAnimalsInUnit, numDetectedPreMoves, reasonOfTest);

                numMovedSoFar += numAnimalsToBeMoved;
            }
        
        }
        
        log.debug("Cattle: Moved {} animals in period (time taken = {})", numMovedSoFar, sw);
        log.debug("Cattle: Moved {}/{} infected animals in period", infectedAnimalsMoved, scenario.getInfectedCows().size());
        
        scenario.setNumInfectedCowsMoved(scenario.getNumInfectedCowsMoved() + infectedAnimalsMoved);
        // Now do the births/deaths
        doSlaughterhouseMoves();
//        doBirthsAndDeaths();
    }
    
        /**
     * Preform the cattle movements for this period.
     * The algorithm is that we randomly pick a movement (which contains a
     * departure-destination reservoir) reservoir from which we move animals,
     * if it has infected animals we select a subset of that herd to move.
     */
    private void doBadgerMovements() {
        log.debug("Moving {} animals in period", numBadgerMovementsForPeriod);
        final StopWatch sw = new StopWatch();
        sw.start();
        final int time = ((int) getProcess().getCurrentTime());

        int numMovedSoFar = 0;
        int infectedAnimalsMoved = 0;
        final int numKnownMoves = scenario.getSettings().getBadgerMovementFrequencies().size();
        
        while (numMovedSoFar < numBadgerMovementsForPeriod) {
            // Find a movement at random between 2 farms, according to the known farm-farm movement distribution.
            int rnd = scenario.getGenerator().getInteger(0, numKnownMoves - 1);
            Pair<String, String> movementData = scenario.getSettings().getBadgerMovementFrequencies().get(rnd);

            String departureUnitId = movementData.getFirst();
            String destinationUnitId = movementData.getSecond();
            
            int numAnimalsToBeMoved = 0;
            Reservoir departureUnit = scenario.getReservoirData().get(departureUnitId);
            if (departureUnit.getOffMovementDistribution().getNumBins() > 0) {
                numAnimalsToBeMoved = departureUnit.getOffMovementDistribution().getRandomBin(scenario.getGenerator());
                // before we move animals make sure there are enough animals on the departure farm
                // if (numAnimalsToBeMoved > departureUnit.getHerdSize()) numAnimalsToBeMoved = 0;
                // but we are keeping the herd size (approximately) constant and so are not really interested in
                // tracking the movement of animals unless they are infected.
            }
            int population = departureUnit.getReservoirSize();

            // There is '<' instead of '<=' in the last condition, so at least one animal stays in the unit.
            if (numAnimalsToBeMoved > 0 && (!scenario.getSettings().isReservoirSizeFlex() || numAnimalsToBeMoved < population)) {
                Collection<InfectedBadger> infectedAnimalsInUnit = scenario.getReservoirInfections().get(departureUnitId);
                log.debug("Badgers off movement dist = {}", departureUnit.getOffMovementDistribution().toCsv());

                int numInfectedAnimalsInUnit = infectedAnimalsInUnit.size();
                log.debug("{}", String.format("Badgers: Moving %d animals from farm %s (N=%d, Infections=%d) to farm %s",
                                              numAnimalsToBeMoved, departureUnitId, population, numInfectedAnimalsInUnit, destinationUnitId));
                
                // NOTE: It should NEVER happen that there are more infected bagders in a reservoir 'numInfectedAnimalsInUnit'
                // than there are badgers in that reservoir 'population'.
                // Controlling if there are susceptible badgers in the reservoir should be handled in
                // MyAmountManager.performEvent()
                
                final HypergeometricDistribution hyperDist = new HypergeometricDistribution(population,
                                                                                       numAnimalsToBeMoved,
                                                                                       numInfectedAnimalsInUnit);
                hyperDist.reseed(generator.getInteger(0, Integer.MAX_VALUE));
                final int numInfectedAnimalsToBeMoved = hyperDist.sample();
                
                log.debug("Badgers: numInfectedAnimalsToBeMoved = {}", numInfectedAnimalsToBeMoved);
                
                recordMovement(time, "badgers", departureUnitId, destinationUnitId,
                               population, numAnimalsToBeMoved, numInfectedAnimalsInUnit, numInfectedAnimalsToBeMoved,
                               rnd, -1);
                
                if (numInfectedAnimalsToBeMoved > 0) {
                    List<InfectedBadger> infectedAnimalsToBeMoved = scenario.getGenerator().selectManyOf(infectedAnimalsInUnit,
                                                                                                         numInfectedAnimalsToBeMoved);
                    log.trace("{}", String.format("Badgers: Moving %d (%d undetected) infection from %s to %s",
                                                      numAnimalsToBeMoved, numInfectedAnimalsToBeMoved, departureUnitId, destinationUnitId));
                        for (InfectedBadger animal : infectedAnimalsToBeMoved) {
                            scenario.getReservoirInfections().get(departureUnitId).remove(animal);
                            scenario.getReservoirInfections().get(destinationUnitId).add(animal);
                            animal.setReservoirId(destinationUnitId);
                            // DB: [RememberReservoieIDs]
                            animal.getAllReservoirIds().add(destinationUnitId);
                            
                            testBadger(animal.getId(), departureUnitId, destinationUnitId, false, "movement");
                        }

                        final int destinationUnitSize = scenario.getReservoirData().get(destinationUnitId).getReservoirSize();
                        
                        // we have moved unobserved infections - we may need to update the unit size (in case it
                        // causes an exception in HypergeometricDistribution.sample()
                        if (scenario.getSettings().isReservoirSizeFlex()) {
                            scenario.getReservoirData().get(departureUnitId).changeSize(-numAnimalsToBeMoved);
                            scenario.getReservoirData().get(destinationUnitId).changeSize(numAnimalsToBeMoved);
                        } else if (scenario.getReservoirInfections().get(destinationUnitId).size() > destinationUnitSize) {
                                final int newSize = scenario.getReservoirInfections().get(destinationUnitId).size();
                                log.trace("{}", String.format("Badgers: We have moved %d undetected animals: updating farm %s size to %d",
                                                              numInfectedAnimalsToBeMoved, destinationUnitId, newSize));
                                scenario.getReservoirData().get(destinationUnitId).setReservoirSize(newSize);
                        }

                        log.trace("{}", String.format("Badgers: Moving %d animals from %s to %s",
                                                      numAnimalsToBeMoved,
                                                      departureUnitId,
                                                      destinationUnitId));
                        infectedAnimalsMoved += numInfectedAnimalsToBeMoved;

                }
                numMovedSoFar += numAnimalsToBeMoved;
            }
        }
        log.debug("Badgers: Moved {} animals in period (time taken = {})", numMovedSoFar, sw);
        log.debug("Badgers: Moved {}/{} infected animals in period", infectedAnimalsMoved, scenario.getInfectedCows().size());
        
        scenario.setNumInfectedBadgersMoved(scenario.getNumInfectedBadgersMoved() + infectedAnimalsMoved);
        // Now do the births/deaths
        doBadgerDeaths();
    }
        
    /**
     * Perform slaughterhouse movements from the distribution of moves to slaughter.
     */
    private void doSlaughterhouseMoves() {

        // We do not explicitly keep track of the herd size (rather assume it stays fairly constant, i.e. can 
        // be modelled with a Normal distribution) and the deaths are all subject to testing.    
        // The data file contains the date a movement was made and a list of farms from which an animal was taken.
        // Where several animals were taken from the same farm the farm id is repeated. We only want the total number 
        // of animals removed in this time period.
        final double currentTime = getProcess().getCurrentTime();
        final int time = (int) currentTime;
        final String reasonOfTest = "abattoir";
        final StopWatch sw = new StopWatch();
        sw.start();

        // this is a list of farm ids that have a move to slaughter
        List<String> movesForPeriod = scenario.getSettings().getCattleDeathDistribution().entrySet().stream()
                .filter(e -> e.getKey() >= currentTime && e.getKey() <= (currentTime + scenario.getSettings().getStepSize()))
                .map(map -> map.getValue())
                .flatMap(c -> c.stream())
                .collect(Collectors.toList());

        List<String> distinctFarmsMovingAnimals = movesForPeriod.stream().distinct().collect(Collectors.toList());

        // TODO: Perhaps we may be better off removing animals from random farms rather than replaying exact
        // slaughterhouse moves...
        int numReactorsRemoved = 0;
        for (final String farmId : distinctFarmsMovingAnimals) {
            final Farm farm = scenario.getFarmData().get(farmId);
            final int numInfectedAnimalsInUnit = scenario.getFarmInfections().get(farmId).size();

            // how many moves do we need off this farm?
            int numAnimalsToBeRemoved = (int) movesForPeriod.stream().filter(e -> farmId.equals(e)).count();
            
            int population = farm.getHerdSize();
            
            // NOTE: if the number of animals to be put ot death is higher than the number of animals on the farm,
            // only send the amount that there are.
            // This will reduce the total number of dead animals, which may be a problem :-/
            if (numAnimalsToBeRemoved > population) {
                numAnimalsToBeRemoved = population;
            }

            // NOTE: The following should NOT HAPPEN!
//            if (numInfectedAnimalsInUnit > population) {
//                population = numInfectedAnimalsInUnit;
//            }
            
            // Select the number of infected animals from the farm to move to slaughter.
            final HypergeometricDistribution hyperDist = new HypergeometricDistribution(population,
                                                                                        numAnimalsToBeRemoved,
                                                                                        numInfectedAnimalsInUnit);
            hyperDist.reseed(generator.getInteger(0, Integer.MAX_VALUE));
            final int numInfectedAnimalsForRemoval = hyperDist.sample();
            
            recordMovement(time, "cows", farmId, "",
                           population, numAnimalsToBeRemoved, numInfectedAnimalsInUnit, numInfectedAnimalsForRemoval,
                           -1, -1);

            // select these animals from the farm
            List<InfectedCow> animalsForSlaughter = scenario.getGenerator().selectManyOf(scenario.getFarmInfections().get(farmId),
                                                                                         numInfectedAnimalsForRemoval);
            
            int numReactorsOnFarm = 0;
            // test every animal as they are all tested at slaughter
            for (InfectedCow cow : animalsForSlaughter) {
                if (testAndRemoveIfInfected(cow.getId(), time, farmId, reasonOfTest)) {
                    numReactorsOnFarm++;

                    // Either place the herd under movement restriction or reset the number of clear tests to zero
                    // and create a theta event for 60 days in the future.
                    scenario.getRestrictedHerds().put(farmId, 0);
                    scenario.getFarmData().get(farmId).setLastClearTestDate(-1);
                    scenario.getFarmData().get(farmId).setLastPositiveTestDate(time);
                    // We need to create a theta event to retest in 2 months; this will be done in 
                    // the registerNewTheta method (theta events are only registered for each period.
                }
            }
            
            if (numReactorsOnFarm > numInfectedAnimalsInUnit) {
                throw new BroadwickException("ERROR: There never should be more 'detected' than 'infected' animals.");
            }
            
            recordHerdTest(time, farmId, numInfectedAnimalsInUnit, numReactorsOnFarm, reasonOfTest);
            
            numReactorsRemoved += numReactorsOnFarm;
        }

        // NOTE: What does this do?
        IntegerDistribution movementSizeDist = new IntegerDistribution();
        distinctFarmsMovingAnimals.forEach((farmId) -> {
            movementSizeDist.setFrequency(Collections.frequency(distinctFarmsMovingAnimals, farmId));
        });

        log.debug("{}", String.format("Removed %d reactors, time taken=%s", numReactorsRemoved, sw));
        scenario.setNumInfectedCowsAtDeath(scenario.getNumInfectedCowsAtDeath() + numReactorsRemoved);
    }

    private void doBadgerDeaths() {
        final double currentTime = getProcess().getCurrentTime();
        final int date = (int) currentTime;
        
        List<InfectedBadger> animalsToDie = new ArrayList<>();
        int dead;
        for (final InfectedBadger animal : scenario.getInfectedBadgers().values()) {
            final double rnd = scenario.getGenerator().getDouble();
            if (rnd < scenario.getSettings().getBadgerDeathRate()) {
                animalsToDie.add(animal);
                dead = 1;
            } else {
                dead = 0;
            }
            recordMovement(date, "badgers", animal.getReservoirId(), "",
                           scenario.getReservoirData().get(animal.getReservoirId()).getReservoirSize(),
                           dead, 1, dead,
                           -1, rnd);
        }
        
        for (final InfectedBadger animal : animalsToDie) {
            final String animalId = animal.getId();
            final String unitId = scenario.getInfectedBadgers().get(animalId).getReservoirId();
            
            // TODO: parameter 'capture' should indicate if the dead badger was found or not
            testBadger(animal.getId(), unitId, "", false, "death");
            
            animal.getSnps().addAll(ProjectSettings.generateSnp(scenario.getStep().getCoordinates().get("mutationRate"),
                                                date, scenario.getGenerator(),
                                                animal.getLastSnpGeneration()));
            animal.setLastSnpGeneration(date);
            animal.setDateSampleTaken(date);
            
            // set the detection date for the node in the transmission tree.
            scenario.getResults().getTransmissionTree().getVertex(animalId).setDetectionDate(date);

            // record the dead animal
            scenario.getExpiredBadgers().put(animalId, animal);

            scenario.getReservoirInfections().get(unitId).remove(animal);
            scenario.getInfectedBadgers().remove(animalId);
        }
        
        scenario.setNumInfectedBadgersAtDeath(scenario.getNumInfectedBadgersAtDeath() + animalsToDie.size());
    }
        
    /**
     * Perform a whole herd test WHT at a given time date on a given herd.
     * @param daysFromStart the number of days from the start date to perform the WHT.
     * @param farmId        The id of the herd
     * @return the number of reactors found in the test.
     */
    private synchronized int performWHT(final int daysFromStart, final String farmId) {
//        final int time = ((int) getProcess().getCurrentTime());
        final String reasonOfTest = "WHT";
        final Collection<InfectedCow> infections = scenario.getFarmInfections().get(farmId);
        final int numInfections = infections.size();

        List<String> infectedCattleIds = new ArrayList<>();
        for (InfectedCow cow : scenario.getFarmInfections().get(farmId)) {
            infectedCattleIds.add(cow.getId());
        }

        int breakdowns = 0;
        if (!infectedCattleIds.isEmpty()) {
            log.debug(String.format("Performing WHT on herd %s (infected animals=%d) at day %d",
                                    farmId, infections.size(), daysFromStart));

            for (final String cowId : infectedCattleIds) {
                if (log.isTraceEnabled()) {
                    log.trace("Checking animal {}", cowId);
                }

                if (testAndRemoveIfInfected(cowId, daysFromStart, farmId, reasonOfTest)) {
                    breakdowns++;
                }
            }
        }

        if (breakdowns > numInfections) {
            throw new BroadwickException("ERROR: There never should be more 'detected' than 'infected' animals.");
        }
        
        recordHerdTest(daysFromStart, farmId, numInfections, breakdowns, reasonOfTest);
        
        // Add the number of breakdowns to the breakdown size distribution.
        scenario.getResults().getReactorsAtBreakdownDistribution().setFrequency(breakdowns);
        return breakdowns;
    }

//    private int testHerd(final int date, final String farmId, final String event) {
//        InfectionState status;
//        List<String> infectedCattleIds = new ArrayList<>();
//        int numberTestedPositive;
//        for (InfectedCow cow : scenario.getFarmInfections().get(farmId)) {
//            status = cow.getInfectionStatus();
//            if ((InfectionState.TESTSENSITIVE == status || InfectionState.INFECTIOUS == status)) {
//                infectedCattleIds.add(cow.getId());
//            }
//        }
//        if (infectedCattleIds.isEmpty()) {
//            return 0;
//        } else {
//            org.apache.commons.math3.distribution.BinomialDistribution BinomialDistribution = new org.apache.commons.math3.distribution.BinomialDistribution(infectedCattleIds.size(), scenario.getStep().getCoordinates().get("testSensitivity"));
//            numberTestedPositive = BinomialDistribution.sample();
//            
//            List<String> removeIDs = scenario.getGenerator().selectManyOf(infectedCattleIds, numberTestedPositive);
//            
//            return numberTestedPositive;
//        }
//    }
    
    private boolean testAndRemoveIfInfected(final String cowId, final int date, final String farmId, final String event) {
        final InfectedCow cow = scenario.getInfectedCows().get(cowId);
        final InfectionStateCow status = cow.getInfectionStatus();
        boolean testPositive = false;
        
        if ((InfectionStateCow.TESTSENSITIVE == status || InfectionStateCow.INFECTIOUS == status)) {
            
            final double rnd = scenario.getGenerator().getDouble();
            
            if (rnd <= scenario.getStep().getCoordinates().get("testSensitivity")) {
                testPositive = true;
                
                // The animal is a potential reactor in this herd and tested positive.
                log.trace("{} tested positive at {}", cowId, date);

                // We sample the animal so update the SNPs and set the sampleTaken date for the cow.
                cow.getSnps().addAll(
                        ProjectSettings.generateSnp(scenario.getStep().getCoordinates().get("mutationRate"),
                                                    date, scenario.getGenerator(),
                                                    cow.getLastSnpGeneration()));
                cow.setLastSnpGeneration(date);
                cow.setDateSampleTaken(date);

                // set the detection date for the node in the transmission tree.
                scenario.getResults().getTransmissionTree().getVertex(cowId).setDetectionDate(date);

                // record the culled cow
                scenario.getCulledCows().put(cowId, cow);

                // finally remove the cow from the herd.
                // DB(debug): check correct removing of cows
                // replace the next with the following
    //            scenario.getFarmInfections().get(farmId).remove(cow);

                Collection<InfectedCow> infectedAnimalsOnFarm = scenario.getFarmInfections().get(farmId);
                infectedAnimalsOnFarm.remove(cow);
                scenario.getFarmInfections().put(farmId, infectedAnimalsOnFarm);

                scenario.getInfectedCows().remove(cowId);
                // DB: [HS] DO NOT change the herd size when a cow is removed due to a positive test result
                //     because, since the herd size is not adjusted after scheduled death,
                //     this introduces inconsistencies
    //            scenario.getFarmData().get(farmId).setHerdSize(scenario.getFarmData().get(farmId).getHerdSize() - 1);
            }
        }
        
        final CattleTest cattleTest = new CattleTest(date, farmId, cowId, testPositive, status, event);
        scenario.getCattleTest().add(cattleTest);
        
        return testPositive;
    }

    private void testBadger(final String badgerId, final String departureUnitId, final String destinationUnitId, final boolean capture, final String event) {
        final InfectedBadger badger = scenario.getInfectedBadgers().get(badgerId);
        final InfectionStateBadger status = InfectionStateBadger.INFECTIOUS;
        boolean testPositive = false;
        int date = ((int) getProcess().getCurrentTime());
        
        final double rnd = scenario.getGenerator().getDouble();
        
        // TODO: REPLACE testSensitivity with relevant value for badgers
        if (capture && rnd <= scenario.getStep().getCoordinates().get("testSensitivity")) {
            testPositive = true;
            
            // TODO: REPLACE mutation rate with relevant value for badgers
            //       - not for now, not enough info to infer anyway
            // We sample the animal, so update the SNPs and set the sampleTaken date.
            badger.getSnps().addAll(
                    ProjectSettings.generateSnp(scenario.getStep().getCoordinates().get("mutationRate"),
                                                date, scenario.getGenerator(),
                                                badger.getLastSnpGeneration()));
            badger.setLastSnpGeneration(date);
            badger.setDateSampleTaken(date);

            // TODO: Transmission tree - what about badgers?
            // probably enter even if not captured? ...
            
        }
        
        final RecordBadger recordBadger = new RecordBadger(date, badgerId, departureUnitId, destinationUnitId, capture, testPositive, status, event);
        scenario.getRecordedBadgers().add(recordBadger);
    }
    
    private void recordHerdTest(final int date, final String unitId, final int infected, final int reactors, final String event) {
        final HerdTest herdTest = new HerdTest(date, unitId, infected, reactors, event);
        scenario.getHerdTests().add(herdTest);
    }
    
    private void recordMovement(final int date, final String species, final String unitID_from, final String unitID_to,
                                final int unit_size, final int anim_move, final int inf_anim, final int inf_move,
                                final int rnd_choice, final double rnd_num) {
        final RecordMovement recordMovement = new RecordMovement(date, species, unitID_from, unitID_to,
                                                                 unit_size, anim_move, inf_anim, inf_move, rnd_choice, rnd_num);
        scenario.getRecordedMovements().add(recordMovement);
    }
    
    private final int numCattleMovementsForPeriod;
    private final int numBadgerMovementsForPeriod;
    private final MyMonteCarloScenario scenario;
    
    private final RNG generator;
}
