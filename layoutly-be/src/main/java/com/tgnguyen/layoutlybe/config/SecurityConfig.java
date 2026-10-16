package com.tgnguyen.layoutlybe.config;
import com.tgnguyen.layoutlybe.security.SessionAuthenticationFilter;
import com.tgnguyen.layoutlybe.service.AccountService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.security.config.http.SessionCreationPolicy;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity http,AccountService accounts,ObjectMapper mapper) throws Exception {
  return http.cors(cors->{})
   .csrf(csrf->csrf.ignoringRequestMatchers(request->(request.getContextPath()+"/api/bridge/import").equals(request.getRequestURI())
       &&request.getHeader("X-Layoutly-Bridge-Token")!=null))
   .securityContext(context->context.requireExplicitSave(true))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.NEVER))
   .requestCache(cache->cache.disable())
   .formLogin(form->form.disable()).httpBasic(basic->basic.disable()).logout(logout->logout.disable())
   .headers(headers->headers.frameOptions(frame->frame.sameOrigin())
       .cacheControl(cache->{}))
   .exceptionHandling(errors->errors
    .authenticationEntryPoint((req,res,ex)->{
     res.setStatus(401); res.setContentType("application/json"); mapper.writeValue(res.getOutputStream(),Map.of("message","Vui lòng đăng nhập."));
    })
    .accessDeniedHandler((req,res,ex)->{
     res.setStatus(403); res.setContentType("application/json"); mapper.writeValue(res.getOutputStream(),Map.of("message","Phiên bảo mật không hợp lệ hoặc không có quyền truy cập."));
    }))
   .authorizeHttpRequests(auth->auth
    .dispatcherTypeMatchers(DispatcherType.ASYNC,DispatcherType.ERROR).permitAll()
    .requestMatchers("/api/auth/capabilities","/api/auth/csrf","/api/auth/me","/api/auth/register","/api/auth/login",
      "/api/auth/forgot-password","/api/auth/reset-password","/api/bridge/status").permitAll()
    .requestMatchers("/api/**").authenticated()
    .anyRequest().permitAll())
   .addFilterBefore(new SessionAuthenticationFilter(accounts),UsernamePasswordAuthenticationFilter.class).build();
 }
}
