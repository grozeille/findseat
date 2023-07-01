package fr.grozeille.findseat.services;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import fr.grozeille.findseat.model.Booking;
import fr.grozeille.findseat.model.BookingType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeamDaysService {

    private final Map<String, Boolean[]> teamDaysByEmail = new HashMap<>();

    private final ConfigService configService;

    public TeamDaysService(ConfigService configService) {
        this.configService = configService;
    }

    public void saveConfig(InputStream inputStream) throws Exception {
        File fileToWrite = new File(configService.getConfigFolder(), ConfigService.FILE_TEAM_DAYS);
        if(fileToWrite.exists()) {
            fileToWrite.delete();
        }
        Files.copy(inputStream, fileToWrite.toPath());

        // invalidate the cache
        teamDaysByEmail.clear();
    }

    public byte[] readConfigAsByte() throws Exception {
        File file = new File(configService.getConfigFolder(), ConfigService.FILE_TEAM_DAYS);
        if(!file.exists()) {
            return new byte[0];
        }

        return Files.readAllBytes(file.toPath());
    }

    public void verifyTeamDays(Booking booking, String email) throws Exception {

        loadTeamDaysFileInCache();

        if(booking.getMonday().equals(BookingType.MANDATORY) && !this.teamDaysByEmail.get(email)[0]) {
            throw new Exception("Invalid team day 'Monday' for "+email);
        } else if(booking.getTuesday().equals(BookingType.MANDATORY) && !this.teamDaysByEmail.get(email)[1]) {
            throw new Exception("Invalid team day 'Tuesday' for "+email);
        } else if(booking.getWednesday().equals(BookingType.MANDATORY) && !this.teamDaysByEmail.get(email)[2]) {
            throw new Exception("Invalid team day 'Wednesday' for "+email);
        } else if(booking.getThursday().equals(BookingType.MANDATORY) && !this.teamDaysByEmail.get(email)[3]) {
            throw new Exception("Invalid team day 'Thursday' for "+email);
        } else if(booking.getFriday().equals(BookingType.MANDATORY) && !this.teamDaysByEmail.get(email)[4]) {
            throw new Exception("Invalid team day 'Friday' for "+email);
        }
    }

    private void loadTeamDaysFileInCache() throws Exception {
        synchronized (this.teamDaysByEmail) {
            if(this.teamDaysByEmail.isEmpty()) {
                File teamDaysFile = new File(configService.getConfigFolder(), ConfigService.FILE_TEAM_DAYS);
                boolean firstLine = true;
                try (CSVReader csvReader = new CSVReader(new FileReader(teamDaysFile))) {
                    String[] values = null;
                    while ((values = csvReader.readNext()) != null) {
                        if(firstLine) {
                            firstLine = false;
                            continue;
                        }
                        String email = values[1];
                        Boolean[] teamDays = List.of(values).subList(2, values.length).stream().map(v -> v.equalsIgnoreCase("1")).toArray(Boolean[]::new);
                        this.teamDaysByEmail.put(email, teamDays);
                    }
                } catch (IOException | CsvValidationException e) {
                    throw new RuntimeException("Not able to load "+teamDaysFile.getName(), e);
                }
            }
        }
    }
}
