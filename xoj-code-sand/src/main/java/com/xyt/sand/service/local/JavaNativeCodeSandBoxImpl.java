package com.xyt.sand.service.local;



import com.xyt.sand.model.ExecuteCodeRequest;
import com.xyt.sand.model.ExecuteCodeResponse;
import com.xyt.sand.service.temple.DefaultCodeSandBoxTemplate;
import org.springframework.stereotype.Component;


/**
 * java原生代码沙箱实现
 */
@Component
public class JavaNativeCodeSandBoxImpl extends DefaultCodeSandBoxTemplate {

    String GLOBAL_CODE_DIR_NAME = "tmpCode";

    String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public JavaNativeCodeSandBoxImpl() {
        super.GLOBAL_CODE_DIR_NAME = GLOBAL_CODE_DIR_NAME;
        super.GLOBAL_JAVA_CLASS_NAME = GLOBAL_JAVA_CLASS_NAME;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

}

