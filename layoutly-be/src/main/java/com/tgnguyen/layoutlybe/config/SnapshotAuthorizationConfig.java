package com.tgnguyen.layoutlybe.config;
import com.tgnguyen.layoutlybe.service.SavedProjectService;
import com.tgnguyen.layoutlybe.security.CurrentUser;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.http.*;
import java.util.Map;
@Configuration
public class SnapshotAuthorizationConfig implements WebMvcConfigurer {
 private final SavedProjectService projects;
 public SnapshotAuthorizationConfig(SavedProjectService projects) { this.projects=projects; }
 @Override public void addInterceptors(InterceptorRegistry registry) {
  registry.addInterceptor(new HandlerInterceptor() {
   @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
    if(request.getDispatcherType()==jakarta.servlet.DispatcherType.ASYNC) return true;
    Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if(variables instanceof Map<?,?> paths&&paths.get("snapshotId") instanceof String id) projects.requireSnapshot(CurrentUser.id(),id);
    response.setHeader("Cache-Control","no-store");
    return true;
   }
  }).addPathPatterns("/api/snapshots/**");
 }
}
