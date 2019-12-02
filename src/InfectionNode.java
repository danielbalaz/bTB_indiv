package btbcluster;

import broadwick.graph.Vertex;
import broadwick.utils.CloneUtils;
import com.google.common.base.Joiner;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Simple class to store the details of an infected individual.
 */
class InfectionNode extends Vertex {

    /**
     * Create a node for inclusion in a phylogenetic tree, including the infection date and the collection of SNPs.
     * @param id            the id of the node.
     * @param snps          the collection of SNPs that this animal has.
     * @param infectionDate the date (number of days from ZERO_DATE) the animal was infected
     * @param detectionDate the date (number of days from ZERO_DATE) the animal was detected (sampled)
     * @param isACow        true if the node refers to a cow, false for a badger
     */
    public InfectionNode(final String id, final String locationId, final Set<Integer> snps,
                         final Integer infectionDate, final Integer detectionDate, final boolean isACow) {
        super(id);
        this.snp = CloneUtils.deepClone(snps);
        this.locationId = locationId;
        this.infectionDate = infectionDate;
        this.detectionDate = detectionDate;
        this.cow = isACow;
    }
    
    public InfectionNode(final InfectionNode node) {
        this (node.id, node.locationId, node.snp, node.infectionDate, node.detectionDate, node.cow);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        snp.clear();
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

        return ((InfectionNode) obj).getId().equals(id);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(", snps=").append(snp);
        sb.append(", infectionDate=").append(infectionDate);
        sb.append(", detectionDate=").append(detectionDate);
        
        return sb.toString();
    }

    public String record() {
        final StringBuilder sb = new StringBuilder();
        
        sb.append(id);
        sb.append(",").append(isCow());
        sb.append(",").append(infectionDate);
        sb.append(",").append(detectionDate);
        sb.append(",").append(Joiner.on(";").join(snp));
        
        return sb.toString();
    }
    
    public static final String Header() {
        return "animal_ID,isCow,InfectionDate,DetectionDate,SNPs\n";
    }
    
    @Getter
    private final boolean cow;
    @Getter
    @SuppressWarnings("PMD.UnusedPrivateField")
    private final Set<Integer> snp;
    @Getter
    @Setter
    @SuppressWarnings("PMD.UnusedPrivateField")
    private String locationId;
    @Getter
    @Setter
    @SuppressWarnings("PMD.UnusedPrivateField")
    private Integer infectionDate;
    @Getter
    @Setter
    @SuppressWarnings("PMD.UnusedPrivateField")
    private Integer detectionDate;
}
