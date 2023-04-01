package org.grozeille.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.grozeille.model.Team;

import java.util.LinkedList;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamRoomDispatchScenario {
    private int roomSize;

    private String roomName;

    private int score;

    private LinkedList<Team> teams;

    public Integer getTotalSize() {
        if(teams == null) {
            return 0;
        }
        return teams.stream().mapToInt(Team::size).sum();
    }
}
