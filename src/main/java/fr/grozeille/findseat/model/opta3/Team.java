package fr.grozeille.findseat.model.opta3;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@PlanningEntity
public class Team {
    @PlanningId
    private Long id;

    private String name;

    private Integer wantMonitoringScreen;

    private Boolean isMandatory;

    private Integer size;

    @PlanningVariable(nullable = true)
    private Desk desk;

    public Team(Long id, String name, Integer wantMonitoringScreen, Boolean isMandatory, Integer size) {
        this.id = id;
        this.name = name;
        this.wantMonitoringScreen = wantMonitoringScreen;
        this.isMandatory = isMandatory;
        this.size = size;
    }
}
