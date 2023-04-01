package org.grozeille;

import org.grozeille.model.Team;
import org.grozeille.util.ConfigUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigUtilTest {
    @Test
    public void getSampleTeamForDayTest() {
        List<Team> sampleTeamForDay = ConfigUtil.getSampleTeamForDay(1, 63);
        assertNotEquals(0, sampleTeamForDay.size());
    }
}
