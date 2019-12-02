package btbcluster;
import broadwick.BroadwickConstants;
import lombok.Data;
import org.joda.time.DateTime;

@Data
public class RecordMovement {
    public RecordMovement(final int date, final String species, final String unitID_from, final String unitID_to,
                          final int unit_size, final int anim_move, final int inf_anim, final int inf_move,
                          final int rnd_choice, final double rnd_num) {
        this.date = BroadwickConstants.toDate(date);
        this.species = species;
        this.unitID_from = unitID_from;
        this.unitID_to = unitID_to;
        if (unit_size < 0) {
            this.unit_size = "";
        } else {
            this.unit_size = String.valueOf(unit_size);
        }
        if (anim_move < 0) {
            this.anim_move = "";
        } else {
            this.anim_move = String.valueOf(anim_move);
        }
        if (inf_anim < 0) {
            this.inf_anim = "";
        } else {
            this.inf_anim = String.valueOf(inf_anim);
        }
        if (inf_move < 0) {
            this.inf_move = "";
        } else {
            this.inf_move = String.valueOf(inf_move);
        }
        if (rnd_choice < 0) {
            this.rnd_choice = "";
        } else {
            this.rnd_choice = String.valueOf(rnd_choice);
        }
        if (rnd_num < 0) {
            this.rnd_num = "";
        } else {
            this.rnd_num = String.valueOf(rnd_num);
        }
    }
    
    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(date.toString(DATE_FORMAT)).append(",");
        sb.append(species).append(",");
        sb.append(unitID_from).append(",");
        sb.append(unitID_to).append(",");
        sb.append(unit_size).append(",");
        sb.append(anim_move).append(",");
        sb.append(inf_anim).append(",");
        sb.append(inf_move).append(",");
        sb.append(rnd_choice).append(",");
        sb.append(rnd_num);
        return sb.toString();
    }
    
    public static final String Header() {
        return "date,species,unitID_from,unitID_to,unit_size,anim_move,inf_anim,inf_move,rnd_choice,rnd_num\n";
    }
    
    final DateTime date;
    final String species;
    final String unitID_from;
    final String unitID_to;
    final String unit_size;
    final String anim_move;
    final String inf_anim;
    final String inf_move;
    final String rnd_choice;
    final String rnd_num;
    private static final String DATE_FORMAT = "yyyy-MM-dd";
}
