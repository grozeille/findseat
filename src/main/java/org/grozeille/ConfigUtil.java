package org.grozeille;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class ConfigUtil {

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
                // not the same room
                team = new Team(rowTeamName, 0, mandatory);
                teams.add(team);
                teamName = rowTeamName;
            }
            String teamMemberName = row.get(2);
            // for now, ignore the name, just count the total number of members
            team.setSize(team.getSize()+1);
        }

        return teams;
    }

    private static List<List<String>> readCsv(String fileName) {

        List<List<String>> records = new ArrayList<>();

        URL resource = App.class.getClassLoader().getResource(fileName);
        try (CSVReader csvReader = new CSVReader(new FileReader(new File(resource.toURI())));) {
            String[] values = null;
            while ((values = csvReader.readNext()) != null) {
                records.add(Arrays.asList(values));
            }
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Not able to load "+fileName, e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return records;
    }
}
