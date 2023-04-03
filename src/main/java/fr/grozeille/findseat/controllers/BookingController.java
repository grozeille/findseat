package fr.grozeille.findseat.controllers;

import fr.grozeille.findseat.FindSeatApplicationProperties;
import fr.grozeille.findseat.model.Booking;
import fr.grozeille.findseat.model.BookingType;
import fr.grozeille.findseat.model.Room;
import fr.grozeille.findseat.model.Team;
import fr.grozeille.findseat.services.ConfigService;
import fr.grozeille.findseat.services.FileBookingService;
import fr.grozeille.findseat.services.TeamDaysService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/booking")
public class BookingController {

    public static final String MIME_TYPE_CSV = "text/csv";

    private final FindSeatApplicationProperties config;

    private final TeamDaysService teamDaysService;

    private final FileBookingService fileBookingService;

    private final ConfigService configService;

    public BookingController(FindSeatApplicationProperties config, TeamDaysService teamDaysService, FileBookingService fileBookingService, ConfigService configService) {
        this.config = config;
        this.teamDaysService = teamDaysService;
        this.fileBookingService = fileBookingService;
        this.configService = configService;
    }

    @GetMapping("/next-week")
    public int nextWeek() {
        return fileBookingService.nextWeek();
    }

    @GetMapping("")
    public Booking getBooking() {

        // TODO: read from DB

        int nextWeek = fileBookingService.nextWeek();
        Booking booking = new Booking();
        booking.setWeek(nextWeek);
        booking.setTuesday(BookingType.MANDATORY);
        booking.setFriday(BookingType.MANDATORY);
        return booking;
    }

    @PostMapping("")
    public ResponseEntity saveBooking(@RequestBody Booking booking) {
        int nextWeek = fileBookingService.nextWeek();

        if(booking.getWeek() < nextWeek) {
            return ResponseEntity.badRequest().body("Week must be in the future, w"+nextWeek+" or higher");
        }

        try {
            teamDaysService.verifyTeamDays(booking, "anonymous@worldcompany.com");
        } catch (Exception e) {
            log.warn("Failed to verify team days", e);
            ResponseEntity.badRequest().body("Invalid team days for the current user");
        }

        // TODO: save in DB

        return ResponseEntity.ok("");
    }

    @PostMapping(value="/batch-sample")
    public ResponseEntity sampleBatchBooking() throws Exception {
        List<Room> rooms = configService.parseRoomsFile();
        int totalRoomSize = rooms.stream().mapToInt(r -> r.roomSize()).sum();
        Map<Integer, List<Team>> teamsForWeek = fileBookingService.generateSampleTeamForWeek(totalRoomSize);
        File outputFile = new File(fileBookingService.getInputFolder(), "allteams.csv");
        fileBookingService.writeTeamForWeekFile(teamsForWeek, outputFile);
        return ResponseEntity.ok("OK");
    }

    @DeleteMapping(value="/batch/{fileName}")
    public ResponseEntity deleteBatchBooking(@PathVariable String fileName) throws Exception {
        deleteBookingFile(fileName);
        return ResponseEntity.ok("OK");
    }

    @PostMapping(value="/batch/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity batchBooking(
            @Parameter(description = "File to upload", required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (!originalFilename.endsWith(".csv")) {
            return ResponseEntity.badRequest().body("Only CSV files are allowed");
        }

        writeBookingFile(file);

        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/batch/")
    public ResponseEntity<String[]> getBookingFileList() throws Exception {
        return ResponseEntity.ok(listBookingFiles());
    }

    @GetMapping(value = "/batch/{fileName}", produces = MIME_TYPE_CSV)
    public ResponseEntity<byte[]> getBookingFile(@PathVariable String fileName) throws Exception {
        byte[] csvData = readBookingFile(fileName);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+fileName);

        return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType(MIME_TYPE_CSV)).body(csvData);
    }

    private void deleteBookingFile(String fileName) throws Exception {
        new File(fileBookingService.getInputFolder(), fileName).delete();
    }
    private void writeBookingFile(MultipartFile file) throws Exception {
        writeFile(file, fileBookingService.getInputFolder(), file.getOriginalFilename());
    }

    private String[] listBookingFiles() throws Exception {
        File inputFolder = fileBookingService.getInputFolder();
        return inputFolder.list();
    }

    private byte[] readBookingFile(String fileName) throws Exception {
        return readFile(fileBookingService.getInputFolder(), fileName);
    }

    private byte[] readFile(File parentFolder, String fileName) throws Exception {
        File file = new File(parentFolder, fileName);
        if(!file.exists()) {
            return new byte[0];
        }

        return Files.readAllBytes(file.toPath());
    }

    private void writeFile(MultipartFile file, File parentFolder, String fileName) throws Exception {
        File fileToWrite = new File(parentFolder, fileName);
        if(fileToWrite.exists()) {
            fileToWrite.delete();
        }
        Files.copy(file.getInputStream(), fileToWrite.toPath());
    }
}
