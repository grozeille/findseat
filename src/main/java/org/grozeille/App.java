package org.grozeille;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        teams.add(new Team("F", 9, false));
        teams.add(new Team("G", 6, false));
        teams.add(new Team("H", 7, false));
        teams.add(new Team("I", 1, false));
        teams.add(new Team("J", 7, false));
        teams.add(new Team("K", 11, false));
        teams.add(new Team("L", 6, false));
        teams.add(new Team("M", 6, false));

        //Integer[] roomSizes = new Integer[]{6, 18, 39};
        // 6 = 3 + 3
        // 18 = 3 + 6 + 6 + 3
        // 39 = 5 + 10 + 10 + 9 + 5
        Integer[] roomSizes = new Integer[]{6, 10, 39};

        dispatchTeamsToRooms(teams, roomSizes, true);
    }

    private static void dispatchTeamsToRooms(List<Team> teams, Integer[] roomSizes, boolean isRoom) {
        ArrayList<Team> teamsToDispatch = new ArrayList<>(teams);

        // try to fit people in all "rooms", starting with the smallest one to the biggest one
        for(Integer rs : Arrays.stream(roomSizes).sorted().toList()) {
            TeamDeskResult result = sumUp(teamsToDispatch, rs);
            Integer sum = result.getTotalSize();

            // if not all teams fit in this room, split the last team
            if(sum > rs) {
                Team lastTeam = result.getTeams().get(result.getTeams().size() - 1);
                int sizeSubGroupA = rs - (result.getTotalSize() - lastTeam.getSize());
                int sizeSubGroupB = lastTeam.getSize() - sizeSubGroupA;

                lastTeam.setSize(sizeSubGroupA);
                sum = result.getTotalSize();

                // create a new "team" which is the second split of the last team, to be assigned to another room
                Team splitLastTeam = new Team(lastTeam.getName()+"bis", sizeSubGroupB, lastTeam.isTeamDay());
                teamsToDispatch.add(splitLastTeam);
            }

            System.out.println((isRoom ? "Room: " : "\tDesk: ") + "bestMatch("+result+")="+sum+" (try to match "+rs+")");

            if(isRoom) {
                // 6 = 3 + 3
                // 18 = 3 + 6 + 6 + 3
                // 10 = 4 + 4 + 2
                // 39 = 5 + 10 + 10 + 9 + 5
                Integer[] subRoomSizes;
                if (rs == 6) {
                    subRoomSizes = new Integer[]{3, 3};
                //} else if (rs == 18) {
                } else if (rs == 10) {
                    //subRoomSizes = new Integer[]{3, 6, 6, 3};
                    subRoomSizes = new Integer[]{4, 4, 2};
                } else { // rs == 39
                    subRoomSizes = new Integer[]{5, 10, 10, 9, 5};
                }
                dispatchTeamsToRooms(result.getTeams(), subRoomSizes, false);
            }

            // [ E, E, E]
            // ----------
            // [ H, D, D]
            //
            // [ H, H, H]
            // ----------
            // [ H, H, H]
            //
            // [ G, G, G]
            // ----------
            // [ G, G, G]


            // [ E, E, E]
            // ----------
            // [ G, G, G]
            //
            // [ G, G, G]
            // ----------
            // [ H, H, H]
            //
            // [ H, H, H]
            // ----------
            // [ H, D, D]

            teamsToDispatch.removeAll(result.getTeams());
        }

        System.out.println((isRoom ? "" : "\t")+"not able to fit: "+teamsToDispatch);
    }

    static List<TeamDeskResult> sumUpRecursive(ArrayList<Team> numbers, int target, ArrayList<Team> partial) {

        int sum = partial.stream().map(Team::getSize).reduce(0, Integer::sum);

        // exact match or too high
        if (sum >= target) {
            //String numbersString = partial.stream().map(Object::toString).collect(Collectors.joining("+"));
            //System.out.println(numbersString + "=" + sum);
            return List.of(new TeamDeskResult(partial, 0));
        }

        List<TeamDeskResult> bestMatches = new ArrayList<>();

        for(int i=0;i<numbers.size();i++) {
            ArrayList<Team> remaining = new ArrayList<>();
            Team n = numbers.get(i);

            for (int j=i+1; j<numbers.size();j++) remaining.add(numbers.get(j));

            ArrayList<Team> partial_rec = new ArrayList<>(partial);
            partial_rec.add(n);

            List<TeamDeskResult> result = sumUpRecursive(remaining,target,partial_rec);
            for(TeamDeskResult r : result) {
                sum = r.getTotalSize();
                // exact match or too high
                if (sum >= target)
                    bestMatches.add(r);
            }

        }

        return bestMatches;
    }

    static TeamDeskResult sumUp(ArrayList<Team> numbers, int target) {
        final int maxPenalty = 10;

        List<TeamDeskResult> results = sumUpRecursive(numbers, target, new ArrayList<>());

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

        // sorted by size (smaller to bigger)
        Optional<TeamDeskResult> optionalFirst = results.stream().findFirst();
        if(optionalFirst.isEmpty()) {
            System.out.println("DEBUG");
        }
        TeamDeskResult first = optionalFirst.get();

        // if best match found, that's it
        if(first.getTotalSize() == target) {
            return first;
        }

        // else, try to score the best solution
        for(TeamDeskResult r : results) {
            // split the last Team and compute the penalty of it
            Team last = r.getTeams().get(r.getTeams().size() - 1);
            int sizeSubGroupA = target - (r.getTotalSize() - last.getSize());
            int sizeSubGroupB = last.getSize() - sizeSubGroupA;
            int min = Math.min(sizeSubGroupA, sizeSubGroupB);
            int penalty = Math.max(maxPenalty - min, 0);

            r.setScore(-penalty);

            //String numbersString = r.getTeams().stream().map(Object::toString).collect(Collectors.joining("+"));
            int sum = r.getTotalSize();
            //System.out.println("target: " + target + " " + r.getTeams() + "=" + sum + " score: "+ r.getScore());
        }

        TeamDeskResult result = results.stream()
                .sorted((o1, o2) -> Comparator.<Integer>reverseOrder().compare(o1.getScore(), o2.getScore()))
                .findFirst().get();
        return result;
    }

}
