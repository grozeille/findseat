package fr.grozeille.findseat.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DayDispatchResult {
    private Map<String, PeopleWithTeam> deskAssignedToPeople = new HashMap<>();
    private List<PeopleWithTeam> notAbleToDispatch = new ArrayList<>();
}
