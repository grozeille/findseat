package org.grozeille;

import com.opencsv.CSVWriter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.grozeille.model.*;
import org.grozeille.util.ConfigUtil;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public class App {

    public static final String CONSOLE_SEPARATOR = "================";
    public static final String DESTINATION_PATH = "target";

    public static void main(String[] args ) {

        long startupTime = System.currentTimeMillis();

        List<Room> rooms = ConfigUtil.parseRoomsFile("test1");
        Map<String, Room> roomsByName = rooms.stream().collect(Collectors.toMap(Room::getName, Function.identity()));
        final Integer totalSizeForAllRooms = rooms.stream().map(Room::roomSize).reduce(0, Integer::sum);


        //List<Team> teams = ConfigUtil.parseTeamsFile("test2");
        Map<Integer, List<Team>> teamsByDay = ConfigUtil.parseTeamForWeekFile("teams2");
        //Map<Integer, List<Team>> teamsByDay = ConfigUtil.getSampleTeamForWeek(totalSizeForAllRooms);
        ConfigUtil.saveTeamForWeekFile(teamsByDay);

        Map<Integer, Map<String, Pair<Team, People>>> deskPeopleMappingPerDay = new HashMap<>();

        // for each day of the week, find the best dispatch scenario to assign desks to everyone
        for(int day = 1; day <= 5; day++) {

            LinkedList<Team> teams = new LinkedList<>(teamsByDay.get(day));
            teamsByDay.put(day, teams);

            // find the best dispatch scenario for the floor, with the best dispatch scenario for each desk group
            Map<String, TeamDispatchScenario> bestDeskGroupScenario = findBestScenarioByRoom(day, rooms, teams);

            // Assign a desk to all people
            List<People> peopleWithDesk = new ArrayList<>();
            Map<String, Pair<Team,People>> deskPeopleMapping = new HashMap<>();

            // for each room, get the dispatch for each desk groups
            for(Map.Entry<String, TeamDispatchScenario> entry : bestDeskGroupScenario.entrySet()) {
                Room room = roomsByName.get(entry.getKey());
                for(TeamRoomDispatchScenario sr : entry.getValue().getDispatched()) {
                    // get all desks of that desk group in that room
                    List<String> desks = room.getDesksGroups().get(sr.getRoomName());
                    Iterator<String> desksIterator = desks.iterator();
                    // assign a desk for each people in that desk group
                    for (Team teamsInDeskGroup : sr.getTeams()) {
                        for (People peopleInDeskGroup : teamsInDeskGroup.getMembers()) {
                            if (desksIterator.hasNext()) {
                                deskPeopleMapping.put(desksIterator.next(), ImmutablePair.of(teamsInDeskGroup, peopleInDeskGroup));
                                peopleWithDesk.add(peopleInDeskGroup);
                            }
                        }
                    }
                }
            }

            deskPeopleMappingPerDay.put(day, deskPeopleMapping);

            exportAllPeople(teams, day, Paths.get(DESTINATION_PATH));
            exportPeopleWithoutDesk(teams, peopleWithDesk, day);
        }

        Duration totalTime = Duration.ofMillis(System.currentTimeMillis()-startupTime);
        System.out.println("Dispatch terminated in "+ totalTime.toHours()+":"+totalTime.toMinutes()+":"+totalTime.toSecondsPart()+":"+totalTime.toMillisPart());

        try {
            URL resource = App.class.getClassLoader().getResource("floor_mapping.xlsx");
            FileInputStream file = new FileInputStream(new File(resource.toURI()));
            Workbook workbook = new XSSFWorkbook(file);

            for (int day = 1; day <= 5; day++) {
                // create a mapping of team/color
                String[] colors = new String[]{"#838CA9", "#A4D78D", "#FFE485", "#FFC598", "#E58787", "#98EBEC", "#70A3CA", "#6994AE", "#FACF7F", "#F7A072", "#FF0F80", "#2B50AA", "#FF9FE5", "#FFD4D4", "#FF858D"};

                Map<String, String> teamColorMap = new HashMap<>();
                List<Team> allDispatchedTeams = teamsByDay.get(day);
                for (int i = 0; i < allDispatchedTeams.size(); i++) {
                    teamColorMap.put(allDispatchedTeams.get(i).getName(), colors[i % colors.length]);
                }

                // clone the floor plan for a specific day to replace desk names by the assigned people
                workbook.cloneSheet(0);
                workbook.setSheetName(workbook.getNumberOfSheets() - 1, DayOfWeek.of(day).name());

                Sheet sheet = workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String currentCellValue = cell.getStringCellValue();
                        if (!currentCellValue.isEmpty()) {
                            Pair<Team, People> peopleInTeam = deskPeopleMappingPerDay.get(day).get(currentCellValue);
                            if (peopleInTeam != null) {
                                String newCellValue = peopleInTeam.getLeft().getSplitOriginalName() + " " + peopleInTeam.getRight().getEmail();
                                cell.setCellValue(newCellValue);

                                CellStyle cellStyle = cell.getSheet().getWorkbook().createCellStyle();

                                String hexaColor = teamColorMap.get(peopleInTeam.getLeft().getSplitOriginalName());
                                XSSFColor color = new XSSFColor(hexaToRgb(hexaColor));

                                cellStyle.setFillForegroundColor(color);
                                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                cell.setCellStyle(cellStyle);
                            } else {
                                cell.setCellValue("EMPTY");
                            }
                        }
                    }
                }
            }

            File floorPlanOutputExcelFile = new File("target/output.xlsx");
            if (floorPlanOutputExcelFile.exists()) {
                floorPlanOutputExcelFile.delete();
            }
            workbook.write(new FileOutputStream(floorPlanOutputExcelFile));

        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, TeamDispatchScenario> findBestScenarioByRoom(int day, List<Room> rooms, LinkedList<Team> teams) {
        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("Day: " + day);

        final Integer totalSizeForAllRooms = rooms.stream().map(Room::roomSize).reduce(0, Integer::sum);
        Map<String, Room> roomsByName = rooms.stream().collect(Collectors.toMap(Room::getName, Function.identity()));

        final Integer totalSizeTeams = teams.stream().map(Team::getSize).reduce(0, Integer::sum);

        System.out.println(CONSOLE_SEPARATOR);
        for (Room r : rooms) {
            System.out.println("Room size=" + r.roomSize() + ", deskGroups=[" + String.join(",", Arrays.stream(r.roomGroupSizes()).map(Object::toString).toList()) + "]");
        }
        System.out.println("Team floor size=" + totalSizeForAllRooms);
        System.out.println(CONSOLE_SEPARATOR);
        for (Team t : teams) {
            System.out.println("Team name=" + t.getName() + ", size=" + t.getSize() + ", mandatory=" + t.isMandatory());
        }
        System.out.println("Team total size=" + totalSizeTeams);
        System.out.println(CONSOLE_SEPARATOR);


        // Random for each run ?
        //Collections.shuffle(teams);

        // first, try to identify all scenarios to fit all people on the floor with all teams as mandatory
        // consider the floor as a single room
        List<TeamRoomDispatchScenario> floorScenarios = null;
        if (totalSizeTeams <= totalSizeForAllRooms) {
            floorScenarios = new ArrayList<>();
            floorScenarios.add(new TeamRoomDispatchScenario(totalSizeForAllRooms, "floor", 0, new LinkedList<>(teams)));
        } else {
            floorScenarios = findAllDispatchScenarioForRoom(teams, "floor", totalSizeForAllRooms, new LinkedList<>());
        }

        // number of mandatory teams we must found in each scenario
        final long nbMandatoryTeams = teams.stream().filter(Team::isMandatory).count();

        // just keep scenarios that include all mandatory teams, and scenario that fit as maximum as possible all teams (all if all teams are smaller than the room, or at least the room size
        floorScenarios = floorScenarios.stream().filter(s -> {
            long currentScenarioNbMandatoryTeams = s.getTeams().stream().filter(Team::isMandatory).count();
            return currentScenarioNbMandatoryTeams == nbMandatoryTeams;
        }).filter(s -> s.getTotalSize() >= Math.min(totalSizeTeams, totalSizeForAllRooms)).toList();

        System.out.println("Found " + floorScenarios.size() + " scenarios to fit dispatch teams on the floor, including all mandatory teams");
        int scenarioCpt = 0;
        Map<String, List<TeamRoomDispatchScenario>> scenariosByOptionalTeamsSizeCombination = new HashMap<>();
        for(TeamRoomDispatchScenario floorDispatchScenario : floorScenarios) {

            // keep only the optional teams. we know that all scenario contain the mandatory teams
            // reduce the teams to the size only: different teams but with same size will produce an equivalent scenario with the same score
            String teamSizeCombination = floorDispatchScenario.getTeams().stream()
                    .filter(Predicate.not(Team::isMandatory))
                    .map(Team::getSize)
                    .sorted()
                    .map(Object::toString)
                    .collect(Collectors.joining());
            List<TeamRoomDispatchScenario> scenarioList;
            if(!scenariosByOptionalTeamsSizeCombination.containsKey(teamSizeCombination)) {
                scenarioList  = new ArrayList<>();
                scenariosByOptionalTeamsSizeCombination.put(teamSizeCombination, scenarioList);
            } else {
                scenarioList = scenariosByOptionalTeamsSizeCombination.get(teamSizeCombination);
            }
            scenarioList.add(floorDispatchScenario);
        }
        Random random = new Random();
        List<TeamRoomDispatchScenario> simplifiedFloorScenarios = new ArrayList<>();
        for(Map.Entry<String, List<TeamRoomDispatchScenario>> e : scenariosByOptionalTeamsSizeCombination.entrySet()) {
            System.out.println("Scenario combination: "+e.getKey());

            // keep only one scenario of the same combination, because they will all have the same score
            simplifiedFloorScenarios.add(e.getValue().get(random.nextInt(e.getValue().size())));

            for(TeamRoomDispatchScenario floorDispatchScenario : e.getValue()) {
                String[] teamsStringFloorScenario = floorDispatchScenario.getTeams().stream()
                        .sorted(Comparator.comparing(Team::isMandatory).reversed())
                        .map(t -> t.getName()+"("+t.getSize()+")"+(t.isMandatory()?"*":""))
                        .toArray(String[]::new);

                int totalPeople = floorDispatchScenario.getTeams().stream().mapToInt(Team::getSize).sum();
                System.out.println("Floor Scenario "+scenarioCpt+": Total="+totalPeople+" ["+String.join(",", teamsStringFloorScenario)+"]");
                scenarioCpt++;
            }
        }


        int bestScenarioScore = Integer.MIN_VALUE;
        TeamDispatchScenario bestScenario = null;
        Map<String, TeamDispatchScenario> bestDeskGroupScenario = null;

        scenarioCpt = 0;
        // for each scenario, try to dispatch teams in the rooms
        for (TeamRoomDispatchScenario floorDispatchScenario : simplifiedFloorScenarios) {

            scenarioCpt++;
            System.out.println(CONSOLE_SEPARATOR);
            System.out.println("Scenario: " + scenarioCpt + ", total to fit=" + floorDispatchScenario.getTotalSize() + " in floor size=" + floorDispatchScenario.getRoomSize());

            int scenarioScore = 0;
            Map<String, TeamDispatchScenario> deskGroupScenario = new HashMap<>();

            // try to dispatch teams in the room and desk groups
            TeamDispatchScenario scenario = findBestDispatchScenariosForAllRooms(floorDispatchScenario.getTeams(), rooms);
            for (TeamRoomDispatchScenario r : scenario.getDispatched()) {
                //System.out.println("Room(size=" + r.getRoomSize() + ", teams=(" + r.getTeams() + "), score=" + r.getScore() + ")");

                // get the desk groups of the current room
                Room room = roomsByName.get(r.getRoomName());
                // create "virtual rooms" for each desk group
                List<Room> deskGroups = room.getDesksGroups().entrySet().stream().map(e -> new Room(e.getKey(), new LinkedHashMap<>(Map.ofEntries(entry(e.getKey(), e.getValue()))))).toList();

                // reset the "split" status of each teams for the desk group dispatch
                // reason: we must include in the next room the rest of a team split in the previous room
                // but we are now dispatching in desk groups, so the split status must apply inside the room
                // also, index by name, to avoid too much objects in memory to generate all permutations
                Map<String, Team> teamIndex = r.getTeams().stream()
                        .peek(t -> t.setSplitTeam(false))
                        .collect(Collectors.toMap(Team::getName, Function.identity()));
                List<String> teamsName = r.getTeams().stream().map(Team::getName).toList();

                //List<List<String>> allTeamPermutations = heapPermutation(teamsName);
                List<List<String>> allTeamPermutations = List.of(teamsName);
                System.out.println("Found "+allTeamPermutations.size()+" permutations for room "+ room.getName());
                int cptPermutation = 0;
                int bestPermutationScenarioScore = Integer.MIN_VALUE;
                TeamDispatchScenario bestPermutationScenario = null;
                for(List<String> teamsNameInPermutation : allTeamPermutations) {
                    if(cptPermutation > 362880) {
                        System.out.println("Stop here, 362880 permutations is the maximum to reduce the time");
                        break;
                    }
                    LinkedList<Team> teamsInPermutation = new LinkedList<>(teamsNameInPermutation.stream().map(s -> teamIndex.get(s)).toList());
                    // dispatch teams in desk groups for the current room
                    TeamDispatchScenario subEndResult = findBestDispatchScenariosForAllRooms(teamsInPermutation, deskGroups);
                    if(subEndResult.totalScore() > bestPermutationScenarioScore) {
                        System.out.println("Permutation "+cptPermutation+" score: "+subEndResult.totalScore());
                        bestPermutationScenario = subEndResult;
                        bestPermutationScenarioScore = subEndResult.totalScore();
                    }
                    cptPermutation++;
                }
                // penalty to split inside the room is 2 times less than split across 2 rooms
                scenarioScore += bestPermutationScenarioScore/2;

                deskGroupScenario.put(r.getRoomName(), bestPermutationScenario);
            }

            // display the best scenario
            /*for(TeamRoomDispatchScenario r : scenario.getDispatched()) {
                System.out.println("Room(size=" + r.getRoomSize() + ", teams=(" + r.getTeams() + "), score=" + r.getScore() + ")");
                TeamDispatchScenario subEndResult = deskGroupScenario.get(r.getRoomName());
                for(TeamRoomDispatchScenario sr : subEndResult.getDispatched()) {
                    System.out.println("\tDesk(size=" + sr.getRoomSize() + ", teams=(" + sr.getTeams() + "), score=" + sr.getScore() + ")");
                }
            }*/

            scenarioScore += scenario.totalScore();

            // add teams that don't fit in the floor based on floor scenarios
            List<Team> notIncludedInTheFloorScenario = new ArrayList<>(teams);
            notIncludedInTheFloorScenario.removeAll(floorDispatchScenario.getTeams());
            scenario.getNotAbleToDispatch().addAll(notIncludedInTheFloorScenario);

            System.out.println("not able to fit: " + scenario.getNotAbleToDispatch());

            System.out.println("Total score: " + scenarioScore);

            // excludes scenarios without all mandatory teams
            boolean notAbleToDispatchMandatoryTeams = scenario.getNotAbleToDispatch().stream().anyMatch(t -> t.isMandatory());

            // keep only the best
            if (!notAbleToDispatchMandatoryTeams) {
                if (scenarioScore > bestScenarioScore) {
                    bestScenario = scenario;
                    bestScenarioScore = scenarioScore;
                    bestDeskGroupScenario = deskGroupScenario;
                }
            }
        }

        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("Best scenario");
        for (TeamRoomDispatchScenario r : bestScenario.getDispatched()) {
            String teamsString = String.join(",", r.getTeams().stream().map(Team::getName).toArray(String[]::new));
            System.out.println("Room(size=" + r.getRoomSize() + ", teams=(" + teamsString + "), score=" + r.getScore() + ")");
            for (TeamRoomDispatchScenario sr : bestDeskGroupScenario.get(r.getRoomName()).getDispatched()) {
                String teamsStringDeskGroup = String.join(",", sr.getTeams().stream().map(Team::getName).toArray(String[]::new));
                System.out.println("\tDesk(size=" + sr.getRoomSize() + ", teams=(" + teamsStringDeskGroup + "), score=" + sr.getScore() + ")");
            }
        }
        System.out.println("not able to fit: " + bestScenario.getNotAbleToDispatch());
        System.out.println("Total score: " + bestScenarioScore);

        return bestDeskGroupScenario;
    }

    private static void exportPeopleWithoutDesk(List<Team> teams, List<People> peopleWithDesk, int day) {
        List<String[]> output = new ArrayList<>();

        List<People> allPeople = teams.stream()
                .flatMap(team -> team.getMembers().stream())
                .toList();

        allPeople = new ArrayList<>(allPeople);
        allPeople.removeAll(peopleWithDesk);
        for (People p : allPeople) {
            String outputCell = p.getEmail();
            output.add(new String[]{outputCell});
            System.out.println("People without desk: " + p);
        }
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter("target/people_without_desk_" + DayOfWeek.of(day).name() + ".csv"))) {
            csvWriter.writeAll(output);
        } catch (IOException e) {
            throw new RuntimeException("Not able to write output CSV", e);
        }
    }


    public static void exportAllPeople(List<Team> teams, int day, Path path){
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String[]> output = new ArrayList<>();
        //header
        output.add(new String[]{"name", "email"});
        for (Team t : teams) {
            for (People p : t.getMembers()) {
                output.add(new String[]{t.getName(), p.getEmail()});
            }
        }
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(path + File.separator + "all_people_" + DayOfWeek.of(day).name() + ".csv"))) {
            csvWriter.writeAll(output);
        } catch (IOException e) {
            throw new RuntimeException("Not able to write output CSV", e);
        }
    }

    static byte[] hexaToRgb(String hexaColor) {
        if (hexaColor.startsWith("#")) {
            hexaColor = hexaColor.substring(1);
        }
        int resultRed = Integer.valueOf(hexaColor.substring(0, 2), 16);
        int resultGreen = Integer.valueOf(hexaColor.substring(2, 4), 16);
        int resultBlue = Integer.valueOf(hexaColor.substring(4, 6), 16);

        return new byte[]{(byte) resultRed, (byte) resultGreen, (byte) resultBlue};
    }

    static TeamDispatchScenario findBestDispatchScenariosForAllRooms(LinkedList<Team> teams, List<Room> rooms) {
        LinkedList<Team> teamsToDispatch = new LinkedList<>(teams);

        TeamDispatchScenario endResult = new TeamDispatchScenario();
        endResult.setDispatched(new ArrayList<>());

        // try to fit people in all "rooms", starting with the smallest one to the biggest one
        //rooms = rooms.stream().sorted(Comparator.comparingInt(Room::roomSize)).toList();
        // TODO: don't do it for desk groups to keep the right order

        for (Room room : rooms) {

            TeamRoomDispatchScenario result = findBestDispatchScenarioForRoom(teamsToDispatch, room.getName(), room.roomSize());
            if (result == null) {
                break;
            }
            Integer sum = result.getTotalSize();

            // if not all teams fit in this room, split the last team
            if (sum > room.roomSize()) {
                Team lastTeam = result.getTeams().get(result.getTeams().size() - 1);
                int spaceLeftWithoutLastTeam = room.roomSize() - (result.getTotalSize() - lastTeam.getSize());

                // split the last team, the second part will be used to dispatch to another room
                Pair<Team, Team> teamSplit = splitTeam(lastTeam, spaceLeftWithoutLastTeam);
                result.getTeams().remove(lastTeam);
                result.getTeams().add(teamSplit.getLeft());
                teamsToDispatch.remove(lastTeam);
                teamsToDispatch.addFirst(teamSplit.getRight());
            }

            // store the result for that room
            endResult.getDispatched().add(result);

            // remove teams dispatched for other rooms
            teamsToDispatch.removeAll(result.getTeams());
        }

        // store teams not dispatched
        endResult.setNotAbleToDispatch(teamsToDispatch);
        return endResult;
    }

    static TeamRoomDispatchScenario findBestDispatchScenarioForRoom(LinkedList<Team> teamsToAllocate, String roomName, int roomSize) {
        // if we split a team of 20 in 2, the penalty is then 0, it's OK if you have 4 other colleagues next to you
        final int maxPenalty = 10;

        List<TeamRoomDispatchScenario> results;

        // special case: fewer people than the room, only 1 scenario
        int totalTeamSize = teamsToAllocate.stream().map(Team::getSize).reduce(0, Integer::sum);
        if (totalTeamSize <= roomSize) {
            return new TeamRoomDispatchScenario(roomSize, roomName, 0, new LinkedList<>(teamsToAllocate));
        } else {
            // find all possible combination to put teams in that room
            results = findAllDispatchScenarioForRoom(teamsToAllocate, roomName, roomSize, new LinkedList<>());
        }

        // some combination can exceed the room size
        // sorted by size (smaller to bigger)
        results = results.stream().sorted((o1, o2) -> {
            int result = Integer.compare(o1.getTotalSize(), o2.getTotalSize());
            if (result == 0) {
                return Integer.compare(o1.getTeams().size(), o2.getTeams().size());
            } else {
                return result;
            }
        }).toList();

        // if the first one (smallest) fit exactly the room size, return it
        Optional<TeamRoomDispatchScenario> optionalFirst = results.stream().findFirst();
        if (optionalFirst.isEmpty()) {
            return null;
        }
        TeamRoomDispatchScenario first = optionalFirst.get();
        if (first.getTotalSize() == roomSize) {
            return first;
        }

        // else, split the last team in 2 (current room and next one) and find the best split scenario based on penalty
        for (TeamRoomDispatchScenario r : results) {
            // split the last Team and compute the penalty of it
            // having 1 person alone is the worst scenario
            // but having a team of 20 split in half is OK
            Team last = r.getTeams().get(r.getTeams().size() - 1);
            int spaceLeft = roomSize - (r.getTotalSize() - last.getSize());

            Pair<Team, Team> teamSplit = splitTeam(last, spaceLeft);

            int smallestGroup = Math.min(teamSplit.getLeft().getSize(), teamSplit.getRight().getSize());
            int penalty = Math.max(maxPenalty - smallestGroup, 0);

            r.setScore(r.getScore() - penalty);
        }

        // how many teams are a split
        long totalSplitTeam = teamsToAllocate.stream().filter(Team::isSplitTeam).count();

        String[] teamsName = results.stream()
                .flatMap((Function<TeamRoomDispatchScenario, Stream<Team>>) t -> t.getTeams().stream())
                .map(Team::getName)
                .toArray(String[]::new);

        System.out.println("["+String.join(",", teamsName)+"]");

        // get the combination with the best score, that includes a team previously split from previous room
        Optional<TeamRoomDispatchScenario> optionalResult = results.stream().filter(r -> r.getTeams().stream().filter(Team::isSplitTeam).count() == totalSplitTeam).sorted((o1, o2) -> Comparator.<Integer>reverseOrder().compare(o1.getScore(), o2.getScore())).findFirst();

        if (optionalResult.isEmpty()) {
            return null;
        }
        return optionalResult.get();
    }

    static List<TeamRoomDispatchScenario> findAllDispatchScenarioForRoom(LinkedList<Team> teamsToAllocate, String roomName, int roomSize, LinkedList<Team> teamsAllocated) {

        // if we already used all desks of the room, we stop here
        int totalNumberPeopleInTheRoom = teamsAllocated.stream().map(Team::getSize).reduce(0, Integer::sum);
        if (totalNumberPeopleInTheRoom >= roomSize) {
            return List.of(new TeamRoomDispatchScenario(roomSize, roomName, 0, teamsAllocated));
        }

        List<TeamRoomDispatchScenario> possibleScenarios = new ArrayList<>();

        // for each teams to allocate, add the team in the room and add the next ones of the list to be allocated
        for (int i = 0; i < teamsToAllocate.size(); i++) {
            // new list of teams for the room
            LinkedList<Team> newTeamsAllocated = new LinkedList<>(teamsAllocated);
            Team n = teamsToAllocate.get(i);
            newTeamsAllocated.add(n);

            // new list of teams to be remaining
            LinkedList<Team> remaining = new LinkedList<>();
            for (int j = i + 1; j < teamsToAllocate.size(); j++) remaining.add(teamsToAllocate.get(j));

            // get all scenarios based on that
            List<TeamRoomDispatchScenario> result = findAllDispatchScenarioForRoom(remaining, roomName, roomSize, newTeamsAllocated);
            for (TeamRoomDispatchScenario r : result) {
                totalNumberPeopleInTheRoom = r.getTotalSize();
                // if the room is filled, add the scenario to the result
                if (totalNumberPeopleInTheRoom >= roomSize) possibleScenarios.add(r);
            }

        }

        return possibleScenarios;
    }

    static List<List<String>> heapPermutation(List<String> teams) {
        return heapPermutation(teams.toArray(new String[0]), teams.size());
    }

    // Generating permutation using Heap Algorithm
    static List<List<String>> heapPermutation(String[] teams, int size)
    {
        List<List<String>> allPermutations = new ArrayList<>();

        // if size becomes 1 then prints the obtained
        // permutation
        if (size == 1)
            return List.of(Arrays.asList(teams));

        for (int i = 0; i < size; i++) {
            allPermutations.addAll(heapPermutation(Arrays.copyOf(teams, teams.length), size - 1));

            // if size is odd, swap 0th i.e (first) and
            // (size-1)th i.e (last) element
            if (size % 2 == 1) {
                String temp = teams[0];
                teams[0] = teams[size - 1];
                teams[size - 1] = temp;
            }

            // If size is even, swap ith
            // and (size-1)th i.e last element
            else {
                String temp = teams[i];
                teams[i] = teams[size - 1];
                teams[size - 1] = temp;
            }
        }

        return allPermutations;
    }

    static Pair<Team, Team> splitTeam(Team team, int size) {
        int sizeSubGroupA = size;
        int sizeSubGroupB = team.getSize() - sizeSubGroupA;

        // re-create the team but with only the members who fit
        Team teamA = new Team(team.getName() + "_A", sizeSubGroupA, team.isMandatory(), team.getManagerEmail());
        teamA.setSplitTeam(true);
        if (team.getSplitOriginalName() != null && team.getSplitOriginalName().length() > 0) {
            teamA.setSplitOriginalName(team.getSplitOriginalName());
        } else {
            teamA.setSplitOriginalName(team.getName());
        }

        // create a new "team" which is the second split of the last team, to be assigned to another room
        Team teamB = new Team(team.getName() + "_B", sizeSubGroupB, team.isMandatory(), team.getManagerEmail());
        teamB.setSplitTeam(true);
        if (team.getSplitOriginalName() != null && team.getSplitOriginalName().length() > 0) {
            teamB.setSplitOriginalName(team.getSplitOriginalName());
        } else {
            teamB.setSplitOriginalName(team.getName());
        }

        teamA.setMembers(team.getMembers().subList(0, sizeSubGroupA));
        teamB.setMembers(team.getMembers().subList(sizeSubGroupA, team.getSize()));

        return ImmutablePair.of(teamA, teamB);
    }
}
