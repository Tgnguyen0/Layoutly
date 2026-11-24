package com.tgnguyen.layoutlybe.project;

import com.tgnguyen.layoutlybe.auth.AuthService;
import com.tgnguyen.layoutlybe.auth.UserAccount;
import com.tgnguyen.layoutlybe.project.ProjectDtos.CreateRequest;
import com.tgnguyen.layoutlybe.project.ProjectDtos.Download;
import com.tgnguyen.layoutlybe.project.ProjectDtos.ProjectDetail;
import com.tgnguyen.layoutlybe.project.ProjectDtos.ProjectSummary;
import com.tgnguyen.layoutlybe.project.ProjectDtos.UpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Du an da luu cua nguoi dung. Moi endpoint deu yeu cau dang nhap va chi thao tac tren du an cua chinh ho. */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final AuthService auth;
    private final ProjectService projects;

    public ProjectController(AuthService auth, ProjectService projects) {
        this.auth = auth;
        this.projects = projects;
    }

    @GetMapping
    public List<ProjectSummary> list(HttpServletRequest request) {
        return projects.list(auth.requireUser(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProjectDetail> create(@RequestBody CreateRequest body, HttpServletRequest request) {
        UserAccount user = auth.requireUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(projects.create(user, body));
    }

    @GetMapping("/{id}")
    public ProjectDetail get(@PathVariable String id, HttpServletRequest request) {
        return projects.get(auth.requireUser(request), id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProjectDetail update(@PathVariable String id, @RequestBody UpdateRequest body, HttpServletRequest request) {
        return projects.update(auth.requireUser(request), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        projects.delete(auth.requireUser(request), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, HttpServletRequest request) {
        Download download = projects.download(auth.requireUser(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .body(download.zip());
    }
}
