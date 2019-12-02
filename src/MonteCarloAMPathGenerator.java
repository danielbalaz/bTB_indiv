package btbcluster;

import broadwick.config.generated.Prior;
import broadwick.config.generated.UniformPrior;
import broadwick.math.Matrix;
import broadwick.math.Vector;
import broadwick.montecarlo.MonteCarloStep;
import broadwick.montecarlo.markovchain.MarkovStepGenerator;
import broadwick.rng.RNG;
import broadwick.statistics.distributions.TruncatedMultivariateNormalDistribution;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates a [Markov] path through parameter space of the BtbIbmClusterDynamics.
 */
@Slf4j
public class MonteCarloAMPathGenerator implements MarkovStepGenerator {

    /**
     * Create a path generator object that proposes a new step according to a given set of priors.
     * @param priors        the priors to be applied in proposing a new step.
     * @param percentageDev the percentage deviation from the current step.
     * @param rng           the instance of the random number generator to use.
     */
    public MonteCarloAMPathGenerator(final Collection<Prior> priors, final double percentageDev, final RNG rng) {

        this.sDev = percentageDev;
        this.generator = rng;

        final int n = priors.size();

        // initialise the mean and covariance matrix that we will update during the simulation.
        // We want the vector elements to be consistent so we iterate over the priors.
        this.means = new Vector(n);
        this.lower = new Vector(n);
        this.upper = new Vector(n);
        this.covariances = new Matrix(n, n);

        final Map<String, Double> initialVals = new LinkedHashMap<>();
        int i = 0;
        for (Prior prior : priors) {
            //TODO - this only works for uniform priors - need to find a neat way of making this 
            // list of priors a list of actual distribution objects e.g. Uniform, TruncatedNormalDistribution
            UniformPrior uniformPrior = (UniformPrior) prior;

            if (uniformPrior.getInitialVal() > uniformPrior.getMax() || uniformPrior.getInitialVal() < uniformPrior.getMin()) {
                throw new IllegalArgumentException(String.format("Invalid prior [%s]. Initial value [%f] not in range [%f,%f]",
                                                                 uniformPrior.getId(), uniformPrior.getInitialVal(), uniformPrior.getMin(), uniformPrior.getMax()));
            }

            initialVals.put(uniformPrior.getId(), uniformPrior.getInitialVal());
            this.lower.setEntry(i, uniformPrior.getMin());
            this.upper.setEntry(i, uniformPrior.getMax());

            //covariances.setEntry(i, i, 1.0);
            covariances.setEntry(i, i, sDev * uniformPrior.getInitialVal() / 100.0);
            i++;
        }
        this.initialStep = new MonteCarloStep(initialVals);

        log.trace("{}", covariances.toString());
    }

    @Override
    public MonteCarloStep generateNextStep(MonteCarloStep step) {

        // We sample from a mulitvariate normal distribution here, 
        // the means for this distribution are the current values of the step: step.getValues()
        // The covariances are the updated at each step according to the adaptive Metropolis algorithm from 
        // http://dept.stat.lsa.umich.edu/~yvesa/afmp.pdf, continuously updating the covraiances in this way
        // means we don't have to store the whole chain.
        final int n = step.getCoordinates().size();
        final double scale = 2.85 / Math.sqrt(n);
        final double[] x = new double[n];

        // Now update the means and covariances. 
        // Doing it after the proposed step means that the means are ALWAYS updated (even for rejected steps) doing 
        // it here means that only the accepted steps (i.e. the members of the chain) are used but the means are
        // skewed by using the same values repeatedly
        for (int i = 0; i < n; i++) {
            x[i] = Iterables.get(step.getCoordinates().values(), i);
            means.setEntry(i, means.element(i) + (x[i] - means.element(i)) / (stepsTaken + 1));
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double cov = (x[i] - means.element(i)) * (x[j] - means.element(j));
                covariances.setEntry(i, j, (covariances.element(i, j)
                                            + (cov - covariances.element(i, j)) / (stepsTaken + 1)) * scale);
                if (i == j) {
                    // to avoid potentially obtaining a singular matrix
                    covariances.setEntry(i, j, covariances.element(i, j) + 0.001);
                    // the variances that are used when sampling from the multivariate distribution are along the 
                    // diagonal and we want these relatively large to have good mixing.
                    //covariances[i][j] = Math.pow(sDev*means[i]/100.0,2); //1.0;
                }
            }
        }

        TruncatedMultivariateNormalDistribution tmvn = new TruncatedMultivariateNormalDistribution(means, covariances, 
                lower, upper);
        Vector proposal = tmvn.sample();

        // Now package this proposal into a MonteCarloStep
        final Map<String, Double> proposedStep = new LinkedHashMap<>(step.getCoordinates().size());
        int i = 0;
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.#####E0");
        for (Map.Entry<String, Double> entry : step.getCoordinates().entrySet()) {
            // update the proposed step, rounding to 4 places
            proposedStep.put(entry.getKey(), Double.valueOf(df.format(proposal.element(i++))));
        }

        stepsTaken++;
        return new MonteCarloStep(proposedStep);
    }

    private double getNewValue(final double value, final double deviation) {
        return generator.getGaussian(value, deviation);
    }

    @Getter
    private final MonteCarloStep initialStep;
    private int stepsTaken = 0;
    private final Vector means;
    private final Matrix covariances;
    private final Vector lower;
    private final Vector upper;
    private final double sDev;
    private final RNG generator;
    private final double precision = 10000.0;
}
