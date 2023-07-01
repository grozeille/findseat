package fr.grozeille.findseat.model.opta;

import lombok.Data;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

@PlanningEntity
@Data
public class DeskAssignment {

    @PlanningId
    private Long id;

    @PlanningVariable
    private People people;

    private String row;

    private Integer number;

    private String deskGroup;

    private Boolean withMonitoringScreens;

    private Boolean doesNotExists;

    public String toDeskNumber() {
        return deskGroup + row + String.format("%03d", number);
    }

    public DeskAssignment() {

    }

    public DeskAssignment(Long id, String deskGroup, String row, Integer number, Boolean withMonitoringScreens, Boolean doesNotExists) {
        this.id = id;
        this.deskGroup = deskGroup;
        this.row = row;
        this.number = number;
        this.withMonitoringScreens = withMonitoringScreens;
        this.doesNotExists = doesNotExists;
    }
}
