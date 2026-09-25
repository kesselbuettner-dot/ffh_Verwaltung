package de.bierverein.api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {
 private final String storagePath;
 public UploadResourceConfig(@Value("${app.storage.path:/app/data}") String storagePath){this.storagePath=storagePath;}
 @Override public void addResourceHandlers(ResourceHandlerRegistry registry){
  String location=storagePath.endsWith("/")?storagePath:storagePath+"/";
  registry.addResourceHandler("/uploads/articles/**").addResourceLocations("file:"+location+"article-images/");
  registry.addResourceHandler("/uploads/**").addResourceLocations("file:"+location);
 }
}
