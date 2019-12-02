package btbcluster;

import broadwick.stochastic.SimulationState;
import broadwick.utils.CloneUtils;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A class to encapsulate the properties of an infected cow.
 */
@Slf4j
public class InfectedCow implements SimulationState {

    // NOTE: Are farmId, snps, dataSampleTaken used anywhere?
    // They could be in MyAmountManager.performEvent()
    // but these values are taken from source of infection,
    // which is important especially as the SNPs get updated.
    // Id and InfectionStatus are used!
    
    /**
     * Create a cow with a set of snps.
     * @param id              the id of the infected cow.
     * @param farmId          the id of the farm where the cow is found
     * @param snps            the snps that should be associated with the cow.
     * @param day             the day the cow is added (this will be used to calculated new mutations)
     * @param infectionStatus the initial infection status of the cow.
     */
    public InfectedCow(final String id, final String farmId, final Set<Integer> snps, final int day, final InfectionStateCow infectionStatus) {
        this.id = id;
        this.farmId = farmId;
        this.snps = (Set<Integer>) CloneUtils.deepClone(snps);
        this.lastSnpGeneration = day;
        this.infectionStatus = infectionStatus;
        this.dateSampleTaken = -1;
        this.allFarmIds = new ArrayList<>();
        allFarmIds.add(farmId);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        return ((InfectedCow) obj).getId().equals(id);
    }

    @Override
    public final String getStateName() {
        return this.toString();
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder(10);
        if (id.isEmpty()) {
            sb.append("COW");
        } else {
            sb.append(id).append("[").append(farmId).append("]").append(":").append(infectionStatus);
        }

        return sb.toString();
    }

    @Override
    protected void finalize() {
        try {
            snps.clear();
            snps = null;
            super.finalize();
        } catch (Throwable t) {
            log.error("Could not free Agent's memory. {}", t.getLocalizedMessage());
        }
    }

    public String record() {
        final StringBuilder sb = new StringBuilder();
        sb.append(id);
        sb.append(",").append(Joiner.on(";").join(allFarmIds));
        sb.append(",").append(infectionStatus);
        sb.append(",").append(dateSampleTaken);
        sb.append(",").append(lastSnpGeneration);
        sb.append(",").append(Joiner.on(";").join(snps));
                  
        return sb.toString();
    }
    
    public static final String Header() {
        return "animal_ID,unit_ID,InfectionStatus,DateSampleTaken,LastSnpGeneration,SNPs\n";
    }
    
    @Getter
    private final String id;
    @Getter
    @SuppressWarnings("PMD.UnusedPrivateField")
    private Set<Integer> snps;
    @Getter
    @Setter
    @SuppressWarnings("PMD.UnusedPrivateField")
    private int lastSnpGeneration;
    @Getter
    @Setter
    @SuppressWarnings("PMD.UnusedPrivateField")
    private int dateSampleTaken;
    @Getter
    @Setter
    private InfectionStateCow infectionStatus;
    @Getter
    @Setter
    private String farmId; // the id of the farm where the cow is found - needs to be updated when moved
    // DB: [RememberFarmIDs]
    @Getter
    @Setter
    private ArrayList<String> allFarmIds;
}
