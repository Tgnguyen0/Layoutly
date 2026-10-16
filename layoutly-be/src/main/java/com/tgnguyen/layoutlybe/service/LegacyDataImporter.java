package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Explicit, idempotent import. Never modifies source files or infers snapshot ownership. */
@Component
public class LegacyDataImporter implements ApplicationRunner {
 private final ObjectMapper mapper;
 private final UserRepository users;
 private final SessionRepository sessions;
 private final SavedProjectService projects;
 private final FigmaSnapshotService snapshots;
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transaction;
 private final Path directory;
 private final String ownershipFile;
 private final boolean enabled;
 public LegacyDataImporter(ObjectMapper mapper,UserRepository users,SessionRepository sessions,SavedProjectService projects,
  FigmaSnapshotService snapshots,JdbcTemplate jdbc,PlatformTransactionManager manager,
  @Value("${layoutly.data.directory:layoutly-data}") String directory,
  @Value("${layoutly.migration.legacy-enabled:false}") boolean enabled,
  @Value("${layoutly.migration.ownership-file:}") String ownershipFile) {
  this.mapper=mapper; this.users=users; this.sessions=sessions; this.projects=projects; this.snapshots=snapshots; this.jdbc=jdbc;
  this.transaction=new TransactionTemplate(manager); this.directory=Path.of(directory).toAbsolutePath().normalize();
  this.enabled=enabled; this.ownershipFile=ownershipFile;
 }
 @Override public void run(ApplicationArguments args) throws Exception {
  if(!enabled) return;
  Path accounts=directory.resolve("accounts/accounts.json");
  if(Files.isRegularFile(accounts)) importAccounts(accounts);
  if(!ownershipFile.isBlank()) importOwnership(Path.of(ownershipFile).toAbsolutePath().normalize());
 }
 public void importAccounts(Path file) throws Exception {
  byte[] source=Files.readAllBytes(file);
  AccountService.Store store=mapper.readValue(source,AccountService.Store.class);
  if(store.users()==null||store.sessions()==null) throw new IllegalStateException("Invalid legacy account store; source preserved.");
  transaction.executeWithoutResult(status->{
   if(alreadyImported(file,source)) return;
   for(AccountService.StoredUser item:store.users()) {
    var p=item.profile(); UUID id=UUID.fromString(p.id());
    if(users.existsById(id)||users.findByEmailAndDeletedAtIsNull(AccountService.email(p.email())).isPresent())
     throw new IllegalStateException("Legacy user conflicts with existing data; import rolled back. Resolve explicitly before retrying.");
    if(item.passwordHash()==null||!item.passwordHash().matches("600000:[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+"))
     throw new IllegalStateException("Unsupported legacy password hash; import rolled back.");
    UserEntity user=new UserEntity(); user.id=id; user.email=AccountService.email(p.email()); user.displayName=p.displayName();
    user.company=p.company()==null?"":p.company(); user.bio=p.bio()==null?"":p.bio(); user.passwordHash=item.passwordHash();
    user.createdAt=Instant.parse(p.createdAt()); user.updatedAt=Instant.parse(p.updatedAt()); users.saveAndFlush(user);
   }
   for(AccountService.Session item:store.sessions()) {
    if(!Instant.parse(item.expiresAt()).isAfter(Instant.now())) continue;
    if(!item.digest().matches("[a-f0-9]{64}")) throw new IllegalStateException("Invalid legacy session digest.");
    SessionEntity session=new SessionEntity(); session.id=UUID.fromString(item.id()); session.userId=UUID.fromString(item.userId());
    session.digest=item.digest(); session.createdAt=Instant.parse(item.createdAt()); session.expiresAt=Instant.parse(item.expiresAt());
    session.rememberMe=item.rememberMe(); session.device=item.device(); sessions.saveAndFlush(session);
   }
   mark(file,source,store.users().size());
  });
 }
 private void importOwnership(Path file) throws Exception {
  byte[] source=Files.readAllBytes(file);
  var root=mapper.readTree(source);
  if(!root.isObject()) throw new IllegalStateException("Ownership manifest must map snapshot IDs to user UUIDs.");
  transaction.executeWithoutResult(status->{
   if(alreadyImported(file,source)) return;
   root.fields().forEachRemaining(entry->{
    UUID user=UUID.fromString(entry.getValue().asText());
    if(!users.existsById(user)) throw new IllegalStateException("Unknown owner in legacy manifest.");
    projects.importLegacyProject(user,entry.getKey());
   });
   mark(file,source,root.size());
  });
 }
 private boolean alreadyImported(Path file,byte[] bytes) {
  // Serializes simultaneous startup imports across application instances.
  jdbc.execute("SELECT pg_advisory_xact_lock(73192501)");
  List<String> hashes=jdbc.queryForList("SELECT sha256 FROM legacy_imports WHERE source=?",String.class,file.toString());
  if(hashes.isEmpty()) return false;
  if(!hashes.get(0).equals(hash(bytes))) throw new IllegalStateException("Legacy source changed since import; preserved for manual reconciliation.");
  return true;
 }
 private void mark(Path file,byte[] bytes,int count) {
  jdbc.update("INSERT INTO legacy_imports(source,sha256,imported_at,metadata) VALUES (?,?,?,jsonb_build_object('count',?::int))",
   file.toString(),hash(bytes),java.sql.Timestamp.from(Instant.now()),count);
 }
 private String hash(byte[] bytes) {
  try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
  catch(Exception ex) { throw new IllegalStateException(ex); }
 }
}
