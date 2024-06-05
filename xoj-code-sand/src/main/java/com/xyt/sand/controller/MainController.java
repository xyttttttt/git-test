package com.xyt.sand.controller;

import com.xyt.sand.model.ExecuteCodeRequest;
import com.xyt.sand.model.ExecuteCodeResponse;
import com.xyt.sand.service.CodeSandBox;
import com.xyt.sand.service.CodeSandboxFactory;
import com.xyt.sand.service.docker.GoDockerCodeSandBoxImpl;
import com.xyt.sand.service.docker.JavaDockerCodeSandBoxImpl;
import com.xyt.sand.service.docker.PythonDockerCodeSandBoxImpl;
import com.xyt.sand.service.local.CjiajiaNativeCodeSandBoxImpl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@RestController("/")
public class MainController {

    //定义鉴权请求头和密钥
    public static final String AUTH_REQUEST_HEADER = "auth-xyt";

    public static final String AUTH_REQUEST_SECRET = "5eafa9b7-7c7c-42cc-92ce-c43abfd29b6f";


    /**
     * 执行代码
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request
            , HttpServletResponse response){
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)){
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null){
            throw new RuntimeException("请求参数为空");
        }
        String language = executeCodeRequest.getLanguage();
        CodeSandBox codeSandBox = CodeSandboxFactory.getInstance(language);
        return codeSandBox.executeCode(executeCodeRequest);
    }

}
