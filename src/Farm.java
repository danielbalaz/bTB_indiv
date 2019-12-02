package btbcluster;

import lombok.extern.slf4j.Slf4j;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import broadwick.statistics.distributions.IntegerDistribution;
import java.io.Serializable;
import lombok.Setter;

/**
 *
 */
@Slf4j
@EqualsAndHashCode
public class Farm implements Serializable {

    public Farm(final String id) {
        this(id, -1, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    public Farm(final String id, final int size, final double xLocation, final double yLocation) {
        this.id = id;
        this.herdSize = size;
//        this.xLocation = xLocation;
//        this.yLocation = yLocation;
//        this.movementFrequencies = new HashMap<>();
        this.offMovementDistribution = new IntegerDistribution();
        this.lastClearTestDate = -1;
        this.lastPositiveTestDate = -1;
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        offMovementDistribution.clear();
    }
    
    public void changeSize(final int change) {
        this.herdSize += change;
    }

    @Getter
    private final String id;
    @Getter
    @Setter
    private int herdSize;
    @Getter
    @Setter
    private int lastClearTestDate;
    @Getter
    @Setter
    private int lastPositiveTestDate;
//    @Getter
//    private final double xLocation;
//    @Getter
//    private final double yLocation;
//    @Getter
//    private final Map<String, Collection<Integer>> movementFrequencies;
    @Getter
    private final IntegerDistribution offMovementDistribution;
}
