package org.grozeille;

import com.opencsv.CSVWriter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;

public class App
{

    public static final String CONSOLE_SEPARATOR = "================";

    public static void main(String[] args ) {
        List<Room> rooms = ConfigUtil.parseRoomsFile("test1");
        final Integer totalSizeForAllRooms = rooms.stream().map(Room::roomSize).reduce(0, Integer::sum);


        //List<Team> teams = ConfigUtil.parseTeamsFile("test2");
        Map<Integer, List<Team>> teamsByDay = ConfigUtil.parseTeamForWeekFile("teams");

        //int day = random.nextInt(4) + 1;
        int day = 2;
        System.out.println("Day: "+day);
        //List<Team> teams = teamsByDay.get(day);

        List<Team> teams = ConfigUtil.getSampleTeamForDay(day, totalSizeForAllRooms);


        final Integer totalSizeTeams = teams.stream().map(Team::getSize).reduce(0, Integer::sum);

        Map<String, Room> roomsByName = rooms.stream().collect(Collectors.toMap(Room::getName, Function.identity()));


        System.out.println(CONSOLE_SEPARATOR);
        for(Room r : rooms) {
            System.out.println("Room size="+r.roomSize()+", deskGroups=["+String.join(",", Arrays.stream(r.roomGroupSizes()).map(Object::toString).toList()) +"]");
        }
        System.out.println("Team floor size="+totalSizeForAllRooms);
        System.out.println(CONSOLE_SEPARATOR);
        for(Team t : teams) {
            System.out.println("Team name=" + t.getName() + ", size=" + t.getSize() + ", mandatory=" + t.isMandatory());
        }
        System.out.println("Team total size="+totalSizeTeams);
        System.out.println(CONSOLE_SEPARATOR);


        // Random for each run ?
        //Collections.shuffle(teams);

        // first, try to identify all scenarios to fit all people on the floor with all teams as mandatory
        // consider the floor as a single room
        List<TeamRoomDispatchScenario> floorScenarios = null;
        if(totalSizeTeams <= totalSizeForAllRooms) {
            floorScenarios = new ArrayList<>();
            floorScenarios.add(new TeamRoomDispatchScenario(totalSizeForAllRooms, "floor", new ArrayList<>(teams), 0));
        }
        else {
            floorScenarios = findAllDispatchScenarioForRoom(teams, "floor", totalSizeForAllRooms, new ArrayList<>());
        }

        // number of mandatory teams we must found in each scenario
        final long nbMandatoryTeams = teams.stream().filter(Team::isMandatory).count();

        // just keep scenarios that include all mandatory teams, and scenario that fit as maximum as possible all teams (all if all teams are smaller than the room, or at least the room size
        floorScenarios = floorScenarios.stream().filter(s -> {
            long currentScenarioNbMandatoryTeams = s.getTeams().stream().filter(Team::isMandatory).count();
            return currentScenarioNbMandatoryTeams == nbMandatoryTeams;
        }).filter(s -> s.getTotalSize() >= Math.min(totalSizeTeams, totalSizeForAllRooms)).toList();

        System.out.println("Found " + floorScenarios.size() + " scenarios to fit dispatch teams on the floor, including all mandatory teams");

        int bestScenarioScore = Integer.MIN_VALUE;
        TeamDispatchScenario bestScenario = null;
        Map<String, TeamDispatchScenario> bestDeskGroupScenario = null;

        int scenarioCpt = 0;
        // for each scenario, try to dispatch teams in the rooms
        for(TeamRoomDispatchScenario floorDispatchScenario : floorScenarios) {

            scenarioCpt++;
            System.out.println(CONSOLE_SEPARATOR);
            System.out.println("Scenario: "+scenarioCpt + ", total to fit=" + floorDispatchScenario.getTotalSize()+" in floor size="+floorDispatchScenario.getRoomSize());

            int scenarioScore = 0;
            Map<String, TeamDispatchScenario> deskGroupScenario = new HashMap<>();

            // try to dispatch teams in the room and desk groups
            TeamDispatchScenario scenario = findBestDispatchScenariosForAllRooms(floorDispatchScenario.getTeams(), rooms);
            for(TeamRoomDispatchScenario r : scenario.getDispatched()) {
                System.out.println("Room(size=" + r.getRoomSize() + ", teams=("+r.getTeams()+"), score="+r.getScore()+")");

                // get the desk groups of the current room
                Room room = roomsByName.get(r.getRoomName());
                // create "virtual rooms" for each desk group
                List<Room> deskGroups = room.getDesksGroups().entrySet().stream().map(e -> new Room(e.getKey(), new LinkedHashMap<>(Map.ofEntries(
                        entry(e.getKey(), e.getValue())
                )))).toList();

                // dispatch teams in desk groups for the current room
                TeamDispatchScenario subEndResult = findBestDispatchScenariosForAllRooms(r.getTeams(), deskGroups);
                for(TeamRoomDispatchScenario sr : subEndResult.getDispatched()) {
                    System.out.println("\tDesk(size=" + sr.getRoomSize() + ", teams=(" + sr.getTeams() + "), score=" + sr.getScore() + ")");
                }
                // penalty to split inside the room is 2 times less than split across 2 rooms
                scenarioScore += subEndResult.totalScore()/2;

                deskGroupScenario.put(r.getRoomName(), subEndResult);
            }
            scenarioScore += scenario.totalScore();

            // add teams that don't fit in the floor based on floor scenarios
            List<Team> notIncludedInTheFloorScenario = new ArrayList<>(teams);
            notIncludedInTheFloorScenario.removeAll(floorDispatchScenario.getTeams());
            scenario.getNotAbleToDispatch().addAll(notIncludedInTheFloorScenario);

            System.out.println("not able to fit: "+scenario.getNotAbleToDispatch());

            System.out.println("Total score: "+scenarioScore);

            // keep only the best
            if(scenarioScore > bestScenarioScore) {
                bestScenario = scenario;
                bestScenarioScore = scenarioScore;
                bestDeskGroupScenario = deskGroupScenario;
            }
        }

        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("Best scenario");
        for(TeamRoomDispatchScenario r : bestScenario.getDispatched()) {
            System.out.println("Room(size=" + r.getRoomSize() + ", teams=(" + r.getTeams() + "), score=" + r.getScore() + ")");
            for(TeamRoomDispatchScenario sr : bestDeskGroupScenario.get(r.getRoomName()).getDispatched()) {
                System.out.println("\tDesk(size=" + sr.getRoomSize() + ", teams=(" + sr.getTeams() + "), score=" + sr.getScore() + ")");
            }
        }
        System.out.println("not able to fit: "+bestScenario.getNotAbleToDispatch());
        System.out.println("Total score: "+bestScenarioScore);

        // build the CSV to mimic the layout of the floor
        // in all rooms, and all desk groups, for all teams assigned to that desk group, assign a specific desk for each members
        List<People> peopleWithDesk = new ArrayList<>();
        Map<String, Pair<Team,People>> deskPeopleMapping = new HashMap<>();
        for(TeamRoomDispatchScenario r : bestScenario.getDispatched()) {
            Room room = roomsByName.get(r.getRoomName());
            for(TeamRoomDispatchScenario sr : bestDeskGroupScenario.get(r.getRoomName()).getDispatched()) {
                // get all desks of that desk group in that room
                List<String> desks = room.getDesksGroups().get(sr.getRoomName());
                Iterator<String> desksIterator = desks.iterator();
                // assign a desk for each people in that desk group
                for(Team teamsInDeskGroup : sr.getTeams()) {
                    for(People peopleInDeskGroup : teamsInDeskGroup.getMembers()) {
                        deskPeopleMapping.put(desksIterator.next(), ImmutablePair.of(teamsInDeskGroup, peopleInDeskGroup));
                        peopleWithDesk.add(peopleInDeskGroup);
                    }
                }
            }
        }

        // read a map of the floor
        List<String[]> output = new ArrayList<>();
        List<List<String>> floorMapping = ConfigUtil.readCsv("floor_mapping.csv");
        for(List<String> row : floorMapping) {
            List<String> outputRow = new ArrayList<>();

            for(String column: row) {
                String outputCell = "";
                if(!column.isEmpty()) {
                    Pair<Team, People> peopleInTeam = deskPeopleMapping.get(column);
                    if(peopleInTeam == null) {
                        System.out.println("Nobody assigned for desk "+column+" ?");
                    }
                    else {
                        outputCell = peopleInTeam.getLeft().getName() + " " +
                                peopleInTeam.getRight().getEmail();
                    }
                }
                outputRow.add(outputCell);
            }

            output.add(outputRow.toArray(new String[0]));
        }

        try (CSVWriter csvWriter = new CSVWriter(new FileWriter("target/output.csv"))) {
            csvWriter.writeAll(output);
        } catch (IOException e) {
            throw new RuntimeException("Not able to write output CSV", e);
        }


        output = new ArrayList<>();
        for(Team t : teams) {
            for(People p : t.getMembers()) {
                String outputCell = t.getName() + " " + p.getEmail();
                output.add(new String[]{outputCell});
            }
        }
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter("target/allpeople.csv"))) {
            csvWriter.writeAll(output);
        } catch (IOException e) {
            throw new RuntimeException("Not able to write output CSV", e);
        }

        List<People> allPeople = teams.stream().flatMap((Function<Team, Stream<People>>) team -> team.getMembers().stream()).toList();
        allPeople = new ArrayList<>(allPeople);
        allPeople.removeAll(peopleWithDesk);
        for(People p : allPeople) {
            System.out.println("People without desk: "+p);
        }
    }

    static TeamDispatchScenario findBestDispatchScenariosForAllRooms(List<Team> teams, List<Room> rooms) {
        ArrayList<Team> teamsToDispatch = new ArrayList<>(teams);

        TeamDispatchScenario endResult = new TeamDispatchScenario();
        endResult.setDispatched(new ArrayList<>());

        // try to fit people in all "rooms", starting with the smallest one to the biggest one
        //rooms = rooms.stream().sorted(Comparator.comparingInt(Room::roomSize)).toList();
        // TODO: don't do it for desk groups to keep the right order

        for(Room room : rooms) {

            TeamRoomDispatchScenario result = findBestDispatchScenarioForRoom(teamsToDispatch, room.getName(), room.roomSize());
            if(result == null) {
                break;
            }
            Integer sum = result.getTotalSize();

            // if not all teams fit in this room, split the last team
            if(sum > room.roomSize()) {
                Team lastTeam = result.getTeams().get(result.getTeams().size() - 1);
                int spaceLeftWithoutLastTeam = room.roomSize() - (result.getTotalSize() - lastTeam.getSize());

                // split the last team, the second part will be used to dispatch to another room
                Pair<Team, Team> teamSplit = splitTeam(lastTeam, spaceLeftWithoutLastTeam);
                result.getTeams().remove(lastTeam);
                result.getTeams().add(teamSplit.getLeft());
                teamsToDispatch.remove(lastTeam);
                teamsToDispatch.add(teamSplit.getRight());
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

    static TeamRoomDispatchScenario findBestDispatchScenarioForRoom(List<Team> teamsToAllocate, String roomName, int roomSize) {
        // if we split a team of 20 in 2, the penalty is then 0, it's OK if you have 4 other colleagues next to you
        final int maxPenalty = 10;

        List<TeamRoomDispatchScenario> results;

        // special case: fewer people than the room, only 1 scenario
        int totalTeamSize = teamsToAllocate.stream().map(Team::getSize).reduce(0, Integer::sum);
        if(totalTeamSize <= roomSize) {
            return new TeamRoomDispatchScenario(roomSize, roomName, new ArrayList<>(teamsToAllocate), 0);
        }
        else {
            // find all possible combination to put teams in that room
            results = findAllDispatchScenarioForRoom(teamsToAllocate, roomName, roomSize, new ArrayList<>());
        }

        // some combination can exceed the room size
        // sorted by size (smaller to bigger)
        results = results.stream()
                .sorted((o1, o2) -> {
                    int result = Integer.compare(o1.getTotalSize(), o2.getTotalSize());
                    if (result == 0) {
                        return Integer.compare(o1.getTeams().size(), o2.getTeams().size());
                    } else {
                        return result;
                    }
                })
                .toList();

        // if the first one (smallest) fit exactly the room size, return it
        Optional<TeamRoomDispatchScenario> optionalFirst = results.stream().findFirst();
        if(optionalFirst.isEmpty()) {
            return null;
        }
        TeamRoomDispatchScenario first = optionalFirst.get();
        if(first.getTotalSize() == roomSize) {
            return first;
        }

        // else, split the last team in 2 (current room and next one) and find the best split scenario based on penalty
        for(TeamRoomDispatchScenario r : results) {
            // split the last Team and compute the penalty of it
            // having 1 person alone is the worst scenario
            // but having a team of 20 split in half is OK
            Team last = r.getTeams().get(r.getTeams().size() - 1);
            int spaceLeft = roomSize - (r.getTotalSize() - last.getSize());

            Pair<Team, Team> teamSplit = splitTeam(last, spaceLeft);

            int smallestGroup = Math.min(teamSplit.getLeft().getSize(), teamSplit.getRight().getSize());
            int penalty = Math.max(maxPenalty - smallestGroup, 0);

            r.setScore(-penalty);
        }

        // get the combination with the best score
        TeamRoomDispatchScenario result = results.stream()
                .sorted((o1, o2) -> Comparator.<Integer>reverseOrder().compare(o1.getScore(), o2.getScore()))
                .findFirst().get();
        return result;
    }

    static List<TeamRoomDispatchScenario> findAllDispatchScenarioForRoom(List<Team> teamsToAllocate, String roomName, int roomSize, ArrayList<Team> teamsAllocated) {

        // if we already used all desks of the room, we stop here
        int totalNumberPeopleInTheRoom = teamsAllocated.stream().map(Team::getSize).reduce(0, Integer::sum);
        if (totalNumberPeopleInTheRoom >= roomSize) {
            return List.of(new TeamRoomDispatchScenario(roomSize, roomName, teamsAllocated, 0));
        }

        List<TeamRoomDispatchScenario> possibleScenarios = new ArrayList<>();

        // for each teams to allocate, add the team in the room and add the next ones of the list to be allocated
        for(int i=0;i<teamsToAllocate.size();i++) {
            // new list of teams for the room
            ArrayList<Team> newTeamsAllocated = new ArrayList<>(teamsAllocated);
            Team n = teamsToAllocate.get(i);
            newTeamsAllocated.add(n);

            // new list of teams to be remaining
            ArrayList<Team> remaining = new ArrayList<>();
            for (int j=i+1; j<teamsToAllocate.size();j++) remaining.add(teamsToAllocate.get(j));

            // get all scenarios based on that
            List<TeamRoomDispatchScenario> result = findAllDispatchScenarioForRoom(remaining, roomName, roomSize, newTeamsAllocated);
            for (TeamRoomDispatchScenario r : result) {
                totalNumberPeopleInTheRoom = r.getTotalSize();
                // if the room is filled, add the scenario to the result
                if (totalNumberPeopleInTheRoom >= roomSize)
                    possibleScenarios.add(r);
            }

        }

        return possibleScenarios;
    }

    static Pair<Team, Team> splitTeam(Team team, int size) {
        int sizeSubGroupA = size;
        int sizeSubGroupB = team.getSize() - sizeSubGroupA;

        // re-create the team but with only the members who fit
        Team teamA = new Team(team.getName()+"_A", sizeSubGroupA, team.isMandatory(), team.getManagerEmail());

        // create a new "team" which is the second split of the last team, to be assigned to another room
        Team teamB = new Team(team.getName()+"_B", sizeSubGroupB, team.isMandatory(), team.getManagerEmail());

        teamA.setMembers(team.getMembers().subList(0, sizeSubGroupA));
        teamB.setMembers(team.getMembers().subList(sizeSubGroupA, team.getSize()));

        if(teamA.getSize() != teamA.getMembers().size()) {
            System.out.println("WTF");
        }
        if(teamB.getSize() != teamB.getMembers().size()) {
            System.out.println("WTF");
        }

        return ImmutablePair.of(teamA, teamB);
    }
}
