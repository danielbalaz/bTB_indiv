package btbcluster;

import broadwick.BroadwickException;
import broadwick.rng.RNG;
import broadwick.statistics.distributions.IntegerDistribution;
import broadwick.utils.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * This class contains all the (constant) project settings such as simulation
 * start and end dates, movement distributions etc. These will never change and
 * are available to every scenario, this calls is NOT thread safe as it should
 * only be read once created.
 */
public class ProjectSettings {

    public ProjectSettings() {
        this.initStepSize = false;
    }

    /**
     * Determine the SNP that is to be applied to a [mutated] strain. The algorithm is very simplistic as we do not need
     * to record where the SNP occurred in the genome so we have a static value that is incremented when we introduce a
     * new SNP.
     * @param mutationRate      the transmissionWeight SNPs appear in the genome per day.
     * @param day               the number of days over which SNPs are accumulated, if negative then there will be at
     *                          least one SNP generated.
     * @param lastSnpGeneration the day the last snp was generated.
     * @param generator         the RNG to use
     * @return a collection (HashSet) of snps that appeared since lastSnpGeneration
     */
    public static Set<Integer> generateSnp(final double mutationRate, final long day,
            final RNG generator, Integer lastSnpGeneration) {

        final long numSNPs;
        Set<Integer> snps = new HashSet<>();

        // DB: [Mut]
        if (mutationRate < 0) {
            // numSNPs must be at least 1.
            numSNPs = Math.max(day, 1);
        } else {
            // DB: [Mut]
            final long days = day - lastSnpGeneration;
            if (days < 0) {
                throw new BroadwickException("Day of the simulation should never be less than the last SNP generation.");
            } else if (days == 0) {
                numSNPs = 0;
            } else {
                numSNPs = generator.getPoisson(mutationRate * days);
            }
        }

        for (int i = 0; i < numSNPs; i++) {
            snps.add(++lastSnp);
        }
        return snps;
    }

    public ProjectSettings setInitMutationsPerClade(final int initMutClade) {
        this.initMutClade = initMutClade;
        return this;
    }
    
    /**
     * Set seed for the random number generator.
     * 
     * @param seed the seed.
     * @return this object.
     */
    public ProjectSettings setSeed(final int seed) {
        this.seed = seed;
        return this;
    }
    
    /**
     * Set the simulation start date.
     *
     * @param date the start date.
     * @return this object.
     */
    public ProjectSettings setStartDate(final int date) {
        this.startDate = date;
        return this;
    }

    /**
     * Set the simulation end date.
     *
     * @param date the end date.
     * @return this object.
     */
    public ProjectSettings setEndDate(final int date) {
        this.endDate = date;
        return this;
    }

        /**
     * Set the start date of input movements.
     *
     * @param date the start date.
     * @return this object.
     */
    public ProjectSettings setStartDateMovements(final int date) {
        this.startDateMovements = date;
        return this;
    }

    /**
     * Set the end date of input movements.
     *
     * @param date the end date.
     * @return this object.
     */
    public ProjectSettings setEndDateMovements(final int date) {
        this.endDateMovements = date;
        return this;
    }

    /**
     * Set the (tau-leap) step size.
     *
     * @param size the step size (in days)
     * @return this object.
     */
    public ProjectSettings setStepSize(final int size) {
        this.stepSize = size;
        this.initStepSize = true;
        return this;
    }

    /**
     * Set the routine test interval (in years).
     *
     * @param years the test interval
     * @return this object.
     */
    public ProjectSettings setTestIntervalInYears(final double years) {
        this.testIntervalInYears = years;
        return this;
    }

    /**
     * Set the number of herds that are initially under restriction.
     *
     * @param num the number of herds
     * @return this object.
     */
    public ProjectSettings setNumInitialRestrictedHerds(final int num) {
        this.numInitialRestrictedHerds = num;
        return this;
    }

    /**
     * Say whether or not to filter short epidemics.
     *
     * @param filter true if short epidemics are ignored.
     * @return this object.
     */
    public ProjectSettings setFilterShortMovements(final boolean filter) {
        this.filterShortEpidemics = filter;
        return this;
    }

