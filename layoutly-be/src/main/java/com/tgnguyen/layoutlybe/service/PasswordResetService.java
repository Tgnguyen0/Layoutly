package com.tgnguyen.layoutlybe.service;
import com.tgnguyen.layoutlybe.persistence.*;
import com.tgnguyen.layoutlybe.security.PasswordHashing;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
public class PasswordResetService {
 private final UserRepository users;
 private final SessionRepository sessions;
 private final ResetTokenRepository tokens;
 private final ObjectProvider<JavaMailSender> mail;
 private final boolean enabled;
 private final String publicUrl;
 private final String from;
 public PasswordResetService(UserRepository users,SessionRepository sessions,ResetTokenRepository tokens,
  ObjectProvider<JavaMailSender> mail,@Value("${layoutly.auth.password-reset-enabled:false}") boolean enabled,
  @Value("${layoutly.auth.public-url:http://localhost:5173}") String publicUrl,@Value("${layoutly.auth.email-from:}") String from) {
  this.users=users; this.sessions=sessions; this.tokens=tokens; this.mail=mail; this.enabled=enabled; this.publicUrl=publicUrl; this.from=from;
 }
 public boolean available() { return enabled&&!from.isBlank()&&mail.getIfAvailable()!=null; }
 @Transactional
 public void forgot(String email) {
  requireAvailable();
  UserEntity user=users.findByEmailAndDeletedAtIsNull(AccountService.email(email)).orElse(null);
  if(user==null) return;
  String token=AccountService.randomToken();
  ResetTokenEntity row=new ResetTokenEntity(); row.id=UUID.randomUUID(); row.userId=user.id;
  row.digest=AccountService.digest(token); row.expiresAt=Instant.now().plus(Duration.ofMinutes(30)); tokens.save(row);
  SimpleMailMessage message=new SimpleMailMessage(); message.setTo(user.email); message.setFrom(from);
  message.setSubject("Đặt lại mật khẩu Layoutly");
  message.setText("Liên kết có hiệu lực trong 30 phút: "+publicUrl+"/#/reset-password?token="+token);
  mail.getObject().send(message);
 }
 @Transactional
 public void reset(String token,String password) {
  requireAvailable(); AccountService.validatePassword(password);
  if(token==null||token.length()>128) throw invalid();
  ResetTokenEntity row=tokens.findByDigest(AccountService.digest(token)).orElseThrow(PasswordResetService::invalid);
  if(row.usedAt!=null||!row.expiresAt.isAfter(Instant.now())) throw invalid();
  UserEntity user=users.findById(row.userId).filter(u->u.deletedAt==null).orElseThrow(PasswordResetService::invalid);
  user.passwordHash=new PasswordHashing().encode(password); user.updatedAt=Instant.now(); row.usedAt=Instant.now();
  sessions.deleteByUserId(user.id);
 }
 private void requireAvailable() {
  if(!available()) throw new AccountService.AccountException(HttpStatus.SERVICE_UNAVAILABLE,"Email khôi phục chưa được cấu hình.");
 }
 private static AccountService.AccountException invalid() { return new AccountService.AccountException(HttpStatus.BAD_REQUEST,"Liên kết không hợp lệ hoặc đã hết hạn."); }
}
