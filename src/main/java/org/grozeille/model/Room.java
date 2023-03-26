package org.grozeille.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    private String name;
    private LinkedHashMap<String, List<String>> desksGroups;

    public Integer roomSize() {
        if(desksGroups == null) return 0;
        return desksGroups.values().stream().map(List::size).reduce(0, Integer::sum);
    }

    public Integer[] roomGroupSizes() {
        return desksGroups.values().stream().map(List::size).toArray(Integer[]::new);
    }
}
