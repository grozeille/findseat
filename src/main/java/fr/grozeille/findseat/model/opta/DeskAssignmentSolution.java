package fr.grozeille.findseat.model.opta;

import lombok.Data;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.ArrayList;
import java.util.List;

@PlanningSolution
@Data
public class DeskAssignmentSolution {

    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<People> people = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<DeskAssignment> deskAssignments = new ArrayList<>();

    @PlanningScore
    private HardMediumSoftScore score;

    public DeskAssignmentSolution() {
    }

    public DeskAssignmentSolution(List<People> people, List<DeskAssignment> deskAssignments) {
        this.people = people;
        this.deskAssignments = deskAssignments;
    }
}
