package fr.grozeille.findseat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeopleWithTeam {
    private People people;
    private Team team;
}
