package fr.grozeille.findseat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamDispatchScenario {

    private List<TeamRoomDispatchScenario> dispatched;

    private List<Team> notAbleToDispatch;

    public int totalScore() {
        return dispatched.stream().mapToInt(TeamRoomDispatchScenario::getScore).sum();
    }
}
