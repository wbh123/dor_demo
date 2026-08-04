package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class VerifiedTeamInvitationWebConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler) {
                if (HttpMethod.POST.matches(request.getMethod())) {
                    throw new BusinessException(
                            "INVITEE_IDENTITY_REQUIRED",
                            "邀请队友必须同时校验学号和姓名，请刷新页面后重新发送邀请",
                            HttpStatus.BAD_REQUEST);
                }
                return true;
            }
        }).addPathPatterns("/api/v1/student/team-invitations");
    }
}
