package org.grozeille;

import java.util.*;

public class App
{
    public static void main( String[] args ) {

        List<Team> teams = new ArrayList<>();
        /*
        teams.add(new Team("A", 6, false));
        teams.add(new Team("B", 3, false));
        teams.add(new Team("C", 9, false));
        teams.add(new Team("D", 9, false));
        teams.add(new Team("E", 20, false));
        teams.add(new Team("F", 12, false));
        teams.add(new Team("G", 12, false));
        teams.add(new Team("H", 9, false));
        teams.add(new Team("I", 17, false));
        teams.add(new Team("J", 20, false));
        teams.add(new Team("K", 8, false));
        teams.add(new Team("L", 5, false));
        teams.add(new Team("M", 15, false));
        teams.add(new Team("N", 6, false));
        */

        teams.add(new Team("A", 2, false));
        teams.add(new Team("B", 2, false));
        teams.add(new Team("C", 1, false));
        teams.add(new Team("D", 2, false));
        teams.add(new Team("E", 3, false));
        teams.add(new Team("F", 9, true));
        teams.add(new Team("G", 6, false));
        teams.add(new Team("H", 7, true));
        teams.add(new Team("I", 1, false));
        teams.add(new Team("J", 7, false));
        teams.add(new Team("K", 11, true));
        teams.add(new Team("L", 6, false));
        teams.add(new Team("M", 6, false));

        // Random for each run ?
        Collections.shuffle(teams);

        Integer[] roomSizes = new Integer[]{6, 18, 39};
        // 6 = 3 + 3
        // 18 = 3 + 6 + 6 + 3
        // 39 = 5 + 10 + 10 + 9 + 5
        //Integer[] roomSizes = new Integer[]{6, 10, 39};


        List<TeamDispatchScenario> teamDispatchScenarios = new ArrayList<>();

        // first, try to identify all scenarios to fit all people on the floor with all teams as mandatory
        // consider the floor as a single room
        List<TeamRoomDispatchScenario> allTeamCombinationForTheFloor = findAllTeamCombinationForTheRoom(teams, Arrays.stream(roomSizes).reduce(0, Integer::sum), new ArrayList<>());
        long nbMandatoryTeams = teams.stream().filter(t -> t.isMandatory()).count();
        for(TeamRoomDispatchScenario floorResult : allTeamCombinationForTheFloor) {
            long resultNbMandatoryTeams = floorResult.getTeams().stream().filter(t -> t.isMandatory()).count();
            if(resultNbMandatoryTeams == nbMandatoryTeams) {

                int scenarioScore = 0;

                // try to dispatch teams in the room and desks
                TeamDispatchScenario scenario = dispatchTeamsToRooms(floorResult.getTeams(), roomSizes);
                for(TeamRoomDispatchScenario r : scenario.getDispatched()) {
                    System.out.println("Room(size=" + r.getRoomSize() + ", teams=("+r.getTeams()+"), score="+r.getScore()+")");

                    Integer[] subRoomSizes;
                    if (r.getRoomSize() == roomSizes[0]) {
                        subRoomSizes = new Integer[]{3, 3};
                    } else if (r.getRoomSize() == roomSizes[1]) {
                        subRoomSizes = new Integer[]{3, 6, 6, 3};
                        //subRoomSizes = new Integer[]{4, 4, 2};
                    } else { // rs == roomSizes[2]
                        subRoomSizes = new Integer[]{5, 10, 10, 9, 5};
                    }
                    TeamDispatchScenario subEndResult = dispatchTeamsToRooms(r.getTeams(), subRoomSizes);
                    for(TeamRoomDispatchScenario sr : subEndResult.getDispatched()) {
                        System.out.println("\tDesk(size=" + sr.getRoomSize() + ", teams=(" + sr.getTeams() + "), score=" + sr.getScore() + ")");
                    }
                    scenarioScore += subEndResult.totalScore();
                    System.out.println("\tnot able to fit: "+subEndResult.getNotAbleToDispatch());
                }
                scenarioScore += scenario.totalScore();
                System.out.println("not able to fit: "+scenario.getNotAbleToDispatch());

                System.out.println("Total score: "+scenarioScore);
            }
        }


    }

    private static TeamDispatchScenario dispatchTeamsToRooms(List<Team> teams, Integer[] roomSizes) {
        ArrayList<Team> teamsToDispatch = new ArrayList<>(teams);

        TeamDispatchScenario endResult = new TeamDispatchScenario();
        endResult.setDispatched(new ArrayList<>());

        // try to fit people in all "rooms", starting with the smallest one to the biggest one
        for(Integer rs : Arrays.stream(roomSizes).sorted().toList()) {
            TeamRoomDispatchScenario result = findBestTeamCombinationForTheRoom(teamsToDispatch, rs);
            Integer sum = result.getTotalSize();

            // if not all teams fit in this room, split the last team
            if(sum > rs) {
                Team lastTeam = result.getTeams().get(result.getTeams().size() - 1);
                int sizeSubGroupA = rs - (result.getTotalSize() - lastTeam.getSize());
                int sizeSubGroupB = lastTeam.getSize() - sizeSubGroupA;

                lastTeam.setSize(sizeSubGroupA);
                sum = result.getTotalSize();

                // create a new "team" which is the second split of the last team, to be assigned to another room
                Team splitLastTeam = new Team(lastTeam.getName()+"bis", sizeSubGroupB, lastTeam.isMandatory());
                teamsToDispatch.add(splitLastTeam);
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

    static List<TeamRoomDispatchScenario> findAllTeamCombinationForTheRoom(List<Team> teamsToAllocate, int roomSize, ArrayList<Team> teamsAllocated) {

        int totalNumberPeopleInTheRoom = teamsAllocated.stream().map(Team::getSize).reduce(0, Integer::sum);

        // exact match or too high
        if (totalNumberPeopleInTheRoom >= roomSize) {
            //String numbersString = partial.stream().map(Object::toString).collect(Collectors.joining("+"));
            //System.out.println(numbersString + "=" + sum);
            return List.of(new TeamRoomDispatchScenario(roomSize, teamsAllocated, 0));
        }

        List<TeamRoomDispatchScenario> bestMatches = new ArrayList<>();


        for(int i=0;i<teamsToAllocate.size();i++) {
            ArrayList<Team> remaining = new ArrayList<>();
            Team n = teamsToAllocate.get(i);

            for (int j=i+1; j<teamsToAllocate.size();j++) remaining.add(teamsToAllocate.get(j));

            ArrayList<Team> newTeamsAllocated = new ArrayList<>(teamsAllocated);
            newTeamsAllocated.add(n);

            List<TeamRoomDispatchScenario> result = findAllTeamCombinationForTheRoom(remaining,roomSize,newTeamsAllocated);
            for(TeamRoomDispatchScenario r : result) {
                totalNumberPeopleInTheRoom = r.getTotalSize();
                // exact match or too high
                if (totalNumberPeopleInTheRoom >= roomSize)
                    bestMatches.add(r);
            }

        }

        return bestMatches;
    }

    static TeamRoomDispatchScenario findBestTeamCombinationForTheRoom(ArrayList<Team> teamsToAllocate, int roomSize) {
        final int maxPenalty = 10;

        // find all possible combination to put teams in that room
        List<TeamRoomDispatchScenario> results = findAllTeamCombinationForTheRoom(teamsToAllocate, roomSize, new ArrayList<>());

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
