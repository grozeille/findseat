package org.grozeille;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team {
    private String name;
    private Integer size;
    private boolean teamDay;
}
