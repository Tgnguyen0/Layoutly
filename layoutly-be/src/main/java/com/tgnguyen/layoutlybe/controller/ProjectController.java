package com.tgnguyen.layoutlybe.controller;
import com.tgnguyen.layoutlybe.service.SavedProjectService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/projects")
public class ProjectController {
 private final SavedProjectService projects;
 public ProjectController(SavedProjectService projects) { this.projects=projects; }
 @GetMapping public Map<String,?> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) String framework) { return Map.of("projects",projects.list(q,framework)); }
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public Map<String,?> create(@RequestBody SavedProjectService.Save input) { return Map.of("project",projects.create(input)); }
 @GetMapping("/{id}") public Map<String,?> get(@PathVariable UUID id) { return Map.of("project",projects.get(id)); }
 @PatchMapping("/{id}") public Map<String,?> update(@PathVariable UUID id,@RequestBody SavedProjectService.Save input) { return Map.of("project",projects.update(id,input)); }
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id) { projects.delete(id); }
 @PostMapping("/{id}/duplicate") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public Map<String,?> duplicate(@PathVariable UUID id) { return Map.of("project",projects.duplicate(id)); }
 @PostMapping("/{id}/reopen") public Map<String,?> reopen(@PathVariable UUID id) { return Map.of("project",projects.reopen(id)); }
 @GetMapping("/{id}/versions") public Map<String,?> versions(@PathVariable UUID id) { return Map.of("versions",projects.versions(id)); }
 @GetMapping("/{id}/assets") public Map<String,?> assets(@PathVariable UUID id) { return Map.of("assets",projects.assets(id)); }
 @GetMapping("/{id}/exports") public Map<String,?> exports(@PathVariable UUID id) { return Map.of("exports",projects.exports(id)); }
 @PostMapping("/{id}/versions") public Map<String,?> save(@PathVariable UUID id,@RequestBody(required=false) Map<String,String> input) {
  return Map.of("project",projects.saveVersion(id,input==null?null:input.get("revisionId")));
 }
}
