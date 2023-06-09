package fr.grozeille.findseat.model.opta2;

import lombok.Data;

@Data
public class Desk {

    private Long id;

    private String deskGroup;

    private String row;

    private String number;

    private Boolean[] withMonitoringScreens;

    private Integer endOfRow;

    private Integer endOfDeskGroup;

    private Integer endOfOpenSpace;

    public String toDeskNumber() {
        return deskGroup + row + number;
    }

    public Desk() {

    }

    public Desk(Long id, String deskGroup, String row, String number, Boolean[] withMonitoringScreens, Integer endOfRow, Integer endOfDeskGroup, Integer endOfOpenSpace) {
        this.id = id;
        this.deskGroup = deskGroup;
        this.row = row;
        this.number = number;
        this.withMonitoringScreens = withMonitoringScreens;
        this.endOfRow = endOfRow;
        this.endOfDeskGroup = endOfDeskGroup;
        this.endOfOpenSpace = endOfOpenSpace;
    }
}
