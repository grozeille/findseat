package fr.grozeille.findseat.model.opta2;

import fr.grozeille.findseat.model.*;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

public class OptaPlanner2SolutionService {


    private final Map<Integer, List<Desk>> deskPerWeek = new HashMap<>();

    private final Map<Integer, List<Team>> teamsPerWeek = new HashMap<>();

    private final Map<Integer, TeamDeskAssignmentSolution> solutionsPerWeek = new HashMap<>();

    private final Map<Integer, Map<String, List<String>>> peopleInTeamsPerWeek = new HashMap<>();

    private final Random random = new Random();

    public OptaPlanner2SolutionService() {
        buildSampleModel();
    }

    public OptaPlanner2SolutionService(List<Room> rooms, Map<Integer, List<fr.grozeille.findseat.model.Team>> teamsByDay) {
        List<Desk> desks = new ArrayList<>();
        long cptDesk = 0;

        int totalDesks = 0;
        for(Room r : rooms) {
            for(Map.Entry<String, List<String>> row : r.getDesksGroups().entrySet()) {
                for(String deskNb : row.getValue()) {
                    totalDesks++;
                }
            }
        }

        Boolean[] withMonitoringScreens = new Boolean[totalDesks];

        for(Room r : rooms) {
            int roomSize = r.roomSize();
            int roomEndIndex = (int) (cptDesk + roomSize);

            for(Map.Entry<String, List<String>> row : r.getDesksGroups().entrySet()) {
                int rowSize = row.getValue().size();
                int rowEndIndex = (int) (cptDesk + rowSize);

                for(String deskNb : row.getValue()) {
                    Desk desk = new Desk(
                            cptDesk++,
                            r.getName(),
                            row.getKey(),
                            deskNb,
                            withMonitoringScreens,
                            rowEndIndex,
                            roomEndIndex,
                            totalDesks);
                    desks.add(desk);
                }
            }
        }

        for(int day : teamsByDay.keySet()) {
            Map<String, List<String>> peopleInTeams = new HashMap<>();
            List<fr.grozeille.findseat.model.Team> teams = teamsByDay.get(day);
            List<Team> teamsForTheDay = new ArrayList<>();
            long cptTeam = 0;
            for(fr.grozeille.findseat.model.Team t1 : teams) {
                boolean mandatory = t1.getMembers().stream().anyMatch(p -> p.getBookingType().equals(BookingType.MANDATORY));

                Team t2 = new Team(
                        cptTeam++,
                        t1.getName(),
                        0,
                        mandatory,
                        t1.size());
                teamsForTheDay.add(t2);

                List<String> allEmails = t1.getMembers().stream().map(People::getEmail).toList();
                peopleInTeams.put(t2.getName(), allEmails);
            }

            peopleInTeamsPerWeek.put(day, peopleInTeams);

            teamsPerWeek.put(day, teamsForTheDay);

            deskPerWeek.put(day, desks);
        }
    }

    public WeekDispatchResult plan(long timeout) {
        resolve(timeout);
        return convertResultModel();
    }

    private WeekDispatchResult convertResultModel() {

        WeekDispatchResult dispatchResult = new WeekDispatchResult();
        for(int day = 1; day <= 5; day++) {
            int totalDesks = this.deskPerWeek.get(day).size();

            TeamDeskAssignmentSolution solution = solutionsPerWeek.get(day);
            // convert to the other legacy data model

            DayDispatchResult dayDispatchResult = new DayDispatchResult();
            for(fr.grozeille.findseat.model.opta2.Team t : solution.getTeams()) {
                List<String> emails = peopleInTeamsPerWeek.get(day).get(t.getName());
                for(int cptMemberInTeam = 0; cptMemberInTeam < t.getSize(); cptMemberInTeam++) {
                    PeopleWithTeam peopleWithTeam = new PeopleWithTeam();
                    peopleWithTeam.setPeople(new fr.grozeille.findseat.model.People());
                    peopleWithTeam.setTeam(new fr.grozeille.findseat.model.Team());
                    peopleWithTeam.getTeam().setName(t.getName());
                    peopleWithTeam.getTeam().setSplitOriginalName(t.getName());
                    peopleWithTeam.getTeam().setSplitTeam(false);
                    peopleWithTeam.getPeople().setEmail(emails.get(cptMemberInTeam));
                    peopleWithTeam.getPeople().setBookingType(Boolean.TRUE.equals(t.getIsMandatory()) ? BookingType.MANDATORY : BookingType.OPTIONAL);

                    if(t.getDesk() != null) {
                        int currentDeskIndex = Math.toIntExact(t.getDesk().getId()) + cptMemberInTeam;
                        if(currentDeskIndex >= totalDesks) {
                            dayDispatchResult.getNotAbleToDispatch().add(peopleWithTeam);
                        } else {
                            Desk currentDesk = solution.getDesks().get(currentDeskIndex);
                            String deskNumber = currentDesk.getNumber();
                            dayDispatchResult.getDeskAssignedToPeople().put(deskNumber, peopleWithTeam);
                        }

                    } else {
                        dayDispatchResult.getNotAbleToDispatch().add(peopleWithTeam);
                    }
                }
            }
            dispatchResult.getDispatchPerDayOfWeek().put(day, dayDispatchResult);
        }

        return dispatchResult;
    }

    private void resolve(long timeout) {
        final SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(TeamDeskAssignmentSolution.class)
                .withEntityClasses(fr.grozeille.findseat.model.opta2.Team.class)
                .withConstraintProviderClass(TeamDeskAssignmentConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(timeout));

        solutionsPerWeek.clear();

        List<Integer> days = Arrays.asList(1, 2, 3, 4, 5);
        List<Map.Entry<Integer, TeamDeskAssignmentSolution>> weekSimulationResults = days.parallelStream().map(day -> {
            List<Team> teams = this.teamsPerWeek.get(day);
            List<Desk> desks = this.deskPerWeek.get(day);
            TeamDeskAssignmentSolution problem = new TeamDeskAssignmentSolution(teams, desks);

            SolverFactory<TeamDeskAssignmentSolution> solverFactory = SolverFactory.create(solverConfig.withRandomSeed(random.nextLong()));
            Solver<TeamDeskAssignmentSolution> solver = solverFactory.buildSolver();

            TeamDeskAssignmentSolution solution = solver.solve(problem);
            return Map.entry(day, solution);
        }).toList();

        for(Map.Entry<Integer, TeamDeskAssignmentSolution> e : weekSimulationResults) {
            solutionsPerWeek.put(e.getKey(), e.getValue());
        }
    }

    private void buildSampleModel() {
        int totalDesks = 0;

        List<Desk> desks = new ArrayList<>();
        List<Team> teams = new ArrayList<>();

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
        totalDesks = Arrays.stream(deskGroups)
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
                            String.format("%03d", cpt),
                            Arrays.copyOfRange(withMonitoringScreen, (int) cptDeskId, withMonitoringScreen.length),
                            endOfRow,
                            endOfDeskGroup,
                            totalDesks);
                    desks.add(d);
                    cptDeskId++;
                }
            }
        }


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

        for(int day = 1; day <= 5; day++) {
            List<Team> teamForTheDay = new ArrayList<>();
            for(Team t : teams) {
                teamForTheDay.add(t.clone());
            }
            this.teamsPerWeek.put(day, teamForTheDay);
            this.deskPerWeek.put(day, desks);
        }
    }
}
