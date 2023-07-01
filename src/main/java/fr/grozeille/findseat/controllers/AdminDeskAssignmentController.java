package fr.grozeille.findseat.controllers;

import fr.grozeille.findseat.model.DayDispatchResult;
import fr.grozeille.findseat.model.Room;
import fr.grozeille.findseat.model.Team;
import fr.grozeille.findseat.model.WeekDispatchResult;
import fr.grozeille.findseat.model.opta.OptaPlannerSolutionService;
import fr.grozeille.findseat.model.opta2.OptaPlanner2SolutionService;
import fr.grozeille.findseat.services.ConfigService;
import fr.grozeille.findseat.services.FileBookingService;
import fr.grozeille.findseat.services.TeamDeskDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/admin/desk-assignment")
public class AdminDeskAssignmentController {

    public static final String MIME_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final FileBookingService fileBookingService;

    private final TeamDeskDispatcher teamDeskDispatcher;

    private final ConfigService configService;

    public AdminDeskAssignmentController(FileBookingService fileBookingService, TeamDeskDispatcher teamDeskDispatcher, ConfigService configService) {
        this.fileBookingService = fileBookingService;
        this.teamDeskDispatcher = teamDeskDispatcher;
        this.configService = configService;
    }

    @PostMapping("/desk-assignment/build")
    public ResponseEntity runDeskAssignment(@RequestParam(required = false, defaultValue = "30") long seconds) throws Exception {
        //buildDeskAssignment();
        buildDeskAssignment2(seconds);
        //buildDeskAssignment3(seconds);
        return ResponseEntity.ok("OK");
    }

    private void buildDeskAssignment3(long timeout) throws Exception {


        OptaPlanner2SolutionService optaPlannerSolutionService = new OptaPlanner2SolutionService();

        WeekDispatchResult dispatchResult = optaPlannerSolutionService.plan(timeout);

        // for each day, export all people without desk
        for(int day = 1; day <= 5; day++) {
            DayDispatchResult dayDispatchResult = dispatchResult.getDispatchPerDayOfWeek().get(day);
            fileBookingService.exportPeopleWithoutDesk(day, dayDispatchResult.getNotAbleToDispatch());
        }

        fileBookingService.exportToExcelFloorMap(dispatchResult);
    }

    private void buildDeskAssignment2(long timeout) throws Exception {
        List<Room> rooms = configService.parseRoomsFile();

        // read all booking files for next week
        Map<Integer, List<Team>> teamsByDay = fileBookingService.readTeamForWeekFile();

        File outputFile = new File(fileBookingService.getOutputFolder(), FileBookingService.FILE_ALL_PEOPLE_WEEK);

        // export the aggregated list of all booking
        fileBookingService.writeTeamForWeekFile(teamsByDay, outputFile);

        OptaPlannerSolutionService optaPlannerSolutionService = new OptaPlannerSolutionService(rooms, teamsByDay);
        WeekDispatchResult dispatchResult = optaPlannerSolutionService.plan(timeout);

        // for each day, export all people without desk
        for(int day = 1; day <= 5; day++) {
            DayDispatchResult dayDispatchResult = dispatchResult.getDispatchPerDayOfWeek().get(day);
            fileBookingService.exportPeopleWithoutDesk(day, dayDispatchResult.getNotAbleToDispatch());
        }

        fileBookingService.exportToExcelFloorMap(dispatchResult);
    }

    private void buildDeskAssignment() throws Exception {
        List<Room> rooms = configService.parseRoomsFile();

        // read all booking files for next week
        Map<Integer, List<Team>> teamsByDay = fileBookingService.readTeamForWeekFile();

        File outputFile = new File(fileBookingService.getOutputFolder(), FileBookingService.FILE_ALL_PEOPLE_WEEK);

        // export the aggregated list of all booking
        fileBookingService.writeTeamForWeekFile(teamsByDay, outputFile);

        // run the dispatch algorithm to assign people to desks
        WeekDispatchResult dispatchResult = teamDeskDispatcher.dispatch(teamsByDay, rooms);

        // for each day, export all people without desk
        for(int day = 1; day <= 5; day++) {
            DayDispatchResult dayDispatchResult = dispatchResult.getDispatchPerDayOfWeek().get(day);
            fileBookingService.exportPeopleWithoutDesk(day, dayDispatchResult.getNotAbleToDispatch());
        }

        fileBookingService.exportToExcelFloorMap(dispatchResult);
    }

    @GetMapping(value = "/desk-assignment", produces = MIME_TYPE_XLSX)
    public ResponseEntity<byte[]> getDeskAssignment() throws Exception {
        int nextWeek = fileBookingService.nextWeek();
        byte[] xlsxData = readDeskAssignment();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=w"+nextWeek+".xlsx");
        return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(xlsxData);
    }

    private byte[] readDeskAssignment() throws Exception {
        int nextWeek = fileBookingService.nextWeek();
        File file = new File(fileBookingService.getOutputFolder(), "w"+nextWeek+".xlsx");

        if(!file.exists()) {
            return new byte[0];
        }

        return Files.readAllBytes(file.toPath());
    }

}