    /**
     * Say whether or not to include a reservoir.
     *
     * @param include true if a reservoir is to be included.
     * @return this object.
     */
    public ProjectSettings setIncludeReservoir(final boolean include) {
        this.includeReservoir = include;
        return this;
    }

    /**
     * Say whether or not the reservoir is active. If active then the SNPs that
     * are transmitted from badger->cow are different for each transmission
     * event, if not active then the same badger is transmitting to cattle.
     *
     * @param active true if the reservoir is active.
     * @return this object.
     */
    public ProjectSettings setActiveReservoir(final boolean active) {
        this.activeReservoir = active;
        return this;
    }

    public ProjectSettings setBadgerDeathRate(final double rate) {
        if (this.initStepSize == false) {
            throw new BroadwickException("Step size needs to be set before badger death rate.");
        }
        this.badgerDeathRate = rate * this.stepSize / 365.0;
        return this;
    }
    
    public ProjectSettings setSigma(final double sigma) {
        if (this.initStepSize == false) {
            throw new BroadwickException("Step size needs to be set before badger death rate.");
        }
        this.sigma = sigma * this.stepSize;
        return this;
    }
    
    /**
     * Say whether or not to stop if a breakdown is detected.
     *
     * @param stop true if the simulation should stop if a breakdown is
     * detected.
     * @return this object.
     */
    public ProjectSettings setStopWithBreakdownDetected(final boolean stop) {
        this.stopWithBreakdownDetected = stop;
        return this;
    }

    // DB: [HS] change herd sizes after move
    /**
     * Say whether the departure and destination farm sizes should change after
     * the move
     *
     * @param flexible true if the herd sizes should change, false if the herd
     * sizes are constant.
     * @return this object
     */
    public ProjectSettings setHerdSizeFlex(final boolean flexible) {
        this.herdSizeFlex = flexible;
        return this;
    }

    /**
     * Say whether the departure and destination reservoir sizes should change after
     * the move
     *
     * @param flexible true if the herd sizes should change, false if the herd
     * sizes are constant.
     * @return this object
     */
    public ProjectSettings setReservoirSizeFlex(final boolean flexible) {
        this.reservoirSizeFlex = flexible;
        return this;
    }
    
    /**
     * Probabilities of seeded animals
     * 
     * @param initialInfectionStates A string defining initial infection states of seedes animals
     * @return this object
     */
    public ProjectSettings setInitialInfectionStates(final String initialInfectionStates) {
        this.initialInfectionStates = initialInfectionStates;
        return this;
    }
    
    /**
     * Initialise infected badgers in locations of the seeded infected cows?
     * 
     * @param badgersFromCows true if for each seeded cow there should be an infected badger
     * initialized in a reservoir directly connected to the farm the cow is on
     * @return this object
     */
    public ProjectSettings setInitBadgersFromCows(final boolean badgersFromCows) {
        this.initBadgersFromCows = badgersFromCows;
        return this;
    }
    
//    /**
//     * Initialise infected cows in locations of the seeded infected badgers?
//     * 
//     * @param cowsFromBadgers true if for each seeded badger there should be an infected cow
//     * initialized on a farm directly connected to the reservoir the badger is on
//     * @return this object
//     */
//    public ProjectSettings setInitCowsFromBadgers(final boolean cowsFromBadgers) {
//        this.initCowsFromBadgers = cowsFromBadgers;
//        return this;
//    }
    
    /**
     * Set the observed pairwise distance distribution.
     *
     * @param dist the distribution.
     * @return this object.
     */
    public ProjectSettings setObservedPairwiseDistanceDistribution(final IntegerDistribution dist) {
        this.observedPairwiseDistanceDistribution = dist;
        return this;
    }

    /**
     * Set the distribution of herd sizes.
     *
     * @param dist the distribution.
     * @return this object.
     */
    public ProjectSettings setHerdSizeDistribution(final IntegerDistribution dist) {
        this.herdSizeDistribution = dist;
        return this;
    }

    /**
     * Set the distribution of sett sizes.
     *
     * @param dist the distribution.
     * @return this object.
     */
    public ProjectSettings setReservoirSizeDistribution(final IntegerDistribution dist) {
        this.reservoirSizeDistribution = dist;
        return this;
    }

