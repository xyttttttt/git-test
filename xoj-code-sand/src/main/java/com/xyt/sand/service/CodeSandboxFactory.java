package com.xyt.sand.service;


import com.xyt.sand.service.docker.GoDockerCodeSandBoxImpl;
import com.xyt.sand.service.docker.JavaDockerCodeSandBoxImpl;
import com.xyt.sand.service.docker.PythonDockerCodeSandBoxImpl;
import com.xyt.sand.service.local.CjiajiaNativeCodeSandBoxImpl;

public class CodeSandboxFactory {
    public static CodeSandBox getInstance(String language) {

        CodeSandBox codeSandBox = null;
        if ("java".equals(language)) {
            codeSandBox = new JavaDockerCodeSandBoxImpl();
        }
        if ("cpp".equals(language)) {
            codeSandBox = new CjiajiaNativeCodeSandBoxImpl();
        }
        if ("go".equals(language)) {
            codeSandBox = new GoDockerCodeSandBoxImpl();
        }
        if ("python".equals(language)) {
            codeSandBox = new PythonDockerCodeSandBoxImpl();
        }
        return codeSandBox;
    }
}
