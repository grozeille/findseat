package org.grozeille.model;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class Team implements Cloneable {
    @NonNull
    private String name;

    private List<People> members = new ArrayList<>();
    @NonNull
    private String managerEmail;

    private boolean splitTeam;
    private String splitOriginalName;

    public Team(String name) {
        this.name = name;
    }

    public Object clone() {
        Team newClone = new Team();
        newClone.setName(this.name);
        newClone.setSplitTeam(this.splitTeam);
        newClone.setSplitOriginalName(this.splitOriginalName);
        newClone.setManagerEmail(this.managerEmail);
        newClone.setMembers(new ArrayList<>(this.members.stream().map(p -> (People)p.clone()).toList()));
        return newClone;
    }

    public int size() {
        if(this.getMembers() != null) {
            return this.getMembers().size();
        }

        return 0;
    }

    public void addMember(People people) {
        members.add(people);
    }
}
