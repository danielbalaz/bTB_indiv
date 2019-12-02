package btbcluster;

import broadwick.statistics.distributions.IntegerDistribution;
import java.util.ArrayList;
import java.util.Collection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Encompass a wildlife reservoir for bovine TB. The reservoir contains infected badgers.
 */
@Slf4j
public class Reservoir {

    public Reservoir(final String id) {
        this(id, -1);
    }
    
    public Reservoir(final String id, final int size) {
        this.id = id;
        this.reservoirSize = size;
        this.connectedFarms = new ArrayList<>();
        this.offMovementDistribution = new IntegerDistribution();
    }

//    /**
//     * Create and add a new infected badger to the reservoir.
//     * @param snps the snps that should be associated with the badger.
//     * @param day  the day the badger is added (this will be used to calculated new mutations)
//     * @return the new badger.
//     */
//    public Badger addInfectedBadger(final Set<Integer> snps, final Integer day) {
//        Badger badger = new Badger(snps, day);
//        infectedBadgers.add(badger);
//        return badger;
//    }
//
//    /**
//     * Select a badger at random from the reservoir.
//     * @return a randomly selected badger.
//     */
//    public Badger selectBadger() {
//        return (Badger) generator.selectOneOf(infectedBadgers);
//    }
    
    @Override
    public void finalize() throws Throwable {
        super.finalize();
        connectedFarms.clear();
        connectedFarms = null;
    }
    
    public void changeSize(final int change) {
        this.reservoirSize += change;
    }

    @Override
    public String toString() {
        return String.format("%s", id);
    }

    @Getter
    private final String id;
    @Getter
    @Setter
    private int reservoirSize;
    @Getter
    private Collection<String> connectedFarms;
    @Getter
    private final IntegerDistribution offMovementDistribution;
}
