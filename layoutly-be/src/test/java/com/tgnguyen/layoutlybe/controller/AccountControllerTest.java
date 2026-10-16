package com.tgnguyen.layoutlybe.controller;
import com.tgnguyen.layoutlybe.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;

/** Filter/CSRF/authorization are exercised with the real application in PostgreSqlIntegrationTest. */
class AccountControllerTest {
 @Test void cookieCoversAllApisAndNeverSerializesTheRawTokenInTheBody() {
  var accounts=mock(AccountService.class);
  var user=new AccountService.User("id","Thanh","a@example.com","","",Instant.now().toString(),Instant.now().toString());
  var session=new AccountService.Session("session","digest","id","now","later",true,"Chrome");
  when(accounts.login("a@example.com","password",true,null,null)).thenReturn(new AccountService.Login(user,"secret-token",session));
  var response=new MockHttpServletResponse();
  var body=new AccountController(accounts,true).login(new AccountController.Credentials("a@example.com","password",true),new MockHttpServletRequest(),response);
  assertThat(body).containsKey("user").doesNotContainKey("token");
  assertThat(response.getHeaders("Set-Cookie")).anySatisfy(cookie->assertThat(cookie).contains("Path=/api;","HttpOnly","Secure","SameSite=Lax","Max-Age=2592000"));
 }
}
