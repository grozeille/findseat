package fr.grozeille.findseat.model.opta2;

import fr.grozeille.findseat.model.BookingType;
import fr.grozeille.findseat.model.DayDispatchResult;
import fr.grozeille.findseat.model.PeopleWithTeam;
import fr.grozeille.findseat.model.WeekDispatchResult;
import net.datafaker.Faker;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

public class OptaPlanner2SolutionService {
    private final List<Desk> desks = new ArrayList<>();

    private final List<Team> teams = new ArrayList<>();

    private final Map<Integer, TeamDeskAssignmentSolution> solutionsPerWeek = new HashMap<>();

    private Integer totalDesks;

    private final Random random = new Random();

    public WeekDispatchResult plan(long timeout) {
        buildModel();
        resolve(timeout);
        return convertResultModel();
    }

    private WeekDispatchResult convertResultModel() {

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
                    peopleWithTeam.setTeam(new fr.grozeille.findseat.model.Team());
                    peopleWithTeam.getTeam().setName(t.getName());
                    peopleWithTeam.getTeam().setSplitOriginalName(t.getName());
                    peopleWithTeam.getTeam().setSplitTeam(false);
                    peopleWithTeam.getPeople().setEmail(faker.name().username());
                    peopleWithTeam.getPeople().setBookingType(Boolean.TRUE.equals(t.getIsMandatory()) ? BookingType.MANDATORY : BookingType.OPTIONAL);

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

    private void buildModel() {
        desks.clear();
        teams.clear();

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
    }
}
