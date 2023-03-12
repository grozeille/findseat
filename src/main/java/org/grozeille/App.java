package org.grozeille;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public class App
{
    public static void main( String[] args ) {
        List<Room> rooms = ConfigUtil.parseRoomsFile();
        List<Team> teams = ConfigUtil.parseTeamsFile();
        Map<String, Room> roomsByName = rooms.stream().collect(Collectors.toMap(Room::getName, Function.identity()));


        // test to reduce second room from 18 to 10
        roomsByName.get("B").getDesksGroups().get("BB").remove(0);
        roomsByName.get("B").getDesksGroups().get("BB").remove(0);
        roomsByName.get("B").getDesksGroups().get("BB").remove(0);
        roomsByName.get("B").getDesksGroups().get("BB").remove(0);
        roomsByName.get("B").getDesksGroups().get("BC").remove(0);
        roomsByName.get("B").getDesksGroups().get("BC").remove(0);
        roomsByName.get("B").getDesksGroups().get("BC").remove(0);
        roomsByName.get("B").getDesksGroups().get("BC").remove(0);

        System.out.println("================");
        for(Room r : rooms) {
            System.out.println("Room size="+r.roomSize()+", deskGroups=["+String.join(",", Arrays.stream(r.roomGroupSizes()).map(Object::toString).toList()) +"]");
        }
        final Integer totalSizeForAllRooms = rooms.stream().map(Room::roomSize).reduce(0, Integer::sum);
        System.out.println("Team floor size="+totalSizeForAllRooms);
        System.out.println("================");
        for(Team t : teams) {
            System.out.println("Team name=" + t.getName() + ", size=" + t.getSize() + ", mandatory=" + t.isMandatory());
        }
        final Integer totalSizeTeams = teams.stream().map(Team::getSize).reduce(0, Integer::sum);
        System.out.println("Team total size="+totalSizeTeams);
        System.out.println("================");


        // Random for each run ?
        //Collections.shuffle(teams);

        // first, try to identify all scenarios to fit all people on the floor with all teams as mandatory
        // consider the floor as a single room
        List<TeamRoomDispatchScenario> allFloorDispatchScenario = findAllTeamCombinationForTheRoom(teams, "floor", totalSizeForAllRooms, new ArrayList<>());

        // number of mandatory teams we must found in each scenario
        final long nbMandatoryTeams = teams.stream().filter(Team::isMandatory).count();

        // just keep scenarios that include all mandatory teams, and scenario that fit as maximum as possible all teams (all if all teams are smaller than the room, or at least the room size
        allFloorDispatchScenario = allFloorDispatchScenario.stream().filter(s -> {
            long currentScenarioNbMandatoryTeams = s.getTeams().stream().filter(Team::isMandatory).count();
            return currentScenarioNbMandatoryTeams == nbMandatoryTeams;
        }).filter(s -> s.getTotalSize() >= Math.min(totalSizeTeams, totalSizeForAllRooms)).toList();

        System.out.println("Found " + allFloorDispatchScenario.size() + " scenarios to fit dispatch teams on the floor, including all mandatory teams");

        TeamDispatchScenario bestScenario = null;
        int bestScenarioScore = -100000;

        // TODO: to the score, keep best scenario that fit the maximum number of people
        // TODO: display people who can't fit

        int scenarioCpt = 0;

        // for each scenario, try to dispatch teams in the rooms
        for(TeamRoomDispatchScenario floorDispatchScenario : allFloorDispatchScenario) {

            scenarioCpt++;
            System.out.println("================");
            System.out.println("Scenario: "+scenarioCpt + ", total to fit=" + floorDispatchScenario.getTotalSize()+" in floor size="+floorDispatchScenario.getRoomSize());

            int scenarioScore = 0;

            if(scenarioCpt == 2) {
                System.out.println("DEBUG");
            }

            // try to dispatch teams in the room and desk groups
            TeamDispatchScenario scenario = dispatchTeamsToRooms(floorDispatchScenario.getTeams(), rooms);
            for(TeamRoomDispatchScenario r : scenario.getDispatched()) {
                System.out.println("Room(size=" + r.getRoomSize() + ", teams=("+r.getTeams()+"), score="+r.getScore()+")");

                // get the desk groups of the current room
                Room room = roomsByName.get(r.getRoomName());
                // create "virtual rooms" for each desk group
                List<Room> deskGroups = room.getDesksGroups().entrySet().stream().map(e -> new Room(e.getKey(), new LinkedHashMap<>(Map.ofEntries(
                        entry(e.getKey(), e.getValue())
                )))).toList();

                // dispatch teams in desk groups for the current room
                TeamDispatchScenario subEndResult = dispatchTeamsToRooms(r.getTeams(), deskGroups);
                for(TeamRoomDispatchScenario sr : subEndResult.getDispatched()) {
                    System.out.println("\tDesk(size=" + sr.getRoomSize() + ", teams=(" + sr.getTeams() + "), score=" + sr.getScore() + ")");
                }
                scenarioScore += subEndResult.totalScore();
                System.out.println("\tnot able to fit: "+subEndResult.getNotAbleToDispatch());
            }
            scenarioScore += scenario.totalScore();
            System.out.println("not able to fit: "+scenario.getNotAbleToDispatch());

            System.out.println("Total score: "+scenarioScore);

            // keep only the best
            if(scenarioScore > bestScenarioScore) {
                bestScenario = scenario;
                bestScenarioScore = scenarioScore;
            }
        }

        System.out.println("================");
        System.out.println("Best scenario");
        System.out.println(bestScenario);

    }

    private static TeamDispatchScenario dispatchTeamsToRooms(List<Team> teams, List<Room> rooms) {
        ArrayList<Team> teamsToDispatch = new ArrayList<>(teams);

        TeamDispatchScenario endResult = new TeamDispatchScenario();
        endResult.setDispatched(new ArrayList<>());

        // try to fit people in all "rooms", starting with the smallest one to the biggest one
        rooms = rooms.stream().sorted(Comparator.comparingInt(Room::roomSize)).toList();

        for(Room room : rooms) {

            if(room.getName().equals("C")) {
                System.out.println("DEBUG");
            }

            TeamRoomDispatchScenario result = findBestTeamCombinationForTheRoom(new ArrayList<>(teamsToDispatch), room.getName(), room.roomSize());
            Integer sum = result.getTotalSize();

            // if not all teams fit in this room, split the last team
            if(sum > room.roomSize()) {
                Team lastTeam = result.getTeams().get(result.getTeams().size() - 1);
                int sizeSubGroupA = room.roomSize() - (result.getTotalSize() - lastTeam.getSize());
                int sizeSubGroupB = lastTeam.getSize() - sizeSubGroupA;

                // re-create the team but with only the members
                Team lastTeamA = new Team(lastTeam.getName()+"_A", sizeSubGroupA, lastTeam.isMandatory());
                result.getTeams().remove(result.getTeams().size() - 1);
                result.getTeams().add(lastTeamA);

                // create a new "team" which is the second split of the last team, to be assigned to another room
                Team lastTeamB = new Team(lastTeam.getName()+"_B", sizeSubGroupB, lastTeam.isMandatory());
                teamsToDispatch.add(lastTeamB);
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

    static List<TeamRoomDispatchScenario> findAllTeamCombinationForTheRoom(List<Team> teamsToAllocate, String roomName, int roomSize, ArrayList<Team> teamsAllocated) {

        int totalNumberPeopleInTheRoom = teamsAllocated.stream().map(Team::getSize).reduce(0, Integer::sum);

        // exact match or too high
        if (totalNumberPeopleInTheRoom >= roomSize) {
            //String numbersString = partial.stream().map(Object::toString).collect(Collectors.joining("+"));
            //System.out.println(numbersString + "=" + sum);
            return List.of(new TeamRoomDispatchScenario(roomSize, roomName, teamsAllocated, 0));
        }

        List<TeamRoomDispatchScenario> bestMatches = new ArrayList<>();


        for(int i=0;i<teamsToAllocate.size();i++) {
            ArrayList<Team> remaining = new ArrayList<>();
            Team n = teamsToAllocate.get(i);

            for (int j=i+1; j<teamsToAllocate.size();j++) remaining.add(teamsToAllocate.get(j));

            ArrayList<Team> newTeamsAllocated = new ArrayList<>(teamsAllocated);
            newTeamsAllocated.add(n);

            List<TeamRoomDispatchScenario> result = findAllTeamCombinationForTheRoom(remaining, roomName, roomSize, newTeamsAllocated);
            for(TeamRoomDispatchScenario r : result) {
                totalNumberPeopleInTheRoom = r.getTotalSize();
                // exact match or too high
                if (totalNumberPeopleInTheRoom >= roomSize)
                    bestMatches.add(r);
            }

        }

        return bestMatches;
    }

    static TeamRoomDispatchScenario findBestTeamCombinationForTheRoom(ArrayList<Team> teamsToAllocate, String roomName, int roomSize) {
        final int maxPenalty = 10;

        List<TeamRoomDispatchScenario> results;

        // special case: fewer people than the room, only 1 scenario
        int totalTeamSize = teamsToAllocate.stream().map(Team::getSize).reduce(0, Integer::sum);
        if(totalTeamSize <= roomSize) {
            return new TeamRoomDispatchScenario(roomSize, roomName, teamsToAllocate, 0);
        }
        else {
            // find all possible combination to put teams in that room
            results = findAllTeamCombinationForTheRoom(teamsToAllocate, roomName, roomSize, new ArrayList<>());
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
        Optional<TeamRoomDispatchScenario> optionalFirst = results.stream().findFirst();
        if(optionalFirst.isEmpty()) {
            System.out.println("DEBUG");
        }
        TeamRoomDispatchScenario first = optionalFirst.get();

        // if best match found (fit exactly the room size), return it
        if(first.getTotalSize() == roomSize) {
            return first;
        }

        // else, split the last team in half and find the best scenario
        for(TeamRoomDispatchScenario r : results) {
            // split the last Team and compute the penalty of it
            // having 1 person alone is the worst scenario
            // but having a team of 10 split in half is OK
            Team last = r.getTeams().get(r.getTeams().size() - 1);
            int sizeSubGroupA = roomSize - (r.getTotalSize() - last.getSize());
            int sizeSubGroupB = last.getSize() - sizeSubGroupA;
            int min = Math.min(sizeSubGroupA, sizeSubGroupB);
            int penalty = Math.max(maxPenalty - min, 0);

            r.setScore(-penalty);

            //String numbersString = r.getTeams().stream().map(Object::toString).collect(Collectors.joining("+"));
            int sum = r.getTotalSize();
            //System.out.println("target: " + target + " " + r.getTeams() + "=" + sum + " score: "+ r.getScore());
        }

        // get the combination with the best score
        TeamRoomDispatchScenario result = results.stream()
                .sorted((o1, o2) -> Comparator.<Integer>reverseOrder().compare(o1.getScore(), o2.getScore()))
                .findFirst().get();
        return result;
    }

}
