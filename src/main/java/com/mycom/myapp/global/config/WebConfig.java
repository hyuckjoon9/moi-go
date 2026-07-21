package com.mycom.myapp.global.config;

import com.mycom.myapp.member.service.ProfileImageStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ProfileImageStorageService profileImageStorageService;

    public WebConfig(ProfileImageStorageService profileImageStorageService) {
        this.profileImageStorageService = profileImageStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/profile-images/**")
                .addResourceLocations(
                        profileImageStorageService.getUploadDirectory().toUri().toString());
    }
}
