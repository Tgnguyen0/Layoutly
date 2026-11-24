package com.tgnguyen.layoutlybe.project;

import com.tgnguyen.layoutlybe.auth.UserAccount;
import com.tgnguyen.layoutlybe.exception.ApiException;
import com.tgnguyen.layoutlybe.project.ProjectDtos.CreateRequest;
import com.tgnguyen.layoutlybe.project.ProjectDtos.Download;
import com.tgnguyen.layoutlybe.project.ProjectDtos.FileInput;
import com.tgnguyen.layoutlybe.project.ProjectDtos.FileView;
import com.tgnguyen.layoutlybe.project.ProjectDtos.ProjectDetail;
import com.tgnguyen.layoutlybe.project.ProjectDtos.ProjectSummary;
import com.tgnguyen.layoutlybe.project.ProjectDtos.UpdateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ProjectService {
    static final int MAX_FILES = 500;
    static final int MAX_FILE_BYTES = 1024 * 1024;
    static final long MAX_PROJECT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> FRAMEWORKS = Set.of("REACT", "HTML");

    private final ProjectRepository projects;
    private final ProjectFileRepository files;
    private final int maxPerUser;

    public ProjectService(ProjectRepository projects,
                          ProjectFileRepository files,
                          @Value("${layoutly.projects.max-per-user:50}") int maxPerUser) {
        this.projects = projects;
        this.files = files;
        this.maxPerUser = maxPerUser;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list(UserAccount owner) {
        return projects.findByOwnerIdOrderByUpdatedAtDesc(owner.getId()).stream().map(ProjectService::summary).toList();
    }

    @Transactional
    public ProjectDetail create(UserAccount owner, CreateRequest request) {
        if (projects.countByOwnerId(owner.getId()) >= maxPerUser) {
            throw new ApiException(HttpStatus.CONFLICT, "Bạn đã đạt giới hạn " + maxPerUser + " dự án. Hãy xóa bớt dự án cũ.");
        }
        Project project = projects.save(new Project(owner, requireName(request.name()),
                requireFramework(request.framework()), cleanSnapshotId(request.snapshotId())));
        if (request.files() != null) upsertFiles(project, request.files(), false);
        return detail(project);
    }

    @Transactional(readOnly = true)
    public ProjectDetail get(UserAccount owner, String id) {
        return detail(find(owner, id));
    }

    @Transactional
    public ProjectDetail update(UserAccount owner, String id, UpdateRequest request) {
        Project project = find(owner, id);
        if (request.name() != null) project.setName(requireName(request.name()));
        if (request.files() != null) upsertFiles(project, request.files(), Boolean.TRUE.equals(request.replaceFiles()));
        project.touch();
        return detail(project);
    }

    @Transactional
    public void delete(UserAccount owner, String id) {
        Project project = find(owner, id);
        files.deleteByProjectId(project.getId());
        projects.delete(project);
    }

    @Transactional(readOnly = true)
    public Download download(UserAccount owner, String id) {
        Project project = find(owner, id);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (ProjectFile file : files.findByProjectIdOrderByPath(project.getId())) {
                zip.putNextEntry(new ZipEntry(file.getPath()));
                zip.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String safeName = project.getName().replaceAll("[^a-zA-Z0-9-_]", "_");
        return new Download((safeName.isBlank() ? "project" : safeName) + ".zip", out.toByteArray());
    }

    private void upsertFiles(Project project, List<FileInput> inputs, boolean replace) {
        if (inputs.size() > MAX_FILES) throw tooLarge("Tối đa " + MAX_FILES + " file mỗi dự án.");

        Map<String, String> incoming = new LinkedHashMap<>();
        for (FileInput input : inputs) {
            if (input == null) throw bad("Danh sách file không hợp lệ.");
            String path = normalizePath(input.path());
            String content = input.content() == null ? "" : input.content();
            if (content.indexOf('\0') >= 0) throw bad("File " + path + " chứa ký tự không hợp lệ (NUL).");
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                throw tooLarge("File " + path + " vượt quá 1 MB.");
            }
            incoming.put(path, content); // cung duong dan: ban sau cung thang
        }

        Map<String, ProjectFile> existing = new LinkedHashMap<>();
        for (ProjectFile file : files.findByProjectIdOrderByPath(project.getId())) existing.put(file.getPath(), file);

        // Kiem tra gioi han tren tap file SAU khi gop, truoc khi ghi bat cu thu gi.
        Map<String, Long> finalSizes = new LinkedHashMap<>();
        if (!replace) existing.forEach((path, file) -> finalSizes.put(path, (long) file.getSizeBytes()));
        incoming.forEach((path, content) -> finalSizes.put(path, (long) content.getBytes(StandardCharsets.UTF_8).length));
        if (finalSizes.size() > MAX_FILES) throw tooLarge("Tối đa " + MAX_FILES + " file mỗi dự án.");
        if (finalSizes.values().stream().mapToLong(Long::longValue).sum() > MAX_PROJECT_BYTES) {
            throw tooLarge("Tổng dung lượng dự án vượt quá 10 MB.");
        }

        if (replace) {
            List<ProjectFile> stale = new ArrayList<>();
            existing.forEach((path, file) -> { if (!incoming.containsKey(path)) stale.add(file); });
            files.deleteAll(stale);
        }
        incoming.forEach((path, content) -> {
            ProjectFile current = existing.get(path);
            if (current != null) current.setContent(content);
            else files.save(new ProjectFile(project, path, content));
        });
        project.touch();
    }

    private Project find(UserAccount owner, String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
        return projects.findByIdAndOwnerId(uuid, owner.getId()).orElseThrow(ProjectService::notFound);
    }

    private ProjectDetail detail(Project project) {
        List<FileView> views = files.findByProjectIdOrderByPath(project.getId()).stream()
                .map(f -> new FileView(f.getPath(), f.getContent(), f.getUpdatedAt()))
                .toList();
        return new ProjectDetail(summary(project), views);
    }

    private static ProjectSummary summary(Project p) {
        return new ProjectSummary(p.getId().toString(), p.getName(), p.getFramework(), p.getSnapshotId(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private static String requireName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 120) throw bad("Tên dự án cần từ 1 đến 120 ký tự.");
        return clean;
    }

    private static String requireFramework(String framework) {
        String clean = framework == null || framework.isBlank() ? "REACT" : framework.trim().toUpperCase(Locale.ROOT);
        if (!FRAMEWORKS.contains(clean)) throw bad("Framework chỉ nhận REACT hoặc HTML.");
        return clean;
    }

    private static String cleanSnapshotId(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) return null;
        String clean = snapshotId.trim();
        if (clean.length() > 100) throw bad("snapshotId quá dài.");
        return clean;
    }

    /** Chi nhan duong dan tuong doi, dung dau "/", khong co "." / ".." / ky tu dieu khien. */
    static String normalizePath(String raw) {
        String path = raw == null ? "" : raw.trim().replace('\\', '/');
        if (path.isEmpty() || path.length() > 300 || path.startsWith("/") || path.contains(":")) {
            throw bad("Đường dẫn file không hợp lệ: " + raw);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw bad("Đường dẫn file không hợp lệ: " + raw);
            }
        }
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) throw bad("Đường dẫn file không hợp lệ.");
        }
        return path;
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static ApiException tooLarge(String message) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy dự án.");
    }
}
