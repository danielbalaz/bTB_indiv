package btbcluster;
import java.io.Serializable;
import lombok.Data;

@Data
public class RecordInitialSize implements Serializable {
    public RecordInitialSize(final String species, final String unitId, final int size) {
        this.species = species;
        this.unitId = unitId;
        this.size = size;
    }
    
    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(species).append(",");
        sb.append(unitId).append(",");
        sb.append(size);
        return sb.toString();
    }
    
    public static final String Header() {
        return "species,unitID,size\n";
    }

    
    private final String species;
    private final String unitId;
    private final int size;
}
