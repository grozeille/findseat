package org.grozeille;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamRoomDispatchScenario {
    private int roomSize;

    private String roomName;
    private List<Team> teams;

    private int score;

    public Stream<Integer> getSizeList() {
        if(teams == null) return Stream.<Integer>builder().build();
        return teams.stream().map(Team::getSize);
    }

    public Integer getTotalSize() {
        return this.getSizeList().reduce(0, Integer::sum);
    }
}
