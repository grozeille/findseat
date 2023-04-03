package fr.grozeille.findseat.controllers;

import fr.grozeille.findseat.FindSeatApplicationProperties;
import fr.grozeille.findseat.services.ConfigService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.File;
import java.nio.file.Files;

@RestController
@Slf4j
@RequestMapping("/admin/config")
public class AdminConfigController {

    public static final String MIME_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MIME_TYPE_CSV = "text/csv";

    private final FindSeatApplicationProperties config;

    private final ConfigService configService;

    public AdminConfigController(FindSeatApplicationProperties config, ConfigService configService) {
        this.config = config;
        this.configService = configService;
    }

    @GetMapping(value="/floor-plan", produces = MIME_TYPE_XLSX)
    public ResponseEntity<byte[]> getFloorPlan() throws Exception {
        byte[] xlsxData = readFile(ConfigService.FILE_FLOOR_PLAN);
        if(xlsxData.length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+ ConfigService.FILE_FLOOR_PLAN);
        return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType(MIME_TYPE_XLSX)).body(xlsxData);
    }

    @PostMapping(value="/floor-plan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity saveFloorPlan(
            @Parameter(description = "File to upload", required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) throws Exception {

        String fileName = file.getOriginalFilename();
        if (!fileName.endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body("Only XLSX files are allowed");
        }

        writeFile(file, ConfigService.FILE_FLOOR_PLAN);

        return ResponseEntity.ok("");
    }

    @GetMapping(value = "/team-days", produces = MIME_TYPE_CSV)
    public ResponseEntity<byte[]> getTeamDays() throws Exception {
        byte[] fileData = readFile(ConfigService.FILE_TEAM_DAYS);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+ ConfigService.FILE_TEAM_DAYS);

        return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType(MIME_TYPE_CSV)).body(fileData);
    }

    @PostMapping(value="/team-days", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity saveTeamDays(
            @Parameter(description = "File to upload", required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (!fileName.endsWith(".csv")) {
            return ResponseEntity.badRequest().body("Only CSV files are allowed");
        }

        writeFile(file, ConfigService.FILE_TEAM_DAYS);

        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/rooms", produces = MIME_TYPE_CSV)
    public ResponseEntity<byte[]> getRooms() throws Exception {
        byte[] fileData = readFile(ConfigService.FILE_ROOMS);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+ConfigService.FILE_ROOMS);

        return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType(MIME_TYPE_CSV)).body(fileData);
    }

    @PostMapping(value="/rooms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity saveRooms(
            @Parameter(description = "File to upload", required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (!fileName.endsWith(".csv")) {
            return ResponseEntity.badRequest().body("Only CSV files are allowed");
        }

        writeFile(file, ConfigService.FILE_ROOMS);

        return ResponseEntity.ok("OK");
    }

    private byte[] readFile(String fileName) throws Exception {
        File parentFolder = configService.getConfigFolder();

        File file = new File(parentFolder, fileName);
        if(!file.exists()) {
            return new byte[0];
        }

        return Files.readAllBytes(file.toPath());
    }

    private void writeFile(MultipartFile file, String fileName) throws Exception {
        File parentFolder = configService.getConfigFolder();
        File fileToWrite = new File(parentFolder, fileName);
        if(fileToWrite.exists()) {
            fileToWrite.delete();
        }
        Files.copy(file.getInputStream(), fileToWrite.toPath());
    }

}
