package fr.grozeille.findseat.services;

import com.opencsv.CSVWriter;
import fr.grozeille.findseat.FindSeatApplicationProperties;
import fr.grozeille.findseat.model.*;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileBookingService {

    public static final String INPUT_FOLDER = "input";
    public static final String OUTPUT_FOLDER = "output";

    public static final String DAY_CODE_TEAMDAY = "T";
    public static final String DAY_CODE_OFF = "";
    public static final String DAY_CODE_FLEX = "F";
    public static final String DAY_CODE_ADDITIONAL = "A";
    public static final String FILE_ALL_PEOPLE_WEEK = "all_people_week.csv";

    private static Map<String, Map<String, Team>> cachedSampleAllTeamByGroup;

    private final FindSeatApplicationProperties config;

    private final ConfigService configService;

    public FileBookingService(FindSeatApplicationProperties config, ConfigService configService) {
        this.config = config;
        this.configService = configService;
    }

    /**
     *
     * @return a map of all teams by day of week, read from the files for the next week
     * @throws Exception
     */
    public Map<Integer, List<Team>> readTeamForWeekFile() throws Exception {

        File[] files = getInputFolder().listFiles();

        Map<Integer, List<Team>> teamsByDay = new HashMap<>();

        for(File f : files) {
            List<List<String>> rows = CsvUtils.readCsv(f);
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
                    BookingType bookingType = null;
                    if(statusForTheDay.equalsIgnoreCase(DAY_CODE_TEAMDAY)) {
                        bookingType = BookingType.MANDATORY;
                    } else if(statusForTheDay.equalsIgnoreCase(DAY_CODE_FLEX)) {
                        bookingType = BookingType.NORMAL;
                    } else if(statusForTheDay.equalsIgnoreCase(DAY_CODE_ADDITIONAL)) {
                        bookingType = BookingType.OPTIONAL;
                    }
                    if (!rowTeamName.equals(teamName)) {
                        team = new Team(rowTeamName, managerEmail);
                        team.setSplitOriginalName(rowTeamName);
                        teamsOfTheDay.add(team);
                        teamName = rowTeamName;
                    }
                    if(bookingType != null) {
                        team.getMembers().add(new People(firstname, lastname, email, bookingType));
                    }
                }
            }
        }

        // remove teams without any members for a specific day
        for(Map.Entry<Integer, List<Team>> entry : teamsByDay.entrySet()) {
            entry.setValue(entry.getValue().stream().filter(t -> t.size() > 0).toList());
        }

        return teamsByDay;
    }

    /**
     * Get cached sample or build for the first load
     * @return a map of groups, with a map of teams as value and team names as key
     */
    private Map<String, Map<String, Team>> getSampleAllTeamByGroup() {
        Faker faker = new Faker();

        if(cachedSampleAllTeamByGroup == null) {
            Map<String, Integer> teamSizes = new HashMap<>();
            cachedSampleAllTeamByGroup = new HashMap<>();
            Map<String,Team> group1 = new HashMap<>();
            createNewTeamInGroupWithSize(teamSizes, group1, "G1T1", 6);
            createNewTeamInGroupWithSize(teamSizes, group1, "G1T2", 20);
            cachedSampleAllTeamByGroup.put("G1", group1);

            Map<String,Team> group2 = new HashMap<>();
            createNewTeamInGroupWithSize(teamSizes, group2, "G2T1", 17);
            createNewTeamInGroupWithSize(teamSizes, group2, "G2T2", 20);
            cachedSampleAllTeamByGroup.put("G2", group2);

            Map<String,Team> group3 = new HashMap<>();
            createNewTeamInGroupWithSize(teamSizes, group3, "G3T1", 3);
            createNewTeamInGroupWithSize(teamSizes, group3, "G3T2", 12);
            createNewTeamInGroupWithSize(teamSizes, group3, "G3T3", 8);
            cachedSampleAllTeamByGroup.put("G3", group3);

            Map<String,Team> group4 = new HashMap<>();
            createNewTeamInGroupWithSize(teamSizes, group4, "G4T1", 5);
            createNewTeamInGroupWithSize(teamSizes, group4, "G4T2", 15);
            createNewTeamInGroupWithSize(teamSizes, group4, "G4T3", 6);
            cachedSampleAllTeamByGroup.put("G4", group4);

            Map<String,Team> group5 = new HashMap<>();
            createNewTeamInGroupWithSize(teamSizes, group5, "G5T1", 9);
            createNewTeamInGroupWithSize(teamSizes, group5, "G5T2", 9);
            createNewTeamInGroupWithSize(teamSizes, group5, "G5T3", 12);
            cachedSampleAllTeamByGroup.put("G5", group5);

            for(Map<String, Team> teams : cachedSampleAllTeamByGroup.values()) {
                for(Team t : teams.values()) {
                    t.setSplitOriginalName(t.getName());
                    String managerEmail = (faker.name().firstName()+"."+faker.name().lastName()+"@worldcompany.com").toLowerCase(Locale.ROOT);
                    t.setManagerEmail(managerEmail);
                    for(int cpt = 0; cpt < teamSizes.get(t.getName()); cpt++) {
                        String firstname = faker.name().firstName();
                        String lastname = faker.name().lastName();
                        String email = (firstname+"."+lastname+"@worldcompany.com").toLowerCase(Locale.ROOT);
                        t.getMembers().add(new People(firstname, lastname, email, BookingType.NORMAL));
                    }
                }
            }
        }

        Map<String, Map<String, Team>> result = new HashMap<>();
        for(Map.Entry<String, Map<String, Team>> group : FileBookingService.cachedSampleAllTeamByGroup.entrySet()) {
            Map<String, Team> newTeam = new HashMap<>();
            result.put(group.getKey(), newTeam);
            for(Map.Entry<String, Team> team : group.getValue().entrySet()) {
                newTeam.put(team.getKey(), (Team)team.getValue().clone());
            }
        }
        return result;
    }

    private void createNewTeamInGroupWithSize(Map<String, Integer> teamSizes, Map<String, Team> group, String teamName, int size) {
        Team team = new Team(teamName);
        teamSizes.put(team.getName(), size);
        group.put(teamName, team);
    }

    private static Team cloneTeamWithMandatory(Team t) {
        Team clone = (Team)t.clone();
        clone.getMembers().stream().forEach(p -> p.setBookingType(BookingType.MANDATORY));
        return clone;
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
    public List<Team> generateSampleTeamForDay(int day, int totalSizeForAllRooms) {
        Map<String, Map<String, Team>> groups = getSampleAllTeamByGroup();

        Map<Integer, List<Team>> days = new HashMap<>();
        days.put(1, new ArrayList<>());
        days.get(1).addAll(groups.get("G1").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());
        days.get(1).addAll(groups.get("G5").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());

        days.put(2, new ArrayList<>());
        days.get(2).addAll(groups.get("G3").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());
        days.get(2).addAll(groups.get("G4").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());

        days.put(3, new ArrayList<>());
        days.get(3).addAll(groups.get("G1").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());
        days.get(3).addAll(groups.get("G2").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());

        days.put(4, new ArrayList<>());
        days.get(4).addAll(groups.get("G4").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());
        days.get(4).addAll(groups.get("G5").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());

        days.put(5, new ArrayList<>());
        days.get(5).addAll(groups.get("G2").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());
        days.get(5).addAll(groups.get("G3").values().stream().map(FileBookingService::cloneTeamWithMandatory).toList());

        List<Team> teams = days.get(day);

        Random random = new Random();

        // simulate vacations in the mandatory teams
        int workingDays = 250;
        int vacations = 4*5 + 5; // 4 weeks + 5 sick days
        for(Team t: teams) {
            int newSize = t.size();
            for(int i = 0; i < t.size(); i++) {
                boolean isOff = random.nextInt(workingDays) <= vacations;
                if(isOff) {
                    newSize--;
                }
            }
            Collections.shuffle(t.getMembers(), random);
            t.setMembers(t.getMembers().subList(0, newSize));
        }

        Integer mandatoryTotalSizeTeams = teams.stream().mapToInt(Team::size).sum();

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
            int newSize = random.nextInt(Math.min(t.size(), 4));
            Collections.shuffle(t.getMembers(), random);
            t.setMembers(t.getMembers().subList(0, newSize));
            t.setMembers(t.getMembers().stream().map(p -> {
                People newP = (People)p.clone();
                newP.setBookingType(BookingType.OPTIONAL);
                return newP;
            }).toList());
            additionalTeams.add(t);
            additionalPeople += t.size();
            if(additionalPeople >= toReach) {
                break;
            }
        }

        teams.addAll(additionalTeams);

        // remove empty teams
        teams = teams.stream().filter(t -> t.size() > 0).toList();

        return teams;
    }

    public Map<Integer, List<Team>> generateSampleTeamForWeek(int totalSizeForAllRooms) {
        Map<Integer, List<Team>> teamsByDay = new HashMap<>();
        for(int day = 1; day <= 5; day++) {
            LinkedList<Team> teams = new LinkedList<>(this.generateSampleTeamForDay(day, totalSizeForAllRooms));
            teamsByDay.put(day, teams);
        }

        return teamsByDay;
    }

    /**
     * build and save a file with the same format as the input files for next week, but consolidated as a single file
     * @param days the map of days including all teams
     * @throws Exception
     */
    public void writeTeamForWeekFile(Map<Integer, List<Team>> days, File outputFile) throws Exception {
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
                    if(p.getBookingType().equals(BookingType.MANDATORY)) {
                        currentPeopleRow[4+day.getKey()] = DAY_CODE_TEAMDAY;
                    } else if(p.getBookingType().equals(BookingType.NORMAL)) {
                        currentPeopleRow[4+day.getKey()] = DAY_CODE_FLEX;
                    } else {
                        currentPeopleRow[4+day.getKey()] = DAY_CODE_ADDITIONAL;
                    }

                }
            }
        }
        List<String[]> output = new ArrayList<>();
        output.add(new String[]{"Team Name","First Name","Last Name","Email","Manager Email",
                "Monday","Tuesday","Wednesday","Thursday","Friday","KeepEmpty"});
        output.addAll(peopleRows.values().stream().sorted((o1, o2) -> StringUtils.compare(o1[0], o2[0])).toList());
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(outputFile))) {
            csvWriter.writeAll(output);
        } catch (IOException e) {
            throw new RuntimeException("Not able to write output CSV", e);
        }
    }

    /**
     * build the excel file with the floor plan and assigned people for each day
     * @param deskPeopleMappingPerDay
     */
    public void exportToExcelFloorMap(WeekDispatchResult deskPeopleMappingPerDay) {
        try {
            File inputFile = new File(configService.getConfigFolder(), ConfigService.FILE_FLOOR_PLAN);
            FileInputStream file = new FileInputStream(inputFile);
            Workbook workbook = new XSSFWorkbook(file);

            for (int day = 1; day <= 5; day++) {
                // create a mapping of team/color
                String[] colors = new String[]{
                        "#838CA9", "#A4D78D", "#FFE485",
                        "#FFC598", "#E58787", "#98EBEC",
                        "#70A3CA", "#6994AE", "#FACF7F",
                        "#F7A072", "#FF0F80", "#2B50AA",
                        "#FF9FE5", "#FFD4D4", "#FF858D"};

                Map<String, String> teamColorMap = new HashMap<>();
                List<Team> allDispatchedTeams = deskPeopleMappingPerDay
                        .getDispatchPerDayOfWeek().get(day)
                        .getDeskAssignedToPeople().values().stream()
                        .map(PeopleWithTeam::getTeam)
                        .distinct()
                        .toList();
                for (int i = 0; i < allDispatchedTeams.size(); i++) {
                    teamColorMap.put(allDispatchedTeams.get(i).getSplitOriginalName(), colors[i % colors.length]);
                }

                // clone the floor plan for a specific day to replace desk names by the assigned people
                workbook.cloneSheet(0);
                workbook.setSheetName(workbook.getNumberOfSheets() - 1, DayOfWeek.of(day).name());

                Sheet sheet = workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String currentCellValue = cell.getStringCellValue();
                        if (!currentCellValue.isEmpty()) {
                            PeopleWithTeam peopleInTeam = deskPeopleMappingPerDay
                                    .getDispatchPerDayOfWeek().get(day)
                                    .getDeskAssignedToPeople().get(currentCellValue);
                            if (peopleInTeam != null) {
                                String BookingTypeChar = BookingTypeToString(peopleInTeam.getPeople().getBookingType());
                                String newCellValue = BookingTypeChar + " " + peopleInTeam.getTeam().getSplitOriginalName() + " " + peopleInTeam.getPeople().getEmail();
                                cell.setCellValue(newCellValue);

                                CellStyle cellStyle = cell.getSheet().getWorkbook().createCellStyle();

                                String hexaColor = teamColorMap.get(peopleInTeam.getTeam().getSplitOriginalName());
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

            File floorPlanOutputExcelFile = new File(getOutputFolder(), "w" + nextWeek()+ ".xlsx");
            if (floorPlanOutputExcelFile.exists()) {
                floorPlanOutputExcelFile.delete();
            }
            workbook.write(new FileOutputStream(floorPlanOutputExcelFile));

        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * save a file with all people not assigned to a desk for a specific day
     * @param day
     * @param peopleWithoutDesk
     */
    public void exportPeopleWithoutDesk(int day, List<PeopleWithTeam> peopleWithoutDesk) throws Exception {
        File outputFile = new File(getOutputFolder(), "people_without_desk_"+DayOfWeek.of(day).name()+".csv");

        List<String[]> output = new ArrayList<>();

        for(PeopleWithTeam pt : peopleWithoutDesk) {
            String BookingTypeChar = BookingTypeToString(pt.getPeople().getBookingType());
            output.add(new String[]{pt.getPeople().getEmail(), BookingTypeChar, pt.getTeam().getSplitOriginalName()});
            log.info("People without desk: " + pt);
        }
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(outputFile))) {
            csvWriter.writeAll(output);
        } catch (IOException e) {
            throw new RuntimeException("Not able to write output CSV", e);
        }
    }

    public File getInputFolder() throws Exception {
        File rootFolder = new File(config.getDataPath());
        if(!rootFolder.exists()) {
            throw new Exception("Path "+config.getDataPath()+" is invalid");
        }

        File inputFolder = new File(rootFolder, INPUT_FOLDER);
        File yearParentFolder = new File(inputFolder, Integer.toString(LocalDate.now().getYear()));
        File weekParentFolder = new File(yearParentFolder, "w"+nextWeek());
        if(!weekParentFolder.exists()) {
            weekParentFolder.mkdirs();
        }

        return weekParentFolder;
    }

    public File getOutputFolder() throws Exception {
        File rootFolder = new File(config.getDataPath());
        if(!rootFolder.exists()) {
            throw new Exception("Path "+config.getDataPath()+" is invalid");
        }

        File inputFolder = new File(rootFolder, OUTPUT_FOLDER);
        File yearParentFolder = new File(inputFolder, Integer.toString(LocalDate.now().getYear()));
        File weekParentFolder = new File(yearParentFolder, "w"+nextWeek());
        if(!weekParentFolder.exists()) {
            weekParentFolder.mkdirs();
        }

        return weekParentFolder;
    }


    public int nextWeek() {
        return LocalDate.now().plusWeeks(1).get(WeekFields.ISO.weekOfYear());
    }


    private String BookingTypeToString(BookingType BookingType) {
        String BookingTypeChar = "";
        if(BookingType.equals(BookingType.MANDATORY)) {
            BookingTypeChar = DAY_CODE_TEAMDAY;
        } else if(BookingType.equals(BookingType.NORMAL)) {
            BookingTypeChar = DAY_CODE_FLEX;
        } else if(BookingType.equals(BookingType.OPTIONAL)) {
            BookingTypeChar = DAY_CODE_ADDITIONAL;
        }
        return BookingTypeChar;
    }
    private byte[] hexaToRgb(String hexaColor) {
        if (hexaColor.startsWith("#")) {
            hexaColor = hexaColor.substring(1);
        }
        int resultRed = Integer.valueOf(hexaColor.substring(0, 2), 16);
        int resultGreen = Integer.valueOf(hexaColor.substring(2, 4), 16);
        int resultBlue = Integer.valueOf(hexaColor.substring(4, 6), 16);

        return new byte[]{(byte) resultRed, (byte) resultGreen, (byte) resultBlue};
    }
}
