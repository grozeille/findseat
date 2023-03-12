package org.grozeille;

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
        return dispatched.stream().map(t -> t.getScore()).reduce(0, Integer::sum);
    }
}