    /**
     * Set the distribution of cattle deaths.
     *
     * @param dist the distribution.
     * @return this object.
     */
    public ProjectSettings setCattleDeathDistribution(final Map<Integer, Collection<String>> dist) {
        this.cattleDeathDistribution = dist;
        return this;
    }

    /**
     * Set the cattle sampling rate.
     *
     * @param rate the sampling rate.
     * @return this object.
     */
    public ProjectSettings setCattleSamplingRate(final Map<Integer, Double> rate) {
        this.cattleSamplingRate = rate;
        return this;
    }

    /**
     * Set the cattle sampling rate.
     *
     * @param rate the sampling rate.
     * @return this object.
     */
    public ProjectSettings setBadgerSamplingRate(final Map<Integer, Double> rate) {
        this.badgerSamplingRate = rate;
        return this;
    }

    /**
     * Set the details of all the reservoirs in this simulation. The map is a
     * cache of reservoir id to reservoir object.
     *
     * @param reservoirs the reservoirs data.
     * @return this object.
     */
//    public ProjectSettings setReservoirData(final Map<String, Reservoir> reservoirs) {
//        this.reservoirData = reservoirs;
//        return this;
//    }

    /**
     * Set the details of all the reservoirs in this simulation. The map is a
     * cache of farm id and a collection of reservoirs to which it is attached.
     *
     * @param reservoirs the reservoirs data.
     * @return this object.
     */
    public ProjectSettings setFarmReservoirs(final Map<String, Collection<Reservoir>> reservoirs) {
        this.farmReservoirs = reservoirs;
        return this;
    }

    /**
     * Set maximum size of btb outbreak in cattle.
     *
     * @param size the maximum allowed outbreak size.
     * @return this object.
     */
    public ProjectSettings setMaxInfectedCows(final int size) {
        this.maxInfectedCows = size;
        return this;
    }

        /**
     * Set maximum size of btb outbreak in cattle.
     *
     * @param size the maximum allowed outbreak size.
     * @return this object.
     */
    public ProjectSettings setMaxInfectedBadgers(final int size) {
        this.maxInfectedBadgers = size;
        return this;
    }

    @Getter
    private int seed;
    private static int lastSnp = 0;
    @Getter
    private int startDate;
    @Getter
    private int endDate;
    @Getter
    private int startDateMovements;
    @Getter
    private int endDateMovements;
    @Getter
    private int stepSize;
    private boolean initStepSize;
    @Getter
    private double testIntervalInYears;
    @Getter
    private int numInitialRestrictedHerds;
    @Getter
    private boolean stopWithBreakdownDetected;
    @Getter
    private boolean filterShortEpidemics;
    @Getter
    private boolean includeReservoir;
    // badger death rate per step size
    @Getter
    private double badgerDeathRate;
    @Getter
    private boolean activeReservoir;
    @Getter
    private String initialInfectionStates;
    @Getter
    private int initMutClade;
    @Getter
    private boolean initBadgersFromCows;
    @Getter
    private double sigma;
    @Getter
    private boolean herdSizeFlex;
    @Getter
    private boolean reservoirSizeFlex;
    @Getter
    private IntegerDistribution observedPairwiseDistanceDistribution;
    @Getter
    private IntegerDistribution herdSizeDistribution;
    @Getter
    private IntegerDistribution reservoirSizeDistribution;
    @Getter
    private Map<Integer, Collection<String>> cattleDeathDistribution;
    @Getter
    private Map<Integer, Double> cattleSamplingRate;
    @Getter
    private Map<Integer, Double> badgerSamplingRate;
    @Getter
    Map<String, Collection<Reservoir>> farmReservoirs;
    @Getter
    private int maxInfectedCows;
    @Getter
    private int maxInfectedBadgers;
    // A list of all the movements stored in a farmId-farmId format. We will stored many duplicates so to pick a random 
    // movement we can select a movement at random from this list and it will respect the distribution of actual movements.
    @Getter
    private final List<Pair<String, String>> cattleMovementFrequencies = new ArrayList<>();
    @Getter
    private final List<Pair<String, String>> badgerMovementFrequencies = new ArrayList<>();
}
