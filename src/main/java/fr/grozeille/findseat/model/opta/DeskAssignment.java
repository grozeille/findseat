package fr.grozeille.findseat.model.opta;

import lombok.Data;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

@PlanningEntity
@Data
public class DeskAssignment implements Cloneable {

    @PlanningId
    private Long id;

    @PlanningVariable
    private People people;

    private String row;

    private String number;

    private String deskGroup;

    private Boolean withMonitoringScreens;

    private Boolean doesNotExists;

    public String toDeskNumber() {
        return deskGroup + row + number;
    }

    public DeskAssignment() {

    }

    public DeskAssignment(Long id, String deskGroup, String row, String number, Boolean withMonitoringScreens, Boolean doesNotExists) {
        this.id = id;
        this.deskGroup = deskGroup;
        this.row = row;
        this.number = number;
        this.withMonitoringScreens = withMonitoringScreens;
        this.doesNotExists = doesNotExists;
    }

    @Override
    public DeskAssignment clone() {
        try {
            DeskAssignment clone = (DeskAssignment) super.clone();
            clone.setId(this.id);
            clone.setDeskGroup(this.getDeskGroup());
            clone.setRow(this.getRow());
            clone.setNumber(this.getNumber());
            clone.setDoesNotExists(this.getDoesNotExists());
            clone.setWithMonitoringScreens(this.getWithMonitoringScreens());
            clone.setPeople(this.getPeople());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
