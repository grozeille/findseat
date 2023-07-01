package fr.grozeille.findseat.model.opta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class People {

    private String email;

    private String team;

    private Boolean wantMonitoringScreen;

    private Boolean isMandatory;
}
