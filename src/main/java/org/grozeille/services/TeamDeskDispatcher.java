package org.grozeille.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.grozeille.model.*;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Slf4j
public class TeamDeskDispatcher {

    private static String CONSOLE_SEPARATOR = "======================";

    public WeekDispatchResult dispatch(Map<Integer, List<Team>> teamsByDay, List<Room> rooms) {
        long startupTime = System.currentTimeMillis();

        WeekDispatchResult result = new WeekDispatchResult();

        // all rooms by name
        Map<String, Room> roomsByName = rooms.stream().collect(Collectors.toMap(Room::getName, Function.identity()));

        // for each day of the week, find the best dispatch scenario to assign desks to everyone
        for(int day = 1; day <= 5; day++) {

            LinkedList<Team> teams = new LinkedList<>(teamsByDay.get(day));
            teamsByDay.put(day, teams);

            // find the best dispatch scenario for the floor, with the best dispatch scenario for each desk group
            Map<String, TeamDispatchScenario> bestDeskGroupScenario = findBestScenarioByRoom(day, rooms, teams);

            // Assign a desk to all people
            final List<People> peopleWithDesk = new ArrayList<>();
            Map<String, PeopleWithTeam> deskPeopleMapping = new HashMap<>();

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
                                deskPeopleMapping.put(desksIterator.next(), new PeopleWithTeam(peopleInDeskGroup, teamsInDeskGroup));
                                peopleWithDesk.add(peopleInDeskGroup);
                            }
                        }
                    }
                }
            }

            // find all people without desk
            List<PeopleWithTeam> peopleWithoutDesks = teams.stream()
                    .flatMap(t -> {
                        final Team teamClone = (Team)t.clone();
                        teamClone.getMembers().removeAll(peopleWithDesk);
                        return teamClone.getMembers().stream().map(p -> new PeopleWithTeam(p, teamClone));
                    })
                    .toList();

            // store the result of the day
            DayDispatchResult dayDispatchResult = new DayDispatchResult();
            dayDispatchResult.setNotAbleToDispatch(peopleWithoutDesks);
            dayDispatchResult.setDeskAssignedToPeople(deskPeopleMapping);

            boolean mandatoryNotDispatched = dayDispatchResult.getNotAbleToDispatch().stream()
                    .anyMatch(p -> p.getPeople().getReservationType().equals(ReservationType.MANDATORY));

            boolean anyOptional = dayDispatchResult.getDeskAssignedToPeople().values().stream()
                    .anyMatch(p -> p.getPeople().getReservationType().equals(ReservationType.OPTIONAL));

            boolean normalNotDispatched = dayDispatchResult.getNotAbleToDispatch().stream()
                    .anyMatch(p -> p.getPeople().getReservationType().equals(ReservationType.NORMAL));

            if(mandatoryNotDispatched || (anyOptional && normalNotDispatched)) {
                throw new RuntimeException("This is a bug");
            }

            result.getDispatchPerDayOfWeek().put(day, dayDispatchResult);
        }

        Duration totalTime = Duration.ofMillis(System.currentTimeMillis()-startupTime);
        log.info("Dispatch terminated in "+ totalTime.toHours()+":"+totalTime.toMinutes()+":"+totalTime.toSecondsPart()+":"+totalTime.toMillisPart());

        return result;
    }

    private Pair<LinkedList<Team>, List<Team>> tryToFillTheFloor(LinkedList<Team> teams, Integer totalSizeForAllRooms) {
        LinkedList<Team> teamsToDispatch = new LinkedList<>();
        List<Team> notAbleDispatch = new ArrayList<>();

        // clone all teams because we are going to modify the members
        LinkedList<Team> initialTeams = teams;
        teams = new LinkedList<>(teams.stream().map(t-> (Team)t.clone()).toList());

        final Integer totalSizeTeams = teams.stream().mapToInt(Team::size).sum();

        if(totalSizeTeams <= totalSizeForAllRooms) {
            teamsToDispatch = teams;
        } else {

            // if not enough space, try to fit people by priority:
            // 1. Mandatory people
            // 2. Additional people who are part of a team with Mandatory people to encourage collaboration
            // 3. Other additional people
            // 4. Optional people who are part of a team with Mandatory people to encourage collaboration
            // 5. Optional people who are part of a team with Additional people to encourage collaboration
            // 6. Other optional people

            // first, we need to split the members with not the same reservation type in the same team
            // in order to have only mandatory people to dispatch first
            List<Team> additionalTeams = new ArrayList<>();
            Map<String, List<People>> optionalPeopleInAdditionalTeams = new HashMap<>();
            Map<String, Team> additionalTeamWithOptionalPeople = new HashMap<>();
            List<Team> optionalTeams = new ArrayList<>();
            Map<String, List<People>> additionalPeopleInMandatoryTeams = new HashMap<>();
            Map<String, Team> mandatoryTeamWithAdditionalPeople = new HashMap<>();
            Map<String, List<People>> optionalPeopleInMandatoryTeams = new HashMap<>();
            Map<String, Team> mandatoryTeamWithOptionalPeople = new HashMap<>();
            for (Team team : teams) {
                long countMandatory = team.getMembers().stream().filter(p -> p.getReservationType().equals(ReservationType.MANDATORY)).count();
                long countAdditional = team.getMembers().stream().filter(p -> p.getReservationType().equals(ReservationType.NORMAL)).count();
                long countOptional = team.getMembers().stream().filter(p -> p.getReservationType().equals(ReservationType.OPTIONAL)).count();
                if (countMandatory > 0) {
                    // if the team contains non-mandatory members, keep them for later
                    if (countMandatory != team.getMembers().size()) {
                        if (countAdditional > 0) {
                            additionalPeopleInMandatoryTeams.put(team.getName(), team.getMembers().stream().filter(p -> p.getReservationType().equals(ReservationType.NORMAL)).toList());
                            team.getMembers().removeAll(additionalPeopleInMandatoryTeams.get(team.getName()));
                            mandatoryTeamWithAdditionalPeople.put(team.getName(), team);
                        }

                        if (countOptional > 0) {
                            optionalPeopleInMandatoryTeams.put(team.getName(), team.getMembers().stream().filter(p -> p.getReservationType().equals(ReservationType.OPTIONAL)).toList());
                            team.getMembers().removeAll(optionalPeopleInMandatoryTeams.get(team.getName()));
                            mandatoryTeamWithOptionalPeople.put(team.getName(), team);
                        }
                    }
                    teamsToDispatch.add(team);
                } else if (countAdditional > 0) {
                    // if the team contains optional members, keep them for later
                    if (countAdditional != team.getMembers().size()) {
                        if (countOptional > 0) {
                            optionalPeopleInAdditionalTeams.put(team.getName(), team.getMembers().stream().filter(p -> p.getReservationType().equals(ReservationType.OPTIONAL)).toList());
                            team.getMembers().removeAll(optionalPeopleInAdditionalTeams.get(team.getName()));
                            additionalTeamWithOptionalPeople.put(team.getName(), team);
                        }
                    }
                    additionalTeams.add(team);
                } else {
                    optionalTeams.add(team);
                }
            }

            // 1. priority is to fit all mandatory people
            final int nbMandatoryPeople = teamsToDispatch.stream()
                    .mapToInt(t -> t.getMembers().size())
                    .sum();
            int remainingDesks = totalSizeForAllRooms - nbMandatoryPeople;
            if (remainingDesks < 0) {
                throw new RuntimeException("Not enough space for mandatory teams. Floor size:" + totalSizeForAllRooms + " total mandatory people:" + nbMandatoryPeople);
            }

            // 2. try to fit all additional people
            // get in priority people of the mandatory teams
            for (Map.Entry<String, List<People>> e : additionalPeopleInMandatoryTeams.entrySet()) {
                List<People> people = e.getValue().subList(0, Math.min(e.getValue().size(), remainingDesks));
                Team team = mandatoryTeamWithAdditionalPeople.get(e.getKey());
                team.getMembers().addAll(people);
                remainingDesks -= people.size();
                if (remainingDesks == 0) {
                    break;
                }
            }

            // 3. get the additional people who are not in a team with other mandatory people
            if (remainingDesks > 0) {
                for (Team team : additionalTeams.stream().sorted(Comparator.comparing(Team::size).reversed()).toList()) {
                    List<People> people = team.getMembers().subList(0, Math.min(team.size(), remainingDesks));
                    team.setMembers(people);
                    teamsToDispatch.add(team);
                    remainingDesks -= people.size();
                    if (remainingDesks == 0) {
                        break;
                    }
                }
            }

            // 4. still have remaining desks ? add optional people
            if (remainingDesks > 0) {
                // start with optional people who are part of a team with other mandatory people
                for (Map.Entry<String, List<People>> e : optionalPeopleInMandatoryTeams.entrySet()) {
                    List<People> people = e.getValue().subList(0, Math.min(e.getValue().size(), remainingDesks));
                    Team team = mandatoryTeamWithOptionalPeople.get(e.getKey());
                    team.getMembers().addAll(people);
                    remainingDesks -= people.size();
                    if (remainingDesks == 0) {
                        break;
                    }
                }
            }

            // 5. then optional people who are part of a team with other additional people
            if (remainingDesks > 0) {
                for (Map.Entry<String, List<People>> e : optionalPeopleInAdditionalTeams.entrySet()) {
                    List<People> people = e.getValue().subList(0, Math.min(e.getValue().size(), remainingDesks));
                    Team team = additionalTeamWithOptionalPeople.get(e.getKey());
                    team.getMembers().addAll(people);
                    remainingDesks -= people.size();
                    if (remainingDesks == 0) {
                        break;
                    }
                }
            }

            // 6.at last, put the rest of the optional teams. To encourage collaboration, take the biggest team to the smallest
            if (remainingDesks > 0) {
                boolean anySkipped = false;
                for (Team team : optionalTeams.stream().sorted(Comparator.comparing(Team::size).reversed()).toList()) {
                    if (team.size() > remainingDesks) {
                        anySkipped = true;
                    } else {
                        teamsToDispatch.add(team);
                        remainingDesks -= team.size();
                    }
                    if (remainingDesks == 0) {
                        break;
                    }
                }
                // if all remaining optional teams are too big to fit into the remaining space, split the first team
                if (remainingDesks > 0 && anySkipped) {
                    Team team = optionalTeams.get(0);
                    team.setMembers(team.getMembers().subList(0, Math.min(team.getMembers().size(), remainingDesks)));
                    teamsToDispatch.add(team);
                    remainingDesks -= team.size();
                }
            }

            // build a list of people not dispatched
            List<People> allDispatchedPeople = teamsToDispatch.stream().flatMap(t -> t.getMembers().stream()).toList();
            for(Team team : initialTeams) {
                ArrayList<People> people = new ArrayList<>(team.getMembers());
                people.removeAll(allDispatchedPeople);
                if(people.size() > 0) {
                    Team newTeam = (Team)team.clone();
                    newTeam.setMembers(people);
                    notAbleDispatch.add(newTeam);
                }
            }
        }
        return Pair.of(teamsToDispatch, notAbleDispatch);
    }

    private Map<String, TeamDispatchScenario> findBestScenarioByRoom(int day, List<Room> rooms, LinkedList<Team> teams) {
        log.info(CONSOLE_SEPARATOR);
        log.info("Day: " + day);

        Integer totalSizeForAllRooms = rooms.stream().mapToInt(Room::roomSize).sum();

        final Integer totalSizeTeams = teams.stream().mapToInt(Team::size).sum();

        log.info(CONSOLE_SEPARATOR);
        for (Room r : rooms) {
            log.info("Room size=" + r.roomSize() + ", deskGroups=[" + String.join(",", Arrays.stream(r.roomGroupSizes()).map(Object::toString).toList()) + "]");
        }
        log.info("Team floor size=" + totalSizeForAllRooms);
        log.info(CONSOLE_SEPARATOR);
        for (Team t : teams) {
            log.info("Team name=" + t.getName() + ", size=" + t.size());
        }
        log.info("Team total size=" + totalSizeTeams);
        log.info(CONSOLE_SEPARATOR);


        // Random for each run ?
        //Collections.shuffle(teams);

        // if not enough people for the floor, remove the smallest rooms
        if(totalSizeTeams < totalSizeForAllRooms) {
            // try by removing the smallest room
            List<Room> roomSortedBySize = rooms.stream().sorted(Comparator.comparing(Room::roomSize)).toList();
            for(Room r : roomSortedBySize) {
                rooms.remove(r);
                totalSizeForAllRooms = rooms.stream().mapToInt(Room::roomSize).sum();
                if(totalSizeTeams.equals(totalSizeForAllRooms)) {
                    break;
                } else if(totalSizeTeams > totalSizeForAllRooms) {
                    // rollback, add the room because it's needed to fit all
                    rooms.add(r);
                    totalSizeForAllRooms = rooms.stream().mapToInt(Room::roomSize).sum();
                    break;
                }
            }

        }
        Map<String, Room> roomsByName = rooms.stream().collect(Collectors.toMap(Room::getName, Function.identity()));

        // select the people who can come that day based on priority
        Pair<LinkedList<Team>, List<Team>> floorDispatchResult = tryToFillTheFloor(teams, totalSizeForAllRooms);
        LinkedList<Team> teamsToDispatch = floorDispatchResult.getLeft();


        // room with best dispatch scenario by desk groups
        Map<String, TeamDispatchScenario> deskGroupScenario = new HashMap<>();

        // try to dispatch teams in the room and desk groups
        TeamDispatchScenario scenario = findBestDispatchScenariosForAllRooms(teamsToDispatch, rooms);
        int scenarioScore = 0;
        scenario.setNotAbleToDispatch(floorDispatchResult.getRight());
        for (TeamRoomDispatchScenario r : scenario.getDispatched()) {
            //log.info("Room(size=" + r.getRoomSize() + ", teams=(" + r.getTeams() + "), score=" + r.getScore() + ")");

            // get the desk groups of the current room
            Room room = roomsByName.get(r.getRoomName());
            // create "virtual rooms" for each desk group
            List<Room> deskGroups = room.getDesksGroups().entrySet().stream().map(e -> new Room(e.getKey(), new LinkedHashMap<>(Map.ofEntries(entry(e.getKey(), e.getValue()))))).toList();

            // reset the "split" status of each teams for the desk group dispatch
            // reason: we must include in the next room the rest of a team split in the previous room
            // but we are now dispatching in desk groups, so the split status must apply inside the room
            r.getTeams().stream().forEach(t -> t.setSplitTeam(false));

            // also, index by name, to avoid too much objects in memory to generate all permutations
            //Map<String, Team> teamIndex = r.getTeams().stream()
            //        .peek(t -> t.setSplitTeam(false))
            //        .collect(Collectors.toMap(Team::getName, Function.identity()));
            //List<String> teamsName = r.getTeams().stream().map(Team::getName).toList();

            //List<List<String>> allNewTeamPermutations = List.of(teamsName);
            //allTeamPermutations = List.of(teamsName);

            //log.info("Found "+allNewTeamPermutations.size()+" permutations for room "+ room.getName());
            //int cptPermutation = 0;
            //int bestPermutationScenarioScore = Integer.MIN_VALUE;
            //TeamDispatchScenario bestPermutationScenario = null;

            //for(List<String> teamsNameInPermutation : allNewTeamPermutations) {

            //LinkedList<Team> teamsInPermutation = new LinkedList<>(teamsNameInPermutation.stream().map(s -> teamIndex.get(s)).toList());
            // dispatch teams in desk groups for the current room
            TeamDispatchScenario subEndResult = findBestDispatchScenariosForAllRooms(r.getTeams(), deskGroups);
                /*if(subEndResult.totalScore() > bestPermutationScenarioScore) {
                    log.info("Permutation "+cptPermutation+" score: "+subEndResult.totalScore());
                    bestPermutationScenario = subEndResult;
                    bestPermutationScenarioScore = subEndResult.totalScore();
                }*/
            //cptPermutation++;
            //}

            // penalty to split inside the room is 2 times less than split across 2 rooms
            scenarioScore += subEndResult.totalScore()/2;
            scenarioScore += r.getScore();

            deskGroupScenario.put(r.getRoomName(), subEndResult);
        }

        log.info("Best scenario");
        for (TeamRoomDispatchScenario r : scenario.getDispatched()) {
            String teamsString = String.join(",", r.getTeams().stream().map(Team::getName).toArray(String[]::new));
            log.info("Room(size=" + r.getRoomSize() + ", teams=(" + teamsString + "), score=" + r.getScore() + ")");
            for (TeamRoomDispatchScenario sr : deskGroupScenario.get(r.getRoomName()).getDispatched()) {
                String teamsStringDeskGroup = String.join(",", sr.getTeams().stream().map(Team::getName).toArray(String[]::new));
                log.info("\tDesk(size=" + sr.getRoomSize() + ", teams=(" + teamsStringDeskGroup + "), score=" + sr.getScore() + ")");
            }
        }
        log.info("not able to fit: " + scenario.getNotAbleToDispatch());
        log.info("Total score: " + scenarioScore);

        return deskGroupScenario;
    }

    private TeamDispatchScenario findBestDispatchScenariosForAllRooms(LinkedList<Team> teams, List<Room> rooms) {
        LinkedList<Team> teamsToDispatch = new LinkedList<>(teams);

        TeamDispatchScenario endResult = new TeamDispatchScenario();
        endResult.setDispatched(new ArrayList<>());

        for (Room room : rooms) {

            TeamRoomDispatchScenario result = findBestDispatchScenarioForRoom(teamsToDispatch, room.getName(), room.roomSize());
            if (result == null) {
                break;
            }
            Integer sum = result.getTotalSize();

            // if not all teams fit in this room, split the last team
            if (sum > room.roomSize()) {
                Team lastTeam = result.getTeams().get(result.getTeams().size() - 1);
                int spaceLeftWithoutLastTeam = room.roomSize() - (result.getTotalSize() - lastTeam.size());

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

    private TeamRoomDispatchScenario findBestDispatchScenarioForRoom(LinkedList<Team> teamsToAllocate, String roomName, int roomSize) {
        // if we split a team of 20 in 2, the penalty is then 0, it's OK if you have 4 other colleagues next to you
        final int maxPenalty = 10;

        List<TeamRoomDispatchScenario> results;

        // special case: fewer people than the room, only 1 scenario
        int totalTeamSize = teamsToAllocate.stream().mapToInt(Team::size).sum();
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
            int spaceLeft = roomSize - (r.getTotalSize() - last.size());

            Pair<Team, Team> teamSplit = splitTeam(last, spaceLeft);

            int smallestGroup = Math.min(teamSplit.getLeft().size(), teamSplit.getRight().size());
            int penalty = Math.max(maxPenalty - smallestGroup, 0);

            r.setScore(r.getScore() - penalty);
        }

        // how many teams are a split
        long totalSplitTeam = teamsToAllocate.stream().filter(Team::isSplitTeam).count();

        /*log.info("------------");
        for(TeamRoomDispatchScenario s : results) {
            String[] teamsName = s.getTeams().stream()
                    .map(t->t.getName()+"("+t.getSize()+")")
                    .toArray(String[]::new);
            log.info("["+String.join(",", teamsName)+"]");
        }*/

        // get the combination with the best score, that includes a team previously split from previous room
        Optional<TeamRoomDispatchScenario> optionalResult = results.stream()
                .filter(r -> r.getTeams().stream()
                        .filter(Team::isSplitTeam).count() == totalSplitTeam)
                .sorted((o1, o2) -> Comparator.<Integer>reverseOrder().compare(o1.getScore(), o2.getScore()))
                .findFirst();

        if (optionalResult.isEmpty()) {
            return null;
        }
        return optionalResult.get();
    }

    private List<TeamRoomDispatchScenario> findAllDispatchScenarioForRoom(LinkedList<Team> teamsToAllocate, String roomName, int roomSize, LinkedList<Team> teamsAllocated) {

        // if we already used all desks of the room, we stop here
        int totalNumberPeopleInTheRoom = teamsAllocated.stream().mapToInt(Team::size).sum();
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

    private List<List<Integer>> heapPermutation(List<Integer> teams) {
        return heapPermutation(teams.toArray(new Integer[0]), teams.size());
    }

    // Generating permutation using Heap Algorithm
    private List<List<Integer>> heapPermutation(Integer[] teams, int size)
    {
        List<List<Integer>> allPermutations = new ArrayList<>();

        // if size becomes 1 then prints the obtained
        // permutation
        if (size == 1)
            return List.of(Arrays.asList(teams));

        for (int i = 0; i < size; i++) {
            allPermutations.addAll(heapPermutation(Arrays.copyOf(teams, teams.length), size - 1));

            if(allPermutations.size() >= 362880) {
                log.warn("Too many permutations");
                break;
            }

            // if size is odd, swap 0th i.e (first) and
            // (size-1)th i.e (last) element
            if (size % 2 == 1) {
                Integer temp = teams[0];
                teams[0] = teams[size - 1];
                teams[size - 1] = temp;
            }

            // If size is even, swap ith
            // and (size-1)th i.e last element
            else {
                Integer temp = teams[i];
                teams[i] = teams[size - 1];
                teams[size - 1] = temp;
            }
        }

        return allPermutations;
    }

    private Pair<Team, Team> splitTeam(Team team, int size) {
        int sizeSubGroupA = size;
        int sizeSubGroupB = team.size() - sizeSubGroupA;

        // re-create the team but with only the members who fit
        Team teamA = new Team(team.getName() + "_A", team.getManagerEmail());
        teamA.setSplitTeam(true);
        if (team.getSplitOriginalName() != null && team.getSplitOriginalName().length() > 0) {
            teamA.setSplitOriginalName(team.getSplitOriginalName());
        } else {
            teamA.setSplitOriginalName(team.getName());
        }

        // create a new "team" which is the second split of the last team, to be assigned to another room
        Team teamB = new Team(team.getName() + "_B", team.getManagerEmail());
        teamB.setSplitTeam(true);
        if (team.getSplitOriginalName() != null && team.getSplitOriginalName().length() > 0) {
            teamB.setSplitOriginalName(team.getSplitOriginalName());
        } else {
            teamB.setSplitOriginalName(team.getName());
        }

        teamA.setMembers(team.getMembers().subList(0, sizeSubGroupA));
        teamB.setMembers(team.getMembers().subList(sizeSubGroupA, team.size()));

        return ImmutablePair.of(teamA, teamB);
    }
}
