package com.tgnguyen.layoutlybe.security;
import com.tgnguyen.layoutlybe.service.AccountService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public final class CurrentUser {
 private CurrentUser() {}
 public static UUID id() {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth==null||!(auth.getPrincipal() instanceof AccountService.User user))
   throw new AccountService.AccountException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập.");
  return UUID.fromString(user.id());
 }
}
