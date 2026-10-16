package com.tgnguyen.layoutlybe.security;
import com.tgnguyen.layoutlybe.service.AccountService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.io.IOException;
import java.util.*;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
 private final AccountService accounts;
 public SessionAuthenticationFilter(AccountService accounts) { this.accounts=accounts; }
 public static String token(HttpServletRequest request) {
  return request.getCookies()==null?null:Arrays.stream(request.getCookies())
    .filter(c->"layoutly_session".equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
 }
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
  String bridge=request.getHeader("X-Layoutly-Bridge-Token");
  String path=request.getRequestURI().substring(request.getContextPath().length());
  boolean bridgeRequest=bridge!=null&&path.equals("/api/bridge/import")&&request.getMethod().equals("POST");
  AccountService.User user=bridgeRequest?accounts.current(bridge,"BRIDGE"):accounts.current(token(request));
  if (user == null && bridge != null && request.getMethod().equals("GET")) {
   var match=java.util.regex.Pattern.compile("^/api/snapshots/([a-zA-Z0-9_-]{8,80})(?:/.*)?$").matcher(path);
   if(match.matches()) user=accounts.bridgeCurrent(bridge,match.group(1));
  }
  if(user!=null) SecurityContextHolder.getContext().setAuthentication(
   UsernamePasswordAuthenticationToken.authenticated(user,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  try { chain.doFilter(request,response); }
  finally { SecurityContextHolder.clearContext(); }
 }
}
