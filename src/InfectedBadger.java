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
 * A class to encapsulate the properties of an infected badger.
 */
@Slf4j
public class InfectedBadger implements SimulationState {

    /**
     * Create a badger with a set of snps.
     * @param id          the id of the infected badger.
     * @param reservoirId the id of the reservoir where the badger can be found.
     * @param snps        the snps that should be associated with the badger.
     * @param day         the day the badger is added (this will be used to calculated new mutations)
     */
    public InfectedBadger(final String id, final String reservoirId, final Set<Integer> snps, final int day) {
        this.id = id;
        this.reservoirId = reservoirId;
        this.snps = (Set<Integer>) CloneUtils.deepClone(snps);
        this.lastSnpGeneration = day;
        this.dateSampleTaken = -1;
        // DB: [RememberFarmIDs]
        this.allReservoirIds = new ArrayList<>();
        allReservoirIds.add(reservoirId);

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

        return ((InfectedBadger) obj).getId().equals(id);
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

    @Override
    public String getStateName() {
        return this.toString();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(10);
        if (id.isEmpty()) {
            sb.append("BADGER");
        } else {
            sb.append(id).append("[").append(reservoirId).append("]").append(":").append(InfectionStateCow.INFECTIOUS);
        }
        return sb.toString();
    }

    public String record() {
        final StringBuilder sb = new StringBuilder();
        sb.append(id);
        sb.append(",").append(Joiner.on(";").join(allReservoirIds));
        sb.append(",").append("INFECTIOUS"); // TODO: update when necessary, i.e. other compartments of badger present, not only Susceptible or Infected
        sb.append(",").append(dateSampleTaken);     // TODO: update when we know when badgers get sampled
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
    private String reservoirId; // the reservoir where the badger can be found.
    // DB: [RememberFarmIDs]
    @Getter
    @Setter
    private ArrayList<String> allReservoirIds;
}
