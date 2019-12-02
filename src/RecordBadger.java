package btbcluster;

import broadwick.BroadwickConstants;
import java.io.Serializable;
import lombok.Data;
import org.joda.time.DateTime;


/**
 * Utility class for imaginary badger detection.
 */
@Data
@SuppressWarnings("all")
public class RecordBadger implements Serializable, Comparable<RecordBadger> {

    /**
     * Create the test object.
     * @param date           the date of the test (number of days from BroadwickConstants.ZERO_DATE).
     * @param unitIdDepart   the id of the unit in which the animal tested resided before the move. If tested for reason other than move, it will be the same as unitIdDest.
     * @param unitIdDest     the id of the unit in which the animal tested resides after the move. If tested for reason other than move, it will be the same as unitIdDepart.
     * @param animalId       the id of the tested animal.
     * @param capture        true if the animal was captured.
     * @param positiveResult true if the animal tested positive, false otherwise.
     * @param infectionState infectionState of the animal
     * @param event          Why was the test performed? move, death, capture
     */
    public RecordBadger(final int date, final String animalId, final String unitIdDepart, final String unitIdDest, final boolean capture, final boolean positiveResult, final InfectionStateBadger infectionState, final String event) {
        this.date = BroadwickConstants.toDate(date);
        this.animalId = animalId;
        this.departureUnitId = unitIdDepart;
        this.destinationUnitId = unitIdDest;
        this.capture = capture;
        this.positive = positiveResult;
        this.infectionState = infectionState;
        this.event = event;
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(date.toString(DATE_FORMAT)).append(",");
        sb.append(animalId).append(",");
        sb.append(departureUnitId).append(",");
        sb.append(destinationUnitId).append(",");
        if (capture) {
            sb.append("TRUE");
        } else {
            sb.append("FALSE");
        }
        sb.append(",");
        if (positive) {
            sb.append("TRUE");
        } else {
            sb.append("FALSE");
        }
        sb.append(",").append(infectionState);
        sb.append(",").append(event);
        return sb.toString();
    }

    @Override
    public final int compareTo(RecordBadger that) {
        final int before = -1;
        final int equal = 0;
        final int after = 1;

        //this optimization is usually worthwhile, and can
        //always be added
        if (this == that) {
            return equal;
        }

        //booleans follow this form
        if (!this.positive && that.positive) {
            return before;
        }
        if (this.positive && !that.positive) {
            return after;
        }

        //objects, including type-safe enums, follow this form
        //note that null objects will throw an exception here
        int comparison = this.animalId.compareTo(that.animalId);
        if (comparison != equal) {
            return comparison;
        }

        comparison = this.date.compareTo(that.date);
        if (comparison != equal) {
            return comparison;
        }

        //all comparisons have yielded equality
        //verify that compareTo is consistent with equals (optional)
        assert this.equals(that) : "compareTo inconsistent with equals.";

        return equal;
    }
    
    public static final String Header() {
        return "date,animal_ID,unit_ID_from,unit_ID_to,capture,testResult,infectionState,reasonOfTesting\n";
    }

    private final DateTime date;
    private final String animalId;
    private final String departureUnitId;
    private final String destinationUnitId;
    private final boolean capture;
    private final boolean positive;
    private final InfectionStateBadger infectionState;
    private final String event;
    private static final String DATE_FORMAT = "yyyy-MM-dd";
}
