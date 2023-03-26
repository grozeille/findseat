package org.grozeille;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.grozeille.model.People;
import org.grozeille.model.Team;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class ExportAllPeopleTest {

    public static final String TARGET_TESTS = "target/tests";

    @Test
    public void testExportAllPeople() throws IOException {
        // Create some test data
        List<Team> teams = new ArrayList<>();
        Team team1 = new Team("Team 1");
        team1.addMember(new People("John", "Snow"));
        team1.addMember(new People("Daenerys", "Targaryen"));
        teams.add(team1);
        Team team2 = new Team("Team 2");
        team2.addMember(new People("Tyrion", "Lannister"));
        team2.addMember(new People("Arya", "Stark"));
        teams.add(team2);

        // Call the method being tested
        int day = 3; // Wednesday
        App.exportAllPeople(teams, day, Paths.get(TARGET_TESTS));

        // Verify that the output file was created and contains the expected data
        String outputFilename = TARGET_TESTS + "/all_people_WEDNESDAY.csv";
        Path destPath = Paths.get(outputFilename);
        assertTrue(Files.exists(destPath));
        List<String> lines = Files.readAllLines(destPath);
        assertEquals(5, lines.size()); // 5 lines: 1 header row + 4 data rows
        assertEquals("\"Team 1\",\"John.Snow@" + People.DOMAIN + "\"", lines.get(1));
        assertEquals("\"Team 1\",\"Daenerys.Targaryen@" + People.DOMAIN + "\"", lines.get(2));
        assertEquals("\"Team 2\",\"Tyrion.Lannister@" + People.DOMAIN + "\"", lines.get(3));
        assertEquals("\"Team 2\",\"Arya.Stark@" + People.DOMAIN + "\"", lines.get(4));
    }

    @Test
    public void testExportAllPeopleWithEmptyTeams() throws IOException {
        // Create some test data
        List<Team> teams = new ArrayList<>();

        // Call the method being tested
        int day = 6; // Saturday
        App.exportAllPeople(teams, day, Paths.get(TARGET_TESTS));

        // Verify that the output file was created but is empty
        String outputFilename = TARGET_TESTS + "/all_people_SATURDAY.csv";
        Path destPath = Paths.get(outputFilename);
        assertTrue(Files.exists(destPath));
        List<String> lines = Files.readAllLines(destPath);
        assertEquals(1, lines.size()); // 1 line: just the header row
    }

    @Test
    public void testExportAllPeopleWithIOException() {
        // Create some test data
        List<Team> teams = new ArrayList<>();
        Team team1 = new Team("Team 1");
        team1.addMember(new People("John", "Snow"));
        teams.add(team1);

        // Call the method being tested, but simulate an IOException when writing to the output file
        int day = 8; // Monday
        assertThrows(RuntimeException.class, () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            System.setOut(new PrintStream(out));
            App.exportAllPeople(teams, day, Paths.get(TARGET_TESTS));
        });
    }
}