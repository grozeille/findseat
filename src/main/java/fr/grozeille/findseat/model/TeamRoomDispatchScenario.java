package fr.grozeille.findseat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedList;

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
