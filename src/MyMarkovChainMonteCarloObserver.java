package btbcluster;

import broadwick.BroadwickVersion;
import broadwick.config.generated.Parameter;
import broadwick.config.generated.Prior;
import broadwick.config.generated.UniformPrior;
import broadwick.io.FileOutput;
import broadwick.montecarlo.markovchain.observer.MarkovChainObserver;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Observer for the Markov Chain Monte Carlo that records the data at each step.
 */
@Slf4j
@EqualsAndHashCode(callSuper=false)
public class MyMarkovChainMonteCarloObserver extends MarkovChainObserver {

    MyMarkovChainMonteCarloObserver(final WPBtbClusterModel model) {
        super();
        this.model = model;
        mcmcOutputFile = new FileOutput(model.getParameterValue("markovChainParameterFile"), false, false);
    }

    @Override
    public void started() {
        mcmcOutputFile.write("# Broadwick version %s\n", BroadwickVersion.getVersionAndTimeStamp());
        mcmcOutputFile.write("#\n# Parameters:\n");

        for (Parameter parameter : model.getParameters()) {
            mcmcOutputFile.write(String.format("# %s = %s\n", parameter.getId(), parameter.getValue()));
        }
        mcmcOutputFile.write("#\n# Priors:\n");
        for (Prior prior : model.getPriors()) {
            UniformPrior pr = (UniformPrior) prior;
            mcmcOutputFile.write("# %s : uniform [%s-%s]\n", pr.getId(), pr.getMin(), pr.getMax());
        }
        mcmcOutputFile.write("#\n#\n# Columns:\n");
        mcmcOutputFile.write(this.monteCarlo.getHeader());
        mcmcOutputFile.flush();
    }

    @Override
    public void step() {
        boolean accepted = this.monteCarlo.isLastStepAccepted();
        mcmcOutputFile.write("%d,%d,%s,%s\n", this.monteCarlo.getNumStepsTaken(), accepted ? 1 : 0, 
                                                                                  this.monteCarlo.getProposedStep().toString(), 
                                                                                  this.monteCarlo.getConsumer().toCsv());
        
        mcmcOutputFile.flush();
    }

    @Override
    public void takeMeasurements() {
    }

    @Override
    public void finished() {
        mcmcOutputFile.close();
    }

    private final WPBtbClusterModel model;
    private final FileOutput mcmcOutputFile;
}
