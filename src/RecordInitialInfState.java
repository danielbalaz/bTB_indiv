package btbcluster;
import java.io.Serializable;
import lombok.Data;

@Data
public class RecordInitialInfState implements Serializable {
    public RecordInitialInfState(final String species, final String animalId, final String unitId, final String state) {
        this.species = species;
        this.animalId = animalId;
        this.unitId = unitId;
        this.state = state;
    }
    
    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(species).append(",");
        sb.append(animalId).append(",");
        sb.append(unitId).append(",");
        sb.append(state);
        return sb.toString();
    }
    
    public static final String Header() {
        return "species,animalId,unitID,state\n";
    }

    
    private final String species;
    private final String animalId;
    private final String unitId;
    private final String state;
}

