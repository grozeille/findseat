package org.grozeille;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import net.datafaker.Faker;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class ConfigUtil {

    public static final String DAY_CODE_TEAMDAY = "T";
    public static final String DAY_CODE_OFF = "";
    public static final String DAY_CODE_FLEX = "F";

    /**
     * parse CSV format:
     * room name, desk group name, desk name
     * roomA, deskGroupA, DeskAA001
     * roomA, deskGroupA, DeskAA002
     * roomA, deskGroupB, DeskAB001
     * roomB, deskGroupA, DeskBA001
     * @return
     */
    public static List<Room> parseRoomsFile(String folder) {
        List<Room> rooms = new ArrayList<>();
        String fileName = folder+"/rooms.csv";
        List<List<String>> rows = readCsv(fileName);

        // consider first line as header
        rows.remove(0);

        String roomName = "";
        Room room = new Room();
        String deskGroupName = "";
        List<String> desks = new ArrayList<>();

        for(List<String> row : rows) {
            String rowRoomName = row.get(0);
            if(!rowRoomName.equals(roomName)) {
                // not the same room
                room = new Room(rowRoomName, new LinkedHashMap<>());
                rooms.add(room);
                deskGroupName = "";
                roomName = rowRoomName;
            }
            String rowDeskGroupName =  row.get(1);
            if(!rowDeskGroupName.equals(deskGroupName)) {
                // not the same desk group
                desks = new ArrayList<>();
                room.getDesksGroups().put(rowDeskGroupName, desks);
                deskGroupName = rowDeskGroupName;
            }
            String deskName = row.get(2);
            desks.add(deskName);
        }

        return rooms;
    }

    /**
     * parse CSV format:
     * team name, mandatory, team member name
     * teamA, 1, John Doe
     * teamB, 0, Sydney Buffy
     * @return
     */
    public static List<Team> parseTeamsFile(String folder) {
        List<Team> teams = new ArrayList<>();
        String fileName = folder+"/teams.csv";
        List<List<String>> rows = readCsv(fileName);

        // consider first line as header
        rows.remove(0);

        String teamName = "";
        Team team = new Team();

        for(List<String> row : rows) {
            String rowTeamName = row.get(0);
            if(!rowTeamName.equals(teamName)) {
                boolean mandatory = row.get(1).equals("1");
                team = new Team(rowTeamName, 0, mandatory, "");
                team.setSplitOriginalName(rowTeamName);
                teams.add(team);
                teamName = rowTeamName;
            }
            String teamMemberName = row.get(2);
            // for now, ignore the name, just count the total number of members
            team.setSize(team.getSize()+1);
        }

        return teams;
    }

    public static Map<Integer, List<Team>> parseTeamForWeekFile(String folder) {
        URL url = App.class.getClassLoader().getResource(folder);
        String path = url.getPath();
        File[] files = new File(path).listFiles();

        Map<Integer, List<Team>> teamsByDay = new HashMap<>();

        for(File f : files) {
            List<List<String>> rows = readCsv(f);
            // consider first line as header
            rows.remove(0);

            for(int day = 0; day < 5; day++) {
                String teamName = "";
                Team team = new Team();

                int statusForDayColumn = 5 + day;
                List<Team> teamsOfTheDay = new ArrayList<>();
                if(teamsByDay.containsKey(day+1)) {
                    teamsOfTheDay = teamsByDay.get(day+1);
                } else {
                    teamsByDay.put(day+1, teamsOfTheDay);
                }

                for (List<String> row : rows) {
                    String rowTeamName = row.get(0);
                    String firstname = row.get(1);
                    String lastname = row.get(2);
                    String email = row.get(3);
                    String managerEmail = row.get(4);
                    String statusForTheDay = row.get(statusForDayColumn);
                    boolean mandatory = statusForTheDay.equalsIgnoreCase(DAY_CODE_TEAMDAY);
                    boolean outOffOffice = statusForTheDay.equalsIgnoreCase(DAY_CODE_OFF);
                    boolean flex = statusForTheDay.equalsIgnoreCase(DAY_CODE_FLEX);
                    if (!rowTeamName.equals(teamName)) {
                        team = new Team(rowTeamName, 0, mandatory, managerEmail);
                        team.setSplitOriginalName(rowTeamName);
                        teamsOfTheDay.add(team);
                        teamName = rowTeamName;
                    }
                    // if any member of the team has his team day that day, that means it's the team day of the team
                    // (we can have "empty" for a member for the team day if that person is out of office)
                    team.setMandatory(team.isMandatory() || mandatory);

                    if(mandatory || flex) {
                        team.getMembers().add(new People(firstname, lastname, email));
                        team.setSize(team.getSize() + 1);
                    }
                }
            }
        }

        // remove teams without any members for a specific day
        for(Map.Entry<Integer, List<Team>> entry : teamsByDay.entrySet()) {
            entry.setValue(entry.getValue().stream().filter(t -> t.getSize() > 0).toList());
        }

        return teamsByDay;
    }

    private static Map<String, Map<String, Team>> cachedSampleAllTeamByGroup;

    public static Map<String, Map<String, Team>> getSampleAllTeamByGroup() {
        Faker faker = new Faker();

        if(cachedSampleAllTeamByGroup == null) {
            cachedSampleAllTeamByGroup = new HashMap<>();
            Map<String,Team> group1 = new HashMap<>();
            group1.put("G1T1", new Team("G1T1", 6, true, ""));
            group1.put("G1T2", new Team("G1T2", 20, true, ""));
            cachedSampleAllTeamByGroup.put("G1", group1);

            Map<String,Team> group2 = new HashMap<>();
            group2.put("G2T1", new Team("G2T1", 17, true, ""));
            group2.put("G2T2", new Team("G2T2", 20, true, ""));
            cachedSampleAllTeamByGroup.put("G2", group2);

            Map<String,Team> group3 = new HashMap<>();
            group3.put("G3T1", new Team("G3T1", 3, true, ""));
            group3.put("G3T2", new Team("G3T2", 12, true, ""));
            group3.put("G3T3", new Team("G3T3", 8, true, ""));
            cachedSampleAllTeamByGroup.put("G3", group3);

            Map<String,Team> group4 = new HashMap<>();
            group4.put("G4T1", new Team("G4T1", 5, true, ""));
            group4.put("G4T2", new Team("G4T2", 15, true, ""));
            group4.put("G4T3", new Team("G4T3", 6, true, ""));
            cachedSampleAllTeamByGroup.put("G4", group4);

            Map<String,Team> group5 = new HashMap<>();
            group5.put("G5T1", new Team("G5T1", 9, true, ""));
            group5.put("G5T2", new Team("G5T2", 9, true, ""));
            group5.put("G5T3", new Team("G5T3", 12, true, ""));
            cachedSampleAllTeamByGroup.put("G5", group5);

            for(Map<String, Team> teams : cachedSampleAllTeamByGroup.values()) {
                for(Team t : teams.values()) {
                    t.setSplitOriginalName(t.getName());
                    String managerEmail = (faker.name().firstName()+"."+faker.name().lastName()+"@worldcompany.com").toLowerCase(Locale.ROOT);
                    t.setManagerEmail(managerEmail);
                    for(int cpt = 0; cpt < t.getSize(); cpt++) {
                        String firstname = faker.name().firstName();
                        String lastname = faker.name().lastName();
                        String email = (firstname+"."+lastname+"@worldcompany.com").toLowerCase(Locale.ROOT);
                        t.getMembers().add(new People(firstname, lastname, email));
                    }
                }
            }
        }

        Map<String, Map<String, Team>> result = new HashMap<>();
        for(Map.Entry<String, Map<String, Team>> group : ConfigUtil.cachedSampleAllTeamByGroup.entrySet()) {
            Map<String, Team> newTeam = new HashMap<>();
            result.put(group.getKey(), newTeam);
            for(Map.Entry<String, Team> team : group.getValue().entrySet()) {
                newTeam.put(team.getKey(), (Team)team.getValue().clone());
            }
        }
        return result;
    }

    /**
     *  Group 1: 6 + 20 = 26
     *  Group 2: 17 + 20 = 37
     *  Group 3: 3 + 12 + 8 = 23
     *  Group 4: 5 + 15 + 6 = 26
     *  Group 5: 9 + 9 + 12 = 30
     *
     *  Monday : Group 1 + 5 = 26 + 30 = 56
     *  Tuesday: Group 3 + 4 = 23 + 26 = 49
     *  Wednesday: Group 1 + 2 = 26 + 37 = 63
     *  Thursday: Group 4 + 5 = 26 + 30 = 56
     *  Friday: Group 2 + 3 = 37 + 23 = 60
     *
     */
    public static List<Team> getSampleTeamForDay(int day, int totalSizeForAllRooms) {
        Map<String, Map<String, Team>> groups = getSampleAllTeamByGroup();

        Map<Integer, List<Team>> days = new HashMap<>();
        days.put(1, new ArrayList<>());
        days.get(1).addAll(groups.get("G1").values());
        days.get(1).addAll(groups.get("G5").values());

        days.put(2, new ArrayList<>());
        days.get(2).addAll(groups.get("G3").values());
        days.get(2).addAll(groups.get("G4").values());

        days.put(3, new ArrayList<>());
        days.get(3).addAll(groups.get("G1").values());
        days.get(3).addAll(groups.get("G2").values());

        days.put(4, new ArrayList<>());
        days.get(4).addAll(groups.get("G4").values());
        days.get(4).addAll(groups.get("G5").values());

        days.put(5, new ArrayList<>());
        days.get(5).addAll(groups.get("G2").values());
        days.get(5).addAll(groups.get("G3").values());

        List<Team> teams = days.get(day);

        Random random = new Random();

        // simulate vacations in the mandatory teams
        int workingDays = 250;
        int vacations = 4*5 + 5; // 4 weeks + 5 sick days
        for(Team t: teams) {
            int newSize = t.getSize();
            for(int i = 0; i < t.getSize(); i++) {
                boolean isOff = random.nextInt(workingDays) <= vacations;
                if(isOff) {
                    newSize--;
                }
            }
            t.setSize(newSize);
        }

        Integer mandatoryTotalSizeTeams = teams.stream().map(Team::getSize).reduce(0, Integer::sum);

        // simulate additional people for optional day
        List<Team> allTeams = groups.values().stream()
                .flatMap((Function<Map<String, Team>, Stream<Team>>) stringTeamMap -> stringTeamMap.values().stream())
                .toList();
        List<Team> otherTeams = new ArrayList<>(allTeams);
        otherTeams.removeAll(teams);
        // we can observe in reality that we have between 10 and 15 people more than available space each day
        // simulate teams until we reach that limit
        int toReach = (totalSizeForAllRooms + random.nextInt(5) + 10) - mandatoryTotalSizeTeams;
        Collections.shuffle(otherTeams, random);
        List<Team> additionalTeams = new ArrayList<>();
        int additionalPeople = 0;
        for(Team t : otherTeams) {
            // max 4 people of the same team are going to come the same optional day
            t.setSize(random.nextInt(Math.min(t.getSize(), 4)));
            t.setMembers(t.getMembers().subList(0, t.getSize()));
            t.setMandatory(false);
            additionalTeams.add(t);
            additionalPeople += t.getSize();
            if(additionalPeople >= toReach) {
                break;
            }
        }

        teams.addAll(additionalTeams);

        return teams;
    }

    public static Map<Integer, List<Team>> getSampleTeamForWeek(int totalSizeForAllRooms) {
        Map<Integer, List<Team>> teamsByDay = new HashMap<>();
        for(int day = 1; day <= 5; day++) {
            LinkedList<Team> teams = new LinkedList<>(ConfigUtil.getSampleTeamForDay(day, totalSizeForAllRooms));
            teamsByDay.put(day, teams);
        }

        return teamsByDay;
    }

    public static void saveTeamForWeekFile(Map<Integer, List<Team>> days) {
        Map<String, String[]> peopleRows = new HashMap<>();
        for(Map.Entry<Integer, List<Team>> day : days.entrySet()) {
            for(Team t : day.getValue()) {
                for(People p : t.getMembers()) {
                    String[] currentPeopleRow;
                    if(peopleRows.containsKey(p.getEmail())) {
                        currentPeopleRow = peopleRows.get(p.getEmail());
                    } else {
                        currentPeopleRow = new String[10];
                        currentPeopleRow[0] = t.getName();
                        currentPeopleRow[1] = p.getFirstname();
                        currentPeopleRow[2] = p.getLastname();
                        currentPeopleRow[3] = p.getEmail();
                        currentPeopleRow[4] = t.getManagerEmail();
                        for(int cpt = 5; cpt < 10; cpt++) {
                            currentPeopleRow[cpt] = "";
                        }
                        peopleRows.put(p.getEmail(), currentPeopleRow);
                    }
                    currentPeopleRow[4+day.getKey()] = t.isMandatory() ? "T" : "F";
                }
            }
        }
        List<String[]> output = new ArrayList<>();
        output.add(new String[]{"Team Name","First Name","Last Name","Email","Manager Email",
                "Monday","Tuesday","Wednesday","Thursday","Friday","KeepEmpty"});
        output.addAll(peopleRows.values().stream().sorted((o1, o2) -> StringUtils.compare(o1[0], o2[0])).toList());
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter("target/all_people_week.csv"))) {
            csvWriter.writeAll(output);
        } catch (IOException e) {
            throw new RuntimeException("Not able to write output CSV", e);
        }
    }

    public static List<List<String>> readCsv(File file)  {
        List<List<String>> records = new ArrayList<>();
        boolean containBOM = false;
        try {
            containBOM = isContainBOM(file);
        } catch (IOException e) {
            throw new RuntimeException("Not able to load "+file.getName(), e);
        }

        boolean firstLine = true;
        try (CSVReader csvReader = new CSVReader(new FileReader(file))) {
            String[] values = null;
            while ((values = csvReader.readNext()) != null) {
                if(containBOM && firstLine) {
                    String firstColumn = values[0];
                    byte[] firstColumnBytes = firstColumn.getBytes(StandardCharsets.UTF_8);
                    // remove the BOM
                    firstColumnBytes = Arrays.copyOfRange(firstColumnBytes, 3, firstColumnBytes.length);
                    firstColumn = new String(firstColumnBytes, StandardCharsets.UTF_8);
                    values[0] = firstColumn;
                }
                records.add(Arrays.asList(values));
                firstLine = false;
            }
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Not able to load "+file.getName(), e);
        }

        return records;
    }

    private static boolean isContainBOM(File file) throws IOException {
        boolean result = false;

        byte[] bom = new byte[3];
        try (InputStream is = new FileInputStream(file)) {

            // read 3 bytes of a file.
            is.read(bom);

            // BOM encoded as ef bb bf
            String content = new String(Hex.encodeHex(bom));
            if ("efbbbf".equalsIgnoreCase(content)) {
                result = true;
            }

        }

        return result;
    }

    public static List<List<String>> readCsv(String fileName) {
        URL resource = App.class.getClassLoader().getResource(fileName);
        try {
            return readCsv(new File(resource.toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Not able to load "+fileName, e);
        }
    }
}
