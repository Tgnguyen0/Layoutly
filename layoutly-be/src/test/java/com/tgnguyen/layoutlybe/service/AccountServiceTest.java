package com.tgnguyen.layoutlybe.service;
import com.tgnguyen.layoutlybe.persistence.*;
import com.tgnguyen.layoutlybe.security.PasswordHashing;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountServiceTest {
 UserRepository users;
 SessionRepository sessions;
 AccountService accounts;
 @BeforeEach void setup() {
  users=mock(UserRepository.class); sessions=mock(SessionRepository.class); accounts=new AccountService(users,sessions);
  when(users.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
  when(sessions.findByUserIdAndKindAndExpiresAtAfterOrderByCreatedAtAsc(any(),any(),any())).thenReturn(List.of());
 }
 @Test void hashesPasswordsAndPersistsOnlySessionDigests() {
  var login=accounts.register("Thanh"," TEST@example.com ","a-long-test-password","1","Chrome");
  var user=org.mockito.ArgumentCaptor.forClass(UserEntity.class);
  verify(users).saveAndFlush(user.capture());
  assertThat(user.getValue().email).isEqualTo("test@example.com");
  assertThat(user.getValue().passwordHash).startsWith("{pbkdf2}").doesNotContain("a-long-test-password");
  assertThat(new PasswordHashing().matches("a-long-test-password",user.getValue().passwordHash)).isTrue();
  var session=org.mockito.ArgumentCaptor.forClass(SessionEntity.class); verify(sessions).save(session.capture());
  assertThat(session.getValue().digest).hasSize(64).isNotEqualTo(login.token());
  assertThat(login.toString()).doesNotContain(login.token());
 }
 @Test void rejectsInvalidRegistrationAndLimitsAbuse() {
  assertThatThrownBy(()->accounts.register("Thanh","a@example.com","short","1","Chrome")).hasMessageContaining("12");
  assertThatThrownBy(()->accounts.register("Thanh","a@example.com","a-long-test-password",null,"Chrome")).hasMessageContaining("điều khoản");
  accounts.limit("ip",2); accounts.limit("ip",2);
  assertThatThrownBy(()->accounts.limit("ip",2)).hasMessageContaining("quá nhiều lần");
 }
 @Test void unknownAndExpiredTokensCannotAuthenticate() {
  assertThat(accounts.current(null)).isNull();
  assertThat(accounts.current("unknown")).isNull();
  verify(sessions).findByDigestAndKindAndExpiresAtAfter(eq(AccountService.digest("unknown")),eq("WEB"),any(Instant.class));
 }
 @Test void passwordHashesHaveRandomSaltsAndRejectMalformedLegacyData() {
  var hashing=new PasswordHashing(); String a=hashing.encode("long-test-password"),b=hashing.encode("long-test-password");
  assertThat(a).isNotEqualTo(b);
  assertThat(hashing.matches("wrong-password",a)).isFalse();
  assertThat(hashing.matches("password","broken:hash")).isFalse();
  assertThat(hashing.matches("password","99999999999999999:salt:hash")).isFalse();
 }
}
