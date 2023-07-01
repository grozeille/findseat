package fr.grozeille.findseat.model.opta;

import fr.grozeille.findseat.model.*;
import net.datafaker.Faker;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;

import java.time.Duration;
import java.util.*;

public class OptaPlannerSolutionService {
    private final Map<Integer, DeskAssignmentSolution> solutionsPerWeek = new HashMap<>();

    private final List<DeskAssignment> deskAssignments = new ArrayList<>();

    private final List<People> people = new ArrayList<>();

    private final Random random = new Random();

    public WeekDispatchResult plan(long timeout) {
        buildModel();
        resolve(timeout);
        return convertResultModel();
    }

    private void buildModel() {
        deskAssignments.clear();
        people.clear();

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
    }

    private void resolve(long timeout){
        final SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(DeskAssignmentSolution.class)
                .withEntityClasses(DeskAssignment.class)
                .withConstraintProviderClass(DeskAssignmentConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(timeout));

        solutionsPerWeek.clear();

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
    }

    private WeekDispatchResult convertResultModel() {
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
        }

        return dispatchResult;
    }
}
