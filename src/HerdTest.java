package btbcluster;

import broadwick.BroadwickConstants;
//import java.io.Serializable;
import lombok.Data;
import org.joda.time.DateTime;

/**
 * Utility class for herd test data.
 */
@Data
@SuppressWarnings("all")
public class HerdTest {
    
    public HerdTest(final int date, final String unitId, final int infected, final int reactors, final String event) {
        this.date = BroadwickConstants.toDate(date);
        this.unitId = unitId;
        this.infected = infected;
        this.reactors = reactors;
        this.event = event;
    }
    
    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(date.toString(DATE_FORMAT)).append(",");
        sb.append(unitId).append(",");
        sb.append(infected).append(",");
        sb.append(reactors).append(",");
        sb.append(event);
        return sb.toString();
    }
    
    public static final String Header() {
        return "date,unit_ID,infected,reactors,reasonOfTesting\n";
    }

    private final DateTime date;
    private final String unitId;
    private final int infected;
    private final int reactors;
    private final String event;
    private static final String DATE_FORMAT = "yyyy-MM-dd";
}
