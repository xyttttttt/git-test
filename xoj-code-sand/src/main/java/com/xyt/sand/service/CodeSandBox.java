package com.xyt.sand.service;




import com.xyt.sand.model.ExecuteCodeRequest;
import com.xyt.sand.model.ExecuteCodeResponse;

/**
 * 代码沙箱的接口定义
 */
public interface CodeSandBox {
    /**
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
