package btbcluster;

import broadwick.BroadwickConstants;
import java.io.Serializable;
import lombok.Data;
import org.joda.time.DateTime;

/**
 * Utility class for skin test data.
 */
@Data
@SuppressWarnings("all")
public class CattleTest implements Serializable, Comparable<CattleTest> {

    /**
     * Create the test object.
     * @param date           the date of the test (number of days from BroadwickConstants.ZERO_DATE).
     * @param unitId         the id of the unit in which the animal tested resides.
     * @param animalId       the id of the tested animal.
     * @param positiveResult true if the animal tested positive, false otherwise.
     * @param infectionState infectionState of the animal
     * @param event          Why was the test performed? pre-move, abattoir, WHT
     */
    public CattleTest(final int date, final String unitId, final String animalId, final boolean positiveResult, final InfectionStateCow infectionState, final String event) {
        this.date = BroadwickConstants.toDate(date);
        this.unitId = unitId;
        this.animalId = animalId;
        this.positive = positiveResult;
        this.infectionState = infectionState;
        this.event = event;
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(date.toString(DATE_FORMAT)).append(",");
        sb.append(unitId).append(",");
        sb.append(animalId).append(",");
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
    public final int compareTo(CattleTest that) {
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
        return "date,unit_ID,animal_ID,testResult,infectionState,reasonOfTesting\n";
    }

    private final DateTime date;
    private final String unitId;
    private final String animalId;
    private final boolean positive;
    private final InfectionStateCow infectionState;
    private final String event;
    private static final String DATE_FORMAT = "yyyy-MM-dd";
}