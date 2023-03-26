package org.grozeille.model;

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
        return dispatched.stream().map(TeamRoomDispatchScenario::getScore).reduce(0, Integer::sum);
    }
}
