package btbcluster;

import broadwick.config.generated.Prior;
import broadwick.config.generated.UniformPrior;
import broadwick.montecarlo.MonteCarloStep;
import broadwick.montecarlo.markovchain.MarkovStepGenerator;
import broadwick.rng.RNG;
import broadwick.statistics.distributions.TruncatedNormalDistribution;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Generates a [Markov] path through parameter space of the BtbIbmClusterDynamics.
 */
public class MonteCarloSrwmPathGenerator implements MarkovStepGenerator {

    /**
     * Create a path generator object that proposes a new step according to a given set of priors.
     * @param priors        the priors to be applied in proposing a new step.
     * @param percentageDev the percentage deviation from the current step.
     * @param rng           the instance of the random number generator to use.
     */
    public MonteCarloSrwmPathGenerator(final Collection<Prior> priors, final double percentageDev, final RNG rng) {
        final Map<String, Double> initialVals = new LinkedHashMap<>();
        for (Prior prior : priors) {
            //TODO - this only works for uniform priors - need to find a neat way of making this 
            // list of priors a list of actual distribution objects e.g. Uniform, TruncatedNormalDistribution
            UniformPrior uniformPrior = (UniformPrior) prior;
            this.priors.put(uniformPrior.getId(), uniformPrior);
            initialVals.put(uniformPrior.getId(), uniformPrior.getInitialVal());

            if (uniformPrior.getInitialVal() > uniformPrior.getMax() || uniformPrior.getInitialVal() < uniformPrior.getMin()) {
                throw new IllegalArgumentException(String.format("Invalid prior [%s]. Initial value [%f] not in range [%f,%f]",
                                                                 uniformPrior.getId(), uniformPrior.getInitialVal(), uniformPrior.getMin(), uniformPrior.getMax()));
            }
        }
        this.initialStep = new MonteCarloStep(initialVals);
        this.sDev = percentageDev;
        this.generator = rng;
    }

    @Override
    public MonteCarloStep generateNextStep(MonteCarloStep step) {

        final Map<String, Double> proposedStep = new LinkedHashMap<>(step.getCoordinates().size());
        for (Map.Entry<String, Double> entry : step.getCoordinates().entrySet()) {

            final UniformPrior prior = (UniformPrior) (priors.get(entry.getKey()));

            double proposedVal = new TruncatedNormalDistribution(entry.getValue(), sDev, prior.getMin(), prior.getMax()).sample();

            proposedStep.put(entry.getKey(), Math.round(proposedVal * precision) / precision);
        }

        return new MonteCarloStep(proposedStep);
    }

    private final Map<String, UniformPrior> priors = new HashMap<>();
    @Getter
    private final MonteCarloStep initialStep;
    private final double sDev;
    private final RNG generator;
    private final double precision = 10000.0;
}
