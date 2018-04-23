package com.ximua.xunwu.security;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 登录验证失败处理器
 * created by qiankeqin
 */
public class LoginAuthFailHandler extends SimpleUrlAuthenticationFailureHandler{
    //记录哪里跳转过来到失败处理
    private final LoginUrlEntryPoint urlEntryPoint;

    //用户获取到错误页面
    public LoginAuthFailHandler(LoginUrlEntryPoint urlEntryPoint) {
        this.urlEntryPoint = urlEntryPoint;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String targetUrl = this.urlEntryPoint.determineUrlToUseForThisRequest(request, response, exception);
        targetUrl += "?"+exception.getMessage();
        //设置异常路径及异常信息
        super.setDefaultFailureUrl(targetUrl);
        //使用默认到处理异常方法
        super.onAuthenticationFailure(request,response,exception);
    }
}
