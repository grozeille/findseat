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
    @NonNull
    private Integer size; // TODO temporary because it's easier to test by forcing the size, but should be calculated based on the members
    @NonNull // Used only for Lombok
    private boolean mandatory;
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
        newClone.setSize(this.size);
        newClone.setMandatory(this.mandatory);
        newClone.setSplitOriginalName(this.splitOriginalName);
        newClone.setManagerEmail(this.managerEmail);
        newClone.setMembers(new ArrayList<>(this.members));
        return newClone;
    }

    public void addMember(People people) {
        members.add(people);
    }
}
