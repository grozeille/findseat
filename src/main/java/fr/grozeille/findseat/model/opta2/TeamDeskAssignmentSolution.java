package fr.grozeille.findseat.model.opta2;

import lombok.Data;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

import java.util.ArrayList;
import java.util.List;

@PlanningSolution
@Data
public class TeamDeskAssignmentSolution {

    @PlanningScore
    private HardMediumSoftScore score;

    @PlanningEntityCollectionProperty
    private List<Team> teams = new ArrayList<>();

    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<Desk> desks = new ArrayList<>();

    public TeamDeskAssignmentSolution() {}

    public TeamDeskAssignmentSolution(List<Team> teams, List<Desk> desks) {
        this.teams = teams;
        this.desks = desks;
    }
}
