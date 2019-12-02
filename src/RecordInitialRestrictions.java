package btbcluster;
import broadwick.BroadwickConstants;
import java.io.Serializable;
import lombok.Data;
import org.joda.time.DateTime;

@Data
public class RecordInitialRestrictions implements Serializable {
    public RecordInitialRestrictions(final String unitId, final int cleartest, final int lastTestDate) {
        this.unitId = unitId;
        this.cleartest = cleartest;
        this.lastTestDate = BroadwickConstants.toDate(lastTestDate);
    }
    
    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(unitId).append(",");
        sb.append(cleartest).append(",");
        sb.append(lastTestDate.toString(DATE_FORMAT));
        return sb.toString();
    }
    
    public static final String Header() {
        return "unitID,clear_test,last_test_date\n";
    }
    

    private final String unitId;
    private final int cleartest;
    private final DateTime lastTestDate;
    private static final String DATE_FORMAT = "yyyy-MM-dd";
}
