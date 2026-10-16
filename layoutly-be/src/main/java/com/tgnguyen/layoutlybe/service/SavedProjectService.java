package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.persistence.*;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import com.tgnguyen.layoutlybe.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @Transactional
public class SavedProjectService {
 private final ProjectRepository projects;
 private final SnapshotRepository snapshots;
 private final ProjectVersionRepository versions;
 private final ProjectAssetRepository assets;
 private final ExportRepository exports;
 private final FigmaSnapshotService figma;
 private final com.tgnguyen.layoutlybe.service.ai.ProjectWorkspaceService workspaces;
 private final ObjectMapper mapper;
 public record ProjectView(UUID id,String name,String description,String framework,String snapshotId,
    Map<String,Object> metadata,Instant createdAt,Instant updatedAt,Instant openedAt,long version) {}
 public record Save(String name,String description,String framework,String snapshotId,Map<String,Object> metadata,Long version) {}
 public SavedProjectService(ProjectRepository projects,SnapshotRepository snapshots,ProjectVersionRepository versions,
   ProjectAssetRepository assets,ExportRepository exports,FigmaSnapshotService figma,
   com.tgnguyen.layoutlybe.service.ai.ProjectWorkspaceService workspaces,ObjectMapper mapper) {
  this.projects=projects; this.snapshots=snapshots; this.versions=versions; this.assets=assets;
  this.exports=exports; this.figma=figma; this.workspaces=workspaces; this.mapper=mapper;
 }
 public void registerSnapshot(UUID owner,FigmaSnapshot snapshot) {
  SnapshotEntity old=snapshots.findById(snapshot.getSnapshotId()).orElse(null);
  if(old!=null&&!old.ownerId.equals(owner)) throw missing();
  SnapshotEntity row=old==null?new SnapshotEntity():old;
  row.id=snapshot.getSnapshotId(); row.ownerId=owner;
  row.metadata=snapshotMetadata(snapshot); row.createdAt=old==null?Instant.now():old.createdAt;
  snapshots.saveAndFlush(row);
 }
 public void importLegacyProject(UUID owner,String snapshotId) {
  FigmaSnapshot snapshot=figma.requireSnapshot(snapshotId);
  registerSnapshot(owner,snapshot);
  if(!projects.findByOwnerIdAndSnapshotIdAndDeletedAtIsNull(owner,snapshotId).isEmpty()) return;
  ProjectEntity p=new ProjectEntity(); p.id=UUID.randomUUID(); p.ownerId=owner; p.snapshotId=snapshotId;
  p.name=AccountService.text(snapshot.getFileName()==null?"Imported design":snapshot.getFileName().substring(0,Math.min(160,snapshot.getFileName().length())),160,true,"Tên dự án");
  p.createdAt=snapshot.getFetchedAt(); p.updatedAt=Instant.now();
  projects.saveAndFlush(p); checkpoint(p,null);
 }
 @Transactional(readOnly=true)
 public void requireSnapshot(UUID owner,String id) {
  snapshots.findByIdAndOwnerId(id,owner).orElseThrow(SavedProjectService::missing);
 }
 @Transactional(readOnly=true)
 public List<ProjectView> list(String query,String framework) {
  String search=query==null?"":query.strip().toLowerCase(Locale.ROOT);
  if(search.length()>160) throw new IllegalArgumentException("Từ khóa tối đa 160 ký tự.");
  return projects.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(CurrentUser.id()).stream()
   .filter(p->p.name.toLowerCase(Locale.ROOT).contains(search))
   .filter(p->framework==null||framework.isBlank()||p.framework.equals(framework))
   .map(this::view).toList();
 }
 @Transactional(readOnly=true)
 public ProjectView get(UUID id) { return view(owned(id)); }
 public ProjectView create(Save input) {
  ProjectEntity p=new ProjectEntity(); p.id=UUID.randomUUID(); p.ownerId=CurrentUser.id();
  p.createdAt=Instant.now(); p.updatedAt=p.createdAt; apply(p,input,true); projects.saveAndFlush(p);
  if(p.snapshotId!=null) checkpoint(p,null);
  return view(p);
 }
 public ProjectView update(UUID id,Save input) {
  ProjectEntity p=owned(id);
  if(input.version()!=null&&input.version()!=p.lockVersion) throw new AccountService.AccountException(HttpStatus.CONFLICT,"Dự án đã thay đổi. Vui lòng tải lại.");
  String previous=p.snapshotId; apply(p,input,false); p.updatedAt=Instant.now(); projects.saveAndFlush(p);
  if(p.snapshotId!=null&&(!Objects.equals(previous,p.snapshotId)||input.snapshotId()!=null)) checkpoint(p,null);
  return view(p);
 }
 public ProjectView reopen(UUID id) {
  ProjectEntity p=owned(id);
  if(p.snapshotId!=null) { requireSnapshot(p.ownerId,p.snapshotId); figma.requireSnapshot(p.snapshotId); }
  Instant openedAt=Instant.now();
  projects.touchOpenedAt(id,p.ownerId,openedAt);
  // The bulk update detaches p, and intentionally leaves the content version unchanged.
  p.openedAt=openedAt; return view(p);
 }
 public void delete(UUID id) { ProjectEntity p=owned(id); p.deletedAt=Instant.now(); p.updatedAt=p.deletedAt; }
 public ProjectView duplicate(UUID id) {
  ProjectEntity original=owned(id);
  ProjectEntity copy=new ProjectEntity(); copy.id=UUID.randomUUID(); copy.ownerId=original.ownerId;
  copy.name=original.name.substring(0,Math.min(150,original.name.length()))+" (bản sao)";
  copy.description=original.description; copy.framework=original.framework;
  copy.metadata=new LinkedHashMap<>(original.metadata); copy.createdAt=Instant.now(); copy.updatedAt=copy.createdAt;
  if(original.snapshotId!=null) {
   FigmaSnapshot clone=figma.copySnapshot(original.snapshotId);
   registerSnapshot(copy.ownerId,clone); copy.snapshotId=clone.getSnapshotId();
   workspaces.copyProject(original.snapshotId,copy.snapshotId);
  }
  projects.saveAndFlush(copy);
  if(copy.snapshotId!=null) checkpoint(copy,null);
  return view(copy);
 }
 @Transactional(readOnly=true)
 public List<ProjectVersionEntity> versions(UUID id) { owned(id); return versions.findByProjectIdOrderByCreatedAtDesc(id); }
 @Transactional(readOnly=true)
 public List<ProjectAssetEntity> assets(UUID id) { owned(id); return assets.findByProjectId(id); }
 @Transactional(readOnly=true)
 public List<ExportEntity> exports(UUID id) { owned(id); return exports.findByProjectIdOrderByCreatedAtDesc(id); }
 public ProjectView saveVersion(UUID id,String revisionId) {
  ProjectEntity p=owned(id);
  if(p.snapshotId==null) throw new IllegalArgumentException("Hãy import thiết kế trước khi lưu phiên bản.");
  if(revisionId!=null&&!workspaces.knownRevision(p.snapshotId,revisionId)) throw new IllegalArgumentException("Revision không tồn tại.");
  checkpoint(p,revisionId); p.updatedAt=Instant.now(); projects.saveAndFlush(p); return view(p);
 }
 public void recordExport(UUID owner,String snapshotId,String format,String revisionId,int byteSize) {
  requireSnapshot(owner,snapshotId);
  for(ProjectEntity p:projects.findByOwnerIdAndSnapshotIdAndDeletedAtIsNull(owner,snapshotId)) {
   syncAssets(p,figma.requireSnapshot(snapshotId));
   ExportEntity e=new ExportEntity(); e.id=UUID.randomUUID(); e.projectId=p.id; e.snapshotId=snapshotId;
   e.format="HTML".equalsIgnoreCase(format)?"HTML":"REACT"; e.revisionId=revisionId; e.byteSize=byteSize; e.createdAt=Instant.now();
   exports.save(e);
  }
 }
 private void apply(ProjectEntity p,Save input,boolean creating) {
  if(input==null) throw new IllegalArgumentException("Thiếu thông tin dự án.");
  if(input.name()!=null||creating) p.name=AccountService.text(input.name(),160,true,"Tên dự án");
  if(input.description()!=null) p.description=AccountService.text(input.description(),2000,false,"Mô tả");
  if(input.framework()!=null) {
   if(!List.of("HTML","REACT").contains(input.framework())) throw new IllegalArgumentException("Framework không hợp lệ.");
   p.framework=input.framework();
  }
  if(input.snapshotId()!=null) {
   requireSnapshot(p.ownerId,input.snapshotId()); figma.requireSnapshot(input.snapshotId()); p.snapshotId=input.snapshotId();
  }
  if(input.metadata()!=null) {
   if(mapper.valueToTree(input.metadata()).toString().length()>16000) throw new IllegalArgumentException("Metadata quá lớn.");
   p.metadata=new LinkedHashMap<>(input.metadata());
  }
 }
 private void checkpoint(ProjectEntity p,String revisionId) {
  FigmaSnapshot snapshot=figma.requireSnapshot(p.snapshotId);
  ProjectVersionEntity v=new ProjectVersionEntity(); v.id=UUID.randomUUID(); v.projectId=p.id;
  v.snapshotId=p.snapshotId; v.revisionId=revisionId; v.designNode=mapper.convertValue(snapshot.getDesignIr(),new TypeReference<>() {});
  v.metadata=snapshotMetadata(snapshot); v.createdAt=Instant.now(); versions.save(v); syncAssets(p,snapshot);
 }
 private void syncAssets(ProjectEntity p,FigmaSnapshot snapshot) {
  if(snapshot.getAssets()==null) return;
  snapshot.getAssets().zipAssetByPath().forEach((path,bytes)->{
   ProjectAssetEntity a=assets.findByProjectIdAndSnapshotIdAndPath(p.id,snapshot.getSnapshotId(),path).orElseGet(ProjectAssetEntity::new);
   if(a.id==null) a.id=UUID.randomUUID();
   a.projectId=p.id; a.snapshotId=snapshot.getSnapshotId(); a.path=path; a.byteSize=bytes.length;
   try { a.sha256=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
   catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
   assets.save(a);
  });
 }
 private ProjectEntity owned(UUID id) { return projects.findByIdAndOwnerIdAndDeletedAtIsNull(id,CurrentUser.id()).orElseThrow(SavedProjectService::missing); }
 private static AccountService.AccountException missing() { return new AccountService.AccountException(HttpStatus.NOT_FOUND,"Không tìm thấy dự án hoặc thiết kế."); }
 private ProjectView view(ProjectEntity p) { return new ProjectView(p.id,p.name,p.description,p.framework,p.snapshotId,p.metadata,p.createdAt,p.updatedAt,p.openedAt,p.lockVersion); }
 private Map<String,Object> snapshotMetadata(FigmaSnapshot s) {
  Map<String,Object> m=new LinkedHashMap<>();
  m.put("source",s.getSource().name()); m.put("fileKey",s.getFileKey()); m.put("fileName",s.getFileName());
  m.put("figmaVersion",s.getFigmaVersion()); m.put("rootNodeId",s.getRootNodeId());
  m.put("previewUrl","/api/snapshots/"+s.getSnapshotId()+"/preview"); m.put("workspaceSnapshotId",s.getSnapshotId());
  return m;
 }
}
