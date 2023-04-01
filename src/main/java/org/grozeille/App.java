package org.grozeille;

import org.grozeille.model.DayDispatchResult;
import org.grozeille.model.Room;
import org.grozeille.model.Team;
import org.grozeille.model.WeekDispatchResult;
import org.grozeille.services.TeamDeskDispatcher;
import org.grozeille.util.ConfigUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class App {
    public static final String DESTINATION_PATH = "target";

    public static void main(String[] args ) {

        List<Room> rooms = ConfigUtil.parseRoomsFile("test1");

        Map<Integer, List<Team>> teamsByDay = ConfigUtil.parseTeamForWeekFile("teams2");
        //Map<Integer, List<Team>> teamsByDay = ConfigUtil.getSampleTeamForWeek(totalSizeForAllRooms);
        ConfigUtil.saveTeamForWeekFile(teamsByDay);

        TeamDeskDispatcher teamDeskDispatcher = new TeamDeskDispatcher();
        WeekDispatchResult dispatchResult = teamDeskDispatcher.dispatch(teamsByDay, rooms);


        Path outputDirectory = Paths.get(DESTINATION_PATH);
        for(int day = 1; day <= 5; day++) {
            DayDispatchResult dayDispatchResult = dispatchResult.getDispatchPerDayOfWeek().get(day);
            ConfigUtil.exportAllPeople(day, teamsByDay.get(day), outputDirectory);
            ConfigUtil.exportPeopleWithoutDesk(day, dayDispatchResult.getNotAbleToDispatch(), outputDirectory);
        }

        ConfigUtil.exportToExcelFloorMap(dispatchResult, outputDirectory);
    }
}
