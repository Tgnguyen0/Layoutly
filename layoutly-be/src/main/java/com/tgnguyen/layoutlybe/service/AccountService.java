package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.persistence.*;
import com.tgnguyen.layoutlybe.security.PasswordHashing;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service @Transactional
public class AccountService {
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
 private final UserRepository users;
 private final SessionRepository sessions;
 private final PasswordHashing passwords=new PasswordHashing();
 private final String dummyHash=passwords.encode(randomToken());
 private final Map<String,Attempt> attempts=new HashMap<>();
 private record Attempt(int count,Instant until) {}
 public record User(String id,String displayName,String email,String company,String bio,String createdAt,String updatedAt) {}
 public record Session(String id,String digest,String userId,String createdAt,String expiresAt,boolean rememberMe,String device) {
  @Override public String toString() { return "Session[redacted]"; }
 }
 public record StoredUser(User profile,String passwordHash) {
  @Override public String toString() { return "StoredUser[redacted]"; }
 }
 public record Store(List<StoredUser> users,List<Session> sessions) {}
 public record Login(User user,String token,Session session) {
  @Override public String toString() { return "Login[redacted]"; }
 }
 public record SessionView(String id,String createdAt,String expiresAt,String device,boolean current) {}
 public record BridgeToken(String token,String expiresAt) {
  @Override public String toString() { return "BridgeToken[redacted]"; }
 }
 public static class AccountException extends RuntimeException {
  public final HttpStatus status;
  public AccountException(HttpStatus status,String message) { super(message); this.status=status; }
 }
 public AccountService(UserRepository users,SessionRepository sessions) { this.users=users; this.sessions=sessions; }
 public synchronized void limit(String key,int maximum) {
  Instant now=Instant.now();
  attempts.entrySet().removeIf(e->e.getValue().until().isBefore(now));
  Attempt old=attempts.getOrDefault(key,new Attempt(0,now.plus(Duration.ofMinutes(15))));
  if(old.count()>=maximum||attempts.size()>=10_000) throw error(HttpStatus.TOO_MANY_REQUESTS,"Bạn đã thử quá nhiều lần. Vui lòng thử lại sau 15 phút.");
  attempts.put(key,new Attempt(old.count()+1,old.until()));
 }
 public Login register(String name,String email,String password,String terms,String device) {
  String normalized=email(email); validatePassword(password);
  if(!"1".equals(terms)) throw error(HttpStatus.BAD_REQUEST,"Vui lòng đồng ý với điều khoản để tạo tài khoản.");
  if(users.findByEmailAndDeletedAtIsNull(normalized).isPresent()) throw error(HttpStatus.CONFLICT,"Email này đã được đăng ký.");
  UserEntity user=new UserEntity(); user.id=UUID.randomUUID(); user.displayName=text(name,100,true,"Họ và tên");
  user.email=normalized; user.passwordHash=passwords.encode(password); user.createdAt=Instant.now(); user.updatedAt=user.createdAt;
  users.saveAndFlush(user); return createSession(user,false,device,null,false);
 }
 public Login login(String email,String password,boolean remember,String device,String previous) {
  UserEntity user=users.findByEmailAndDeletedAtIsNull(email(email)).orElse(null);
  boolean valid=password!=null&&password.length()<=128&&passwords.matches(password,user==null?dummyHash:user.passwordHash);
  if(!valid||user==null) throw error(HttpStatus.UNAUTHORIZED,"Email hoặc mật khẩu không đúng.");
  if(passwords.upgradeEncoding(user.passwordHash)) user.passwordHash=passwords.encode(password);
  return createSession(user,remember,device,previous,false);
 }
 @Transactional(readOnly=true)
 public User current(String token) { return current(token,"WEB"); }
 @Transactional(readOnly=true)
 public User current(String token,String kind) {
  SessionEntity session=session(token,kind);
  return session==null?null:users.findById(session.userId).filter(u->u.deletedAt==null).map(AccountService::view).orElse(null);
 }
 public User require(String token) {
  User user=current(token);
  if(user==null) throw error(HttpStatus.UNAUTHORIZED,"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
  return user;
 }
 public User update(String token,String name,String email,String company,String bio,String password) {
  UserEntity user=entity(require(token).id()); String normalized=email(email);
  if(!user.email.equals(normalized)) {
   checkPassword(user,password);
   if(users.findByEmailAndDeletedAtIsNull(normalized).isPresent()) throw error(HttpStatus.CONFLICT,"Email này đã được sử dụng.");
   revokeOthers(token);
  }
  user.displayName=text(name,100,true,"Họ và tên"); user.email=normalized;
  user.company=text(company,120,false,"Công ty"); user.bio=text(bio,500,false,"Giới thiệu");
  user.updatedAt=Instant.now(); users.saveAndFlush(user); return view(user);
 }
 public Login changePassword(String token,String currentPassword,String password,String device) {
  UserEntity user=entity(require(token).id()); checkPassword(user,currentPassword); validatePassword(password);
  if(Objects.equals(currentPassword,password)) throw error(HttpStatus.BAD_REQUEST,"Mật khẩu mới cần khác mật khẩu hiện tại.");
  user.passwordHash=passwords.encode(password); user.updatedAt=Instant.now();
  return createSession(user,Objects.requireNonNull(session(token,"WEB")).rememberMe,device,token,true);
 }
 @Transactional(readOnly=true)
 public List<SessionView> sessions(String token) {
  UUID owner=UUID.fromString(require(token).id());
  return sessions.findByUserIdAndKindAndExpiresAtAfterOrderByCreatedAtAsc(owner,"WEB",Instant.now()).stream()
   .map(s->new SessionView(s.id.toString(),s.createdAt.toString(),s.expiresAt.toString(),s.device,s.digest.equals(digest(token))))
   .sorted(Comparator.comparing(SessionView::current).reversed()).toList();
 }
 public void revokeOthers(String token) {
  UUID owner=UUID.fromString(require(token).id());
  sessions.findByUserId(owner).stream().filter(s->!s.digest.equals(digest(token))).forEach(sessions::delete);
 }
 public void logout(String token) { if(token!=null) sessions.deleteByDigest(digest(token)); }
 public void delete(String token,String password) {
  UserEntity user=entity(require(token).id()); checkPassword(user,password);
  user.deletedAt=Instant.now(); user.updatedAt=user.deletedAt; sessions.deleteByUserId(user.id);
 }
 public BridgeToken bridgeToken(String token) {
  UserEntity user=entity(require(token).id()); String value=randomToken();
  SessionEntity s=new SessionEntity(); s.id=UUID.randomUUID(); s.userId=user.id; s.digest=digest(value);
  s.createdAt=Instant.now(); s.expiresAt=s.createdAt.plus(Duration.ofMinutes(10)); s.kind="BRIDGE"; s.device="Figma Bridge";
  sessions.save(s); return new BridgeToken(value,s.expiresAt.toString());
 }
 public void bindBridge(String token,String snapshotId) {
  if(token==null) return;
  SessionEntity session=session(token,"BRIDGE");
  if(session==null) throw error(HttpStatus.UNAUTHORIZED,"Mã kết nối đã hết hạn.");
  session.scopeSnapshotId=snapshotId;
 }
 @Transactional(readOnly=true)
 public User bridgeCurrent(String token,String snapshotId) {
  SessionEntity session=session(token,"BRIDGE");
  return session!=null&&Objects.equals(session.scopeSnapshotId,snapshotId)?current(token,"BRIDGE"):null;
 }
 public Login captureSession(UUID owner) {
  Login login=createSession(entity(owner.toString()),false,"Internal preview capture",null,false);
  SessionEntity row=session(login.token(),"WEB");
  row.expiresAt=Instant.now().plusSeconds(60);
  return login;
 }
 private Login createSession(UserEntity user,boolean remember,String device,String previous,boolean revokeAll) {
  if(revokeAll) sessions.deleteByUserId(user.id); else if(previous!=null) sessions.deleteByDigest(digest(previous));
  List<SessionEntity> owned=sessions.findByUserIdAndKindAndExpiresAtAfterOrderByCreatedAtAsc(user.id,"WEB",Instant.now());
  if(owned.size()>=20) owned.subList(0,owned.size()-19).forEach(sessions::delete);
  String token=randomToken(); Instant now=Instant.now();
  SessionEntity s=new SessionEntity(); s.id=UUID.randomUUID(); s.digest=digest(token); s.userId=user.id;
  s.createdAt=now; s.expiresAt=now.plus(remember?Duration.ofDays(30):Duration.ofHours(8)); s.rememberMe=remember;
  s.device=device==null?"Trình duyệt":device.substring(0,Math.min(240,device.length())); sessions.save(s);
  return new Login(view(user),token,new Session(s.id.toString(),s.digest,user.id.toString(),now.toString(),s.expiresAt.toString(),remember,s.device));
 }
 private SessionEntity session(String token,String kind) {
  return token==null||token.length()>128?null:sessions.findByDigestAndKindAndExpiresAtAfter(digest(token),kind,Instant.now()).orElse(null);
 }
 private UserEntity entity(String id) {
  return users.findById(UUID.fromString(id)).filter(u->u.deletedAt==null).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập lại."));
 }
 private void checkPassword(UserEntity user,String password) {
  if(password==null||password.length()>128||!passwords.matches(password,user.passwordHash)) throw error(HttpStatus.BAD_REQUEST,"Mật khẩu hiện tại không đúng.");
 }
 public static User view(UserEntity u) { return new User(u.id.toString(),u.displayName,u.email,u.company,u.bio,u.createdAt.toString(),u.updatedAt.toString()); }
 public static String randomToken() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
 public static String digest(String value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
  catch(Exception e) { throw new IllegalStateException(e); }
 }
 public static String email(String value) {
  String email=text(value,254,true,"Email").toLowerCase(Locale.ROOT);
  if(!EMAIL.matcher(email).matches()) throw error(HttpStatus.BAD_REQUEST,"Nhập một địa chỉ email hợp lệ."); return email;
 }
 public static String text(String value,int max,boolean required,String label) {
  String cleaned=value==null?"":value.strip();
  if((required&&cleaned.isEmpty())||cleaned.length()>max||cleaned.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'))
   throw error(HttpStatus.BAD_REQUEST,label+" không hợp lệ (tối đa "+max+" ký tự)."); return cleaned;
 }
 public static void validatePassword(String password) {
  if(password==null||password.length()<12||password.length()>128) throw error(HttpStatus.BAD_REQUEST,"Mật khẩu cần từ 12 đến 128 ký tự.");
 }
 private static AccountException error(HttpStatus status,String message) { return new AccountException(status,message); }
}
