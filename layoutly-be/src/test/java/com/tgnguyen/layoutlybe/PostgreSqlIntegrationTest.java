package com.tgnguyen.layoutlybe;

import com.fasterxml.jackson.databind.*;
import com.tgnguyen.layoutlybe.persistence.*;
import com.tgnguyen.layoutlybe.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.*;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import com.sun.net.httpserver.HttpServer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Requires real PostgreSQL: Testcontainers by default; explicit isolated local DB is supported. */
@SpringBootTest @AutoConfigureMockMvc
class PostgreSqlIntegrationTest {
 static final Path DATA;
 static final HttpServer FIGMA;
 static PostgreSQLContainer<?> postgres;
 static {
  try {
   DATA=Files.createTempDirectory("layoutly-pg-integration-");
   FIGMA=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
   FIGMA.createContext("/v1/files/",exchange->{
    byte[] json=PostgreSqlIntegrationTest.class.getResourceAsStream("/figma-fixtures/horizontal/figma-raw.json").readAllBytes();
    exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,json.length);
    exchange.getResponseBody().write(json); exchange.close();
   });
   FIGMA.start();
  } catch(IOException ex) { throw new ExceptionInInitializerError(ex); }
 }
 @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
  String external=System.getenv("LAYOUTLY_TEST_DATABASE_URL");
  if(external==null||external.isBlank()) {
   postgres=new PostgreSQLContainer<>("postgres:16-alpine"); postgres.start();
   r.add("spring.datasource.url",postgres::getJdbcUrl); r.add("spring.datasource.username",postgres::getUsername); r.add("spring.datasource.password",postgres::getPassword);
  } else {
   r.add("spring.datasource.url",()->external+(external.contains("?")?"&":"?")+"currentSchema=layoutly_integration");
   r.add("spring.datasource.username",()->System.getenv().getOrDefault("LAYOUTLY_TEST_DATABASE_USER","layoutly_test"));
   r.add("spring.datasource.password",()->System.getenv().getOrDefault("LAYOUTLY_TEST_DATABASE_PASSWORD",""));
   r.add("spring.flyway.schemas",()->"layoutly_integration");
   r.add("spring.jpa.properties.hibernate.default_schema",()->"layoutly_integration");
  }
  r.add("layoutly.data.directory",()->DATA.toString());
  r.add("layoutly.cache.directory",()->DATA.resolve("cache").toString());
  r.add("layoutly.migration.legacy-enabled",()->false);
  r.add("figma.api.base-url",()->"http://127.0.0.1:"+FIGMA.getAddress().getPort()+"/v1");
  r.add("figma.api.min-interval-ms",()->0);
  r.add("figma.api.token",()->"integration-upstream-token");
 }
 @AfterAll static void stop() { FIGMA.stop(0); if(postgres!=null) postgres.stop(); }
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper mapper;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Autowired UserRepository users;
 @Autowired SessionRepository sessions;
 @Autowired LegacyDataImporter importer;
 @Autowired FigmaSnapshotService snapshots;
 @Autowired SavedProjectService projects;
 @Autowired AiConversationService conversations;
 @Autowired ResetTokenRepository resetTokens;
 @Autowired AccountService accounts;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 static final String PASSWORD="integration-test-password-123";

 class Browser {
  Cookie auth;
  MockHttpSession csrfSession;
  String csrf;
  void handshake() throws Exception {
   var req=get("/api/auth/csrf"); if(auth!=null) req.cookie(auth);
   var result=mvc.perform(req).andReturn(); assertThat(result.getResponse().getStatus()).isEqualTo(200);
   csrfSession=(MockHttpSession)result.getRequest().getSession(false);
   csrf=mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
  }
  JsonNode register(String email) throws Exception {
   return send("POST","/api/auth/register",Map.of("displayName","Integration User","email",email,"password",PASSWORD,"acceptedTermsVersion","1"),201);
  }
  JsonNode send(String method,String path,Object body,int expected) throws Exception {
   var request=request(org.springframework.http.HttpMethod.valueOf(method),path).contentType(MediaType.APPLICATION_JSON);
   if(auth!=null) request.cookie(auth);
   if(!method.equals("GET")) { handshake(); request.session(csrfSession).header("X-CSRF-TOKEN",csrf); }
   if(body!=null) request.content(mapper.writeValueAsString(body));
   var result=mvc.perform(request).andReturn();
   if(result.getRequest().isAsyncStarted()) { result.getAsyncResult(30000); result=mvc.perform(asyncDispatch(result)).andReturn(); }
   assertThat(result.getResponse().getStatus()).withFailMessage("%s %s: %s",method,path,result.getResponse().getContentAsString()).isEqualTo(expected);
   for(String cookie:result.getResponse().getHeaders("Set-Cookie")) {
    if(cookie.contains("Path=/api;")&&!cookie.contains("Max-Age=0")) auth=new Cookie("layoutly_session",cookie.split(";",2)[0].split("=",2)[1]);
   }
   String content=result.getResponse().getContentAsString();
   return content.isBlank()?mapper.nullNode():mapper.readTree(content);
  }
  MvcResult bytes(String path) throws Exception {
   var result=mvc.perform(get(path).cookie(auth)).andReturn();
   if(result.getRequest().isAsyncStarted()) { result.getAsyncResult(30000); result=mvc.perform(asyncDispatch(result)).andReturn(); }
   assertThat(result.getResponse().getStatus()).withFailMessage(result.getResponse().getContentAsString()).isEqualTo(200);
   return result;
  }
 }
 String uniqueEmail() { return "test-"+UUID.randomUUID()+"@example.com"; }

 @Test void completeLifecycleRestImportSaveLogoutLoginReopenAndZip() throws Exception {
  Browser b=new Browser(); String email=uniqueEmail();
  JsonNode user=b.register(email).path("user"); assertThat(user.path("id").asText()).isNotBlank();
  b.send("POST","/api/auth/logout",null,204);
  b.send("POST","/api/auth/login",Map.of("email",email,"password",PASSWORD),200);
  JsonNode snapshot=b.send("POST","/api/figma/import",Map.of("fileKey","integration-file"),200);
  String sid=snapshot.path("snapshotId").asText(); assertThat(sid).isNotBlank();
  JsonNode project=b.send("POST","/api/projects",Map.of("name","Saved Figma project","snapshotId",sid,"framework","REACT","metadata",Map.of("viewport",1440)),201).path("project");
  String id=project.path("id").asText();
  var open=b.send("POST","/api/projects/"+id+"/reopen",null,200).path("project");
  var openAgain=b.send("POST","/api/projects/"+id+"/reopen",null,200).path("project");
  assertThat(open.path("version").asLong()).isEqualTo(openAgain.path("version").asLong()).isEqualTo(project.path("version").asLong());
  b.send("POST","/api/projects/"+id+"/versions",Map.of(),200);
  b.send("POST","/api/auth/logout",null,204);
  b.send("GET","/api/projects",null,401);
  b.send("POST","/api/auth/login",Map.of("email",email,"password",PASSWORD),200);
  var reopened=b.send("POST","/api/projects/"+id+"/reopen",null,200).path("project");
  assertThat(reopened.path("snapshotId").asText()).isEqualTo(sid);
  assertThat(reopened.path("metadata").path("viewport").asInt()).isEqualTo(1440);
  byte[] zip=b.bytes("/api/snapshots/"+sid+"/export?format=REACT").getResponse().getContentAsByteArray();
  Set<String> files=new HashSet<>();
  try(var stream=new ZipInputStream(new ByteArrayInputStream(zip))) { ZipEntry entry; while((entry=stream.getNextEntry())!=null) files.add(entry.getName()); }
  assertThat(files).contains("package.json","src/App.jsx");
  assertThat(b.send("GET","/api/projects/"+id+"/exports",null,200).path("exports")).hasSize(1);
  assertThat(b.send("GET","/api/projects/"+id+"/versions",null,200).path("versions")).hasSize(2);
  assertThat(jdbc.queryForObject("SELECT jsonb_typeof(design_node) FROM project_versions WHERE project_id=? LIMIT 1",String.class,UUID.fromString(id))).isEqualTo("object");
 }

 @Test void csrfAndEveryPrivateSnapshotRouteRejectAnotherOwner() throws Exception {
  mvc.perform(get("/api/projects")).andDo(result->assertThat(result.getResponse().getStatus()).isEqualTo(401));
  mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}")).andDo(result->assertThat(result.getResponse().getStatus()).isEqualTo(403));
  Browser a=new Browser(),b=new Browser(); a.register(uniqueEmail()); b.register(uniqueEmail());
  var imported=a.send("POST","/api/figma/import",Map.of("fileKey","authorization-file"),200);
  String sid=imported.path("snapshotId").asText();
  String id=a.send("POST","/api/projects",Map.of("name","Private","snapshotId",sid),201).path("project").path("id").asText();
  b.send("GET","/api/projects/"+id,null,404);
  for(String path:List.of("","/tree","/preview","/diagnostics","/assets/secret.png","/export","/ai/files","/ai/revisions","/ai/conversation","/ai/jobs/unknown","/ai/screenshots/00000000-0000-0000-0000-000000000000"))
   b.send("GET","/api/snapshots/"+sid+path,null,404);
  b.send("POST","/api/snapshots/"+sid+"/refresh",null,404);
  b.send("POST","/api/snapshots/"+sid+"/ai/chat",Map.of("prompt","secret"),404);
  b.send("PATCH","/api/projects/"+id,Map.of("name","stolen"),404);
  b.send("POST","/api/projects/"+id+"/duplicate",null,404);
  b.send("DELETE","/api/projects/"+id,null,404);
  assertThat(b.send("GET","/api/projects",null,200).path("projects").toString()).doesNotContain(id);
 }

 @Test void searchRenameDuplicateAndSoftDeletePreserveOriginalData() throws Exception {
  Browser b=new Browser(); b.register(uniqueEmail());
  String sid=b.send("POST","/api/figma/import",Map.of("fileKey","duplicate-file"),200).path("snapshotId").asText();
  String id=b.send("POST","/api/projects",Map.of("name","Old name","snapshotId",sid),201).path("project").path("id").asText();
  b.send("PATCH","/api/projects/"+id,Map.of("name","Unique renamed"),200);
  assertThat(b.send("GET","/api/projects?q=Unique",null,200).path("projects")).hasSize(1);
  var copy=b.send("POST","/api/projects/"+id+"/duplicate",null,201).path("project");
  assertThat(copy.path("id").asText()).isNotEqualTo(id);
  assertThat(copy.path("snapshotId").asText()).isNotEqualTo(sid);
  b.send("DELETE","/api/projects/"+id,null,204);
  b.send("GET","/api/projects/"+id,null,404);
  assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM projects WHERE id=?",Boolean.class,UUID.fromString(id))).isTrue();
  assertThat(Files.isRegularFile(DATA.resolve("snapshots").resolve(sid).resolve("figma-raw.json"))).isTrue();
  b.send("POST","/api/projects/"+copy.path("id").asText()+"/reopen",null,200);
 }

 @Test void profilePasswordRotationAndExpiredSessions() throws Exception {
  Browser b=new Browser(); String email=uniqueEmail(); var user=b.register(email).path("user");
  b.send("PATCH","/api/auth/profile",Map.of("displayName","Updated Name","email",email,"company","Studio","bio","Designer"),200);
  Cookie old=b.auth;
  b.send("POST","/api/auth/password",Map.of("currentPassword",PASSWORD,"newPassword","a-new-integration-password"),200);
  assertThat(b.auth.getValue()).isNotEqualTo(old.getValue());
  assertThat(sessions.findByDigestAndKindAndExpiresAtAfter(AccountService.digest(old.getValue()),"WEB",Instant.now())).isEmpty();
  b.send("POST","/api/auth/login",Map.of("email",email,"password",PASSWORD),401);
  b.send("POST","/api/auth/login",Map.of("email",email,"password","a-new-integration-password"),200);
  jdbc.update("UPDATE user_sessions SET expires_at=? WHERE digest=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),AccountService.digest(b.auth.getValue()));
  b.send("GET","/api/projects",null,401);
 }

 @Test void legacyImportIsIdempotentPreservesFilesAndRejectsChangedSources() throws Exception {
  Path directory=Files.createTempDirectory(DATA,"legacy-"); Path file=directory.resolve("accounts.json");
  String id=UUID.randomUUID().toString(),email=uniqueEmail(),now=Instant.now().toString();
  byte[] salt=new byte[16]; new java.security.SecureRandom().nextBytes(salt);
  var spec=new javax.crypto.spec.PBEKeySpec(PASSWORD.toCharArray(),salt,600000,256);
  String hash="600000:"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
  mapper.writeValue(file.toFile(),new AccountService.Store(List.of(new AccountService.StoredUser(new AccountService.User(id,"Legacy",email,"","",now,now),hash)),List.of()));
  byte[] before=Files.readAllBytes(file);
  importer.importAccounts(file); importer.importAccounts(file);
  assertThat(Files.readAllBytes(file)).isEqualTo(before);
  Browser b=new Browser(); b.send("POST","/api/auth/login",Map.of("email",email,"password",PASSWORD),200);
  assertThat(users.findById(UUID.fromString(id)).orElseThrow().passwordHash).startsWith("{pbkdf2}");
  Files.writeString(file,"{}");
  assertThatThrownBy(()->importer.importAccounts(file)).isInstanceOf(IllegalStateException.class);
  assertThat(Files.readString(file)).isEqualTo("{}");
 }

 @Test void aiConversationSurvivesSubsequentReadsAndIsOwned() throws Exception {
  Browser a=new Browser(),b=new Browser(); a.register(uniqueEmail()); b.register(uniqueEmail());
  String sid=a.send("POST","/api/figma/import",Map.of("fileKey","conversation-file"),200).path("snapshotId").asText();
  UUID conversation=conversations.userMessage(sid,"Change the header","REACT");
  conversations.message(conversation,"assistant",Map.of("message","Saved conversation"));
  var history=a.send("GET","/api/snapshots/"+sid+"/ai/conversation",null,200).path("messages");
  assertThat(history).hasSize(2);
  assertThat(history.get(1).path("content").path("message").asText()).isEqualTo("Saved conversation");
  b.send("GET","/api/snapshots/"+sid+"/ai/conversation",null,404);
 }

 @Test void bridgePairingRequiresAuthenticationAndScopesReadAccess() throws Exception {
  Browser b=new Browser(); b.register(uniqueEmail());
  String token=b.send("POST","/api/auth/bridge-token",null,200).path("token").asText();
  assertThat(accounts.current(token)).isNull();
  assertThat(accounts.current(token,"BRIDGE")).isNotNull();
  mvc.perform(get("/api/projects").header("X-Layoutly-Bridge-Token",token))
   .andDo(result->assertThat(result.getResponse().getStatus()).isEqualTo(401));
  String sid=b.send("POST","/api/figma/import",Map.of("fileKey","bridge-read-file"),200).path("snapshotId").asText();
  accounts.bindBridge(token,sid);
  mvc.perform(get("/api/snapshots/"+sid).header("X-Layoutly-Bridge-Token",token))
   .andDo(result->assertThat(result.getResponse().getStatus()).isEqualTo(200));
  String another=b.send("POST","/api/figma/import",Map.of("fileKey","another-file"),200).path("snapshotId").asText();
  mvc.perform(get("/api/snapshots/"+another).header("X-Layoutly-Bridge-Token",token))
   .andDo(result->assertThat(result.getResponse().getStatus()).isEqualTo(401));
  jdbc.update("UPDATE user_sessions SET expires_at=? WHERE digest=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),AccountService.digest(token));
  mvc.perform(get("/api/snapshots/"+sid).header("X-Layoutly-Bridge-Token",token))
   .andDo(result->assertThat(result.getResponse().getStatus()).isEqualTo(401));
 }

 @Test @org.springframework.transaction.annotation.Transactional
 void configuredEmailResetIsOneTimeAndRevokesSessions() {
  String email=uniqueEmail();
  var login=accounts.register("Reset User",email,PASSWORD,"1","Browser");
  var mail=org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);
  var factory=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
  factory.registerSingleton("mail",mail);
  var reset=new PasswordResetService(users,sessions,resetTokens,factory.getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class),true,"https://layoutly.example","sender@example.com");
  reset.forgot(email);
  var sent=org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
  org.mockito.Mockito.verify(mail).send(sent.capture());
  String token=sent.getValue().getText().split("token=",2)[1];
  reset.reset(token,"reset-new-password-456");
  assertThat(accounts.current(login.token())).isNull();
  assertThat(accounts.login(email,"reset-new-password-456",false,"Browser",null).user().email()).isEqualTo(email);
  assertThatThrownBy(()->reset.reset(token,"another-password-789")).hasMessageContaining("hết hạn");
  org.mockito.Mockito.reset(mail);
  reset.forgot(uniqueEmail());
  org.mockito.Mockito.verifyNoInteractions(mail);
 }

 @Test void refreshCreatesANewSnapshotAndPreservesThePreviousVersion() throws Exception {
  Browser b=new Browser(); b.register(uniqueEmail());
  String original=b.send("POST","/api/figma/import",Map.of("fileKey","refresh-file"),200).path("snapshotId").asText();
  byte[] before=Files.readAllBytes(DATA.resolve("snapshots").resolve(original).resolve("figma-raw.json"));
  String refreshed=b.send("POST","/api/snapshots/"+original+"/refresh",null,200).path("snapshotId").asText();
  assertThat(refreshed).isNotEqualTo(original);
  assertThat(Files.readAllBytes(DATA.resolve("snapshots").resolve(original).resolve("figma-raw.json"))).isEqualTo(before);
  b.send("GET","/api/snapshots/"+refreshed,null,200);
  b.send("GET","/api/snapshots/"+original,null,200);
 }

 @Test void explicitOwnershipManifestMakesLegacySnapshotsVisibleWithoutChangingFiles() throws Exception {
  Browser b=new Browser(); String owner=b.register(uniqueEmail()).path("user").path("id").asText();
  var snapshot=snapshots.importPrivateSnapshot("legacy-design",null,"legacy-test-token").block(Duration.ofSeconds(10));
  Path file=DATA.resolve("snapshots").resolve(snapshot.getSnapshotId()).resolve("figma-raw.json");
  byte[] before=Files.readAllBytes(file);
  Path manifest=DATA.resolve("ownership-"+UUID.randomUUID()+".json");
  mapper.writeValue(manifest.toFile(),Map.of(snapshot.getSnapshotId(),owner));
  var migration=new LegacyDataImporter(mapper,users,sessions,projects,snapshots,jdbc,transactionManager,DATA.toString(),true,manifest.toString());
  migration.run(new org.springframework.boot.DefaultApplicationArguments());
  migration.run(new org.springframework.boot.DefaultApplicationArguments());
  assertThat(Files.readAllBytes(file)).isEqualTo(before);
  var list=b.send("GET","/api/projects",null,200).path("projects");
  assertThat(list).hasSize(1);
  assertThat(list.get(0).path("snapshotId").asText()).isEqualTo(snapshot.getSnapshotId());
 }
}
