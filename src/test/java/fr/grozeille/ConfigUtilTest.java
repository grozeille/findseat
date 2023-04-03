package fr.grozeille;

import fr.grozeille.findseat.FindSeatApplicationProperties;
import fr.grozeille.findseat.model.Team;
import fr.grozeille.findseat.services.ConfigService;
import fr.grozeille.findseat.services.FileBookingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigUtilTest {
    @Test
    public void getSampleTeamForDayTest() {
        FindSeatApplicationProperties properties = new FindSeatApplicationProperties();
        properties.setDataPath("target");

        ConfigService configService = new ConfigService(properties);
        FileBookingService fileBookingService = new FileBookingService(properties, configService);

        List<Team> sampleTeamForDay = fileBookingService.generateSampleTeamForDay(1, 63);
        assertNotEquals(0, sampleTeamForDay.size());
    }
}
