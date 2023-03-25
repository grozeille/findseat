package org.grozeille;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public Stream<Integer> getSizeList() {
        if(teams == null) return Stream.<Integer>builder().build();
        return teams.stream().map(Team::getSize);
    }

    public Integer getTotalSize() {
        return this.getSizeList().reduce(0, Integer::sum);
    }
}
