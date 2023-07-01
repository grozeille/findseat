package fr.grozeille.findseat.controllers;

import fr.grozeille.findseat.model.*;
import fr.grozeille.findseat.model.opta.*;
import fr.grozeille.findseat.model.opta.People;
import fr.grozeille.findseat.model.opta2.Desk;
import fr.grozeille.findseat.model.opta2.TeamDeskAssignmentConstraintProvider;
import fr.grozeille.findseat.model.opta2.TeamDeskAssignmentSolution;
import fr.grozeille.findseat.services.ConfigService;
import fr.grozeille.findseat.services.FileBookingService;
import fr.grozeille.findseat.services.TeamDeskDispatcher;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

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
        //buildDeskAssignment2(seconds);
        buildDeskAssignment3(seconds);
        return ResponseEntity.ok("OK");
    }

    private void buildDeskAssignment3(long timeout) throws Exception {

        List<Desk> desks = new ArrayList<>();

        Object[] deskGroups = new Object[]{
                new Object[] {
                        "A",
                        new Object[] {
                                new Object[]{"A", 3},
                                new Object[]{"B", 3},
                        }
                },
                new Object[] {
                        "B",
                        new Object[] {
                                new Object[]{"A", 3},
                                new Object[]{"B", 6},
                                new Object[]{"C", 6},
                                new Object[]{"D", 3},

                        }
                },
                new Object[] {
                        "C",
                        new Object[] {
                                new Object[]{"A", 5},
                                new Object[]{"B", 10},
                                new Object[]{"C", 10},
                                new Object[]{"D", 9},
                                new Object[]{"E", 5}

                        }
                }
        };
        Integer[] monitoringScreens = new Integer[] { 7,8,9,10,11,12};
        Integer totalDesks = Arrays.stream(deskGroups)
                .map(g -> ((Object[])g)[1])
                .flatMap(g -> Stream.of((Object[])g))
                .map(o -> (int)((Object[])o)[1])
                .reduce(0, Integer::sum);
        Boolean[] withMonitoringScreen = new Boolean[totalDesks];
        for(int indexWithMonitoringScreen : monitoringScreens) {
            withMonitoringScreen[indexWithMonitoringScreen-1] = true;
        }
        long cptDeskId = 0;
        for(Object g : deskGroups) {
            String group = (String) ((Object[]) g)[0];
            Object[] rows = (Object[]) ((Object[]) g)[1];
            int groupSize = Arrays.stream(rows).map(o -> (int)((Object[])o)[1]).reduce(0, Integer::sum);

            for (Object r : rows) {
                String row = (String) ((Object[]) r)[0];
                int deskNb = (int) ((Object[]) r)[1];

                int endOfRow = (int)cptDeskId + deskNb;
                int endOfDeskGroup = (int)cptDeskId + groupSize;

                for (int cpt = 1; cpt <= deskNb; cpt++) {
                    Desk d = new Desk(
                            cptDeskId,
                            group,
                            row,
                            cpt,
                            Arrays.copyOfRange(withMonitoringScreen, (int) cptDeskId, withMonitoringScreen.length),
                            endOfRow,
                            endOfDeskGroup,
                            totalDesks);
                    desks.add(d);
                    cptDeskId++;
                }
            }
        }

        List<fr.grozeille.findseat.model.opta2.Team> teams = new ArrayList<>();

        int[] teamSizes = new int[] {
                6,4,6,5,7,5,6,6,6,7
        };

        long cptTeam = 0;
        for(int s : teamSizes) {
            fr.grozeille.findseat.model.opta2.Team t = new fr.grozeille.findseat.model.opta2.Team(
                    cptTeam++,
                    Long.toString(cptTeam),
                    0,
                    true,
                    s);
            teams.add(t);
        }

        int otherPeople = totalDesks - teams.stream()
                .map(fr.grozeille.findseat.model.opta2.Team::getSize)
                .reduce(0, Integer::sum) + 10;

        final Random random = new Random();
        while(otherPeople > 0) {
            int teamSize = Math.min(random.nextInt(3)+1, otherPeople);
            fr.grozeille.findseat.model.opta2.Team t = new fr.grozeille.findseat.model.opta2.Team(
                    cptTeam++,
                    Long.toString(cptTeam),
                    0,
                    false,
                    teamSize);
            teams.add(t);
            otherPeople -= teamSize;
        }

        final SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(TeamDeskAssignmentSolution.class)
                .withEntityClasses(fr.grozeille.findseat.model.opta2.Team.class)
                .withConstraintProviderClass(TeamDeskAssignmentConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(timeout));

        Map<Integer, TeamDeskAssignmentSolution> solutionsPerWeek = new HashMap<>();

        List<Integer> days = Arrays.asList(1, 2, 3, 4, 5);
        List<Map.Entry<Integer, TeamDeskAssignmentSolution>> weekSimulationResults = days.parallelStream().map(day -> {
            TeamDeskAssignmentSolution problem = new TeamDeskAssignmentSolution(teams, desks);

            SolverFactory<TeamDeskAssignmentSolution> solverFactory = SolverFactory.create(solverConfig.withRandomSeed(random.nextLong()));
            Solver<TeamDeskAssignmentSolution> solver = solverFactory.buildSolver();

            TeamDeskAssignmentSolution solution = solver.solve(problem);
            return Map.entry(day, solution);
        }).toList();

        for(Map.Entry<Integer, TeamDeskAssignmentSolution> e : weekSimulationResults) {
            solutionsPerWeek.put(e.getKey(), e.getValue());
        }

        Faker faker = new Faker();
        WeekDispatchResult dispatchResult = new WeekDispatchResult();
        for(int day = 1; day <= 5; day++) {
            TeamDeskAssignmentSolution solution = solutionsPerWeek.get(day);
            // convert to the other legacy data model

            DayDispatchResult dayDispatchResult = new DayDispatchResult();
            for(fr.grozeille.findseat.model.opta2.Team t : solution.getTeams()) {
                for(int cptMemberInTeam = 0; cptMemberInTeam < t.getSize(); cptMemberInTeam++) {
                    PeopleWithTeam peopleWithTeam = new PeopleWithTeam();
                    peopleWithTeam.setPeople(new fr.grozeille.findseat.model.People());
                    peopleWithTeam.setTeam(new Team());
                    peopleWithTeam.getTeam().setName(t.getName());
                    peopleWithTeam.getTeam().setSplitOriginalName(t.getName());
                    peopleWithTeam.getTeam().setSplitTeam(false);
                    peopleWithTeam.getPeople().setEmail(faker.name().username());
                    peopleWithTeam.getPeople().setBookingType(t.getIsMandatory() ? BookingType.MANDATORY : BookingType.OPTIONAL);

                    if(t.getDesk() != null) {
                        int currentDeskIndex = Math.toIntExact(t.getDesk().getId()) + cptMemberInTeam;
                        if(currentDeskIndex >= totalDesks) {
                            dayDispatchResult.getNotAbleToDispatch().add(peopleWithTeam);
                        } else {
                            Desk currentDesk = solution.getDesks().get(currentDeskIndex);
                            String deskNumber = currentDesk.toDeskNumber();
                            dayDispatchResult.getDeskAssignedToPeople().put(deskNumber, peopleWithTeam);
                        }

                    } else {
                        dayDispatchResult.getNotAbleToDispatch().add(peopleWithTeam);
                    }
                }
            }
            dispatchResult.getDispatchPerDayOfWeek().put(day, dayDispatchResult);
            fileBookingService.exportPeopleWithoutDesk(day, dayDispatchResult.getNotAbleToDispatch());
        }

        fileBookingService.exportToExcelFloorMap(dispatchResult);
    }

    private void buildDeskAssignment2(long timeout) throws Exception {

        final List<DeskAssignment> deskAssignments = new ArrayList<>();
        Object[] rows = new Object[]{
                new Object[]{"A", 3, "A"},
                new Object[]{"B", 3, "A"},
                new Object[]{"A", 3, "B"},
                new Object[]{"B", 6, "B"},
                new Object[]{"C", 6, "B"},
                new Object[]{"D", 3, "B"},
                new Object[]{"A", 5, "C"},
                new Object[]{"B", 10, "C"},
                new Object[]{"C", 10, "C"},
                new Object[]{"D", 9, "C"},
                new Object[]{"E", 5, "C"}
        };
        long cptDeskId = 0;
        for(Object r : rows) {
            String row = (String)((Object[])r)[0];
            int deskNb = (int)((Object[])r)[1];
            String deskGroup = (String)((Object[])r)[2];
            for(int cpt = 1; cpt <= deskNb; cpt++) {
                DeskAssignment d = new DeskAssignment(cptDeskId++, deskGroup, row, cpt, false, false);
                deskAssignments.add(d);
            }
        }

        final List<People> people = new ArrayList<>();
        //7, 6, 7, 6, 1, 6, 7, 5, 10, 7, 7, 3, 9, 12, 6, 7, 11, 8, 5, 6, 5, 12, 5
        //1, 1, 1, 0, 0, 1, 1, 1,  0, 1, 1, 0, 0,  0, 0, 1,  0, 1, 0, 0, 0,  0, 0
        Faker faker = new Faker();
        int[] teams = new int[] {
          6,4,6,5,7,5,6,6,6,7
        };
        int cptTeam = 0;
        for(int t : teams) {
            cptTeam++;
            for(int cpt = 0; cpt < t; cpt++) {
                People p = new People(
                        faker.name().username(),
                        Integer.toString(cptTeam),
                        false,
                        true);
                people.add(p);
            }
        }

        int otherPeople = deskAssignments.size() - people.size() + 10;
        final Random random = new Random();
        int cptInTeam = 0;
        int teamSize = random.nextInt(3)+1;
        cptTeam++;
        for(int cpt = 0; cpt < otherPeople; cpt++) {
            if(cptInTeam >= teamSize) {
                cptTeam++;
                cptInTeam = 0;
                teamSize = random.nextInt(3)+1;
            }
            People p = new People(
                    faker.name().username(),
                    Integer.toString(cptTeam),
                    false,
                    false);
            people.add(p);
            cptInTeam++;
        }

        int missingDesks = people.size() - deskAssignments.size();
        for(int cpt = 0; cpt <  missingDesks; cpt++) {
            deskAssignments.add(new DeskAssignment(cptDeskId++, "E", "E", cpt, false, true));
        }


        final SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(DeskAssignmentSolution.class)
                .withEntityClasses(DeskAssignment.class)
                .withConstraintProviderClass(DeskAssignmentConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(timeout));

        Map<Integer, DeskAssignmentSolution> solutionsPerWeek = new HashMap<>();

        List<Integer> days = Arrays.asList(1, 2, 3, 4, 5);
        List<Map.Entry<Integer, DeskAssignmentSolution>> weekSimulationResults = days.parallelStream().map(day -> {
            DeskAssignmentSolution problem = new DeskAssignmentSolution(people, deskAssignments);

            SolverFactory<DeskAssignmentSolution> solverFactory = SolverFactory.create(solverConfig.withRandomSeed(random.nextLong()));
            Solver<DeskAssignmentSolution> solver = solverFactory.buildSolver();

            DeskAssignmentSolution solution = solver.solve(problem);
            return Map.entry(day, solution);
        }).toList();

        for(Map.Entry<Integer, DeskAssignmentSolution> e : weekSimulationResults) {
            solutionsPerWeek.put(e.getKey(), e.getValue());
        }

        /*for(int day = 1; day <= 5; day++) {
            DeskAssignmentSolution problem = new DeskAssignmentSolution(people, deskAssignments);

            SolverFactory<DeskAssignmentSolution> solverFactory = SolverFactory.create(solverConfig.withRandomSeed(random.nextLong()));
            Solver<DeskAssignmentSolution> solver = solverFactory.buildSolver();

            DeskAssignmentSolution solution = solver.solve(problem);
            solutionsPerWeek.put(day, solution);
        }*/

        WeekDispatchResult dispatchResult = new WeekDispatchResult();
        for(int day = 1; day <= 5; day++) {
            DeskAssignmentSolution solution = solutionsPerWeek.get(day);
            // convert to the other legacy data model
            DayDispatchResult dayDispatchResult = new DayDispatchResult();
            for(DeskAssignment d : solution.getDeskAssignments()) {
                String deskNumber = d.toDeskNumber();
                PeopleWithTeam peopleWithTeam = new PeopleWithTeam();
                peopleWithTeam.setPeople(new fr.grozeille.findseat.model.People());
                peopleWithTeam.setTeam(new Team());
                peopleWithTeam.getTeam().setName(d.getPeople().getTeam());
                peopleWithTeam.getTeam().setSplitTeam(false);
                peopleWithTeam.getTeam().setSplitOriginalName(d.getPeople().getTeam());
                peopleWithTeam.getPeople().setEmail(d.getPeople().getEmail());
                peopleWithTeam.getPeople().setBookingType(d.getPeople().getIsMandatory() ? BookingType.MANDATORY : BookingType.OPTIONAL);

                if(d.getDeskGroup().equalsIgnoreCase("E")) {
                    dayDispatchResult.getNotAbleToDispatch().add(peopleWithTeam);
                } else {
                    dayDispatchResult.getDeskAssignedToPeople().put(deskNumber, peopleWithTeam);
                }
            }
            dispatchResult.getDispatchPerDayOfWeek().put(day, dayDispatchResult);
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
