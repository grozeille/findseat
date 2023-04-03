package fr.grozeille.findseat.services;

import fr.grozeille.findseat.FindSeatApplicationProperties;
import fr.grozeille.findseat.controllers.AdminConfigController;
import fr.grozeille.findseat.model.Room;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Service
@Slf4j
public class ConfigService {

    public static final String FILE_FLOOR_PLAN = "floor-plan.xlsx";
    public static final String FILE_ROOMS = "rooms.csv";
    public static final String FILE_TEAM_DAYS = "team-days.csv";
    public static final String CONFIG_FOLDER = "config";

    private final FindSeatApplicationProperties config;

    public ConfigService(FindSeatApplicationProperties config) {
        this.config = config;
    }

    public File getConfigFolder() throws Exception {
        File rootFolder = new File(config.getDataPath());
        if(!rootFolder.exists()) {
            throw new Exception("Path "+config.getDataPath()+" is invalid");
        }

        File configParentFolder = new File(rootFolder, CONFIG_FOLDER);
        if(!configParentFolder.exists()) {
            configParentFolder.mkdirs();
            // first time so we also copy the sample files
            String[] sampleFiles = {FILE_ROOMS, FILE_FLOOR_PLAN, FILE_TEAM_DAYS};
            for (String sampleFile : sampleFiles) {
                try (InputStream file = AdminConfigController.class.getResourceAsStream("/sample/" + sampleFile)) {
                    Files.copy(file, new File(configParentFolder, sampleFile).toPath());
                }
            }
        }

        return configParentFolder;
    }

    /**
     * parse CSV format:
     * room name, desk group name, desk name
     * roomA, deskGroupA, DeskAA001
     * roomA, deskGroupA, DeskAA002
     * roomA, deskGroupB, DeskAB001
     * roomB, deskGroupA, DeskBA001
     * @return list of rooms based on a rooms.csv file in the provided folder
     */
    public List<Room> parseRoomsFile() throws Exception {
        List<Room> rooms = new ArrayList<>();
        File roomsFile = new File(getConfigFolder(), FILE_ROOMS);
        List<List<String>> rows = CsvUtils.readCsv(roomsFile);

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


}
