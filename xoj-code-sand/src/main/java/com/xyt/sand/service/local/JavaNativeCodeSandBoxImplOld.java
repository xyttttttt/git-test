package com.xyt.sand.service.local;



import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.xyt.sand.model.ExecuteCodeRequest;
import com.xyt.sand.model.ExecuteCodeResponse;
import com.xyt.sand.model.ExecuteMessage;
import com.xyt.sand.model.JudgeInfo;
import com.xyt.sand.service.CodeSandBox;
import com.xyt.sand.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandBoxImplOld implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final List<String> BLACKLIST = Arrays.asList("Files","exec");


    private static final String SECURITY_MANAGER_PATH = "D:\\WorkSpaces\\xoj-code-sandbox\\src\\main\\resources\\testCode\\security";

    private static final String SECURITY_MANAGER_CLASS_NAME="MySecurityManager";
    private static final Long TIME_OUT  = 10000l;

    private static final WordTree WORDTREE;

    static {//初始化字典树
        WORDTREE = new WordTree();
        WORDTREE.addWords(BLACKLIST);
    }

    public static void main(String[] args) {
        JavaNativeCodeSandBoxImplOld javaNativeCodeSandBox = new JavaNativeCodeSandBoxImplOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        // String code = ResourceUtil.readStr("testCode/unSafeCode/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        //   System.setSecurityManager(new DenySecurityManager());


        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        //校验代码 中是否包含黑名单中的命令
//        FoundWord foundWord = WORDTREE.matchWord(code);
//        if (foundWord != null){
//            System.out.println("包含敏感词"+foundWord.getFoundWord());
//            return null;
//        }


        String userDir = System.getProperty("user.dir");



        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //判断全局代码目录是否存在 没有则新建
        if (!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //编译代码，得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s",userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.handleProcessMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            getErrorResponse(e);
        }


        //执行代码 得到输出结果



        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input: inputList){
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s",userCodeParentPath,input);
            // String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s",userCodeParentPath,input);
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s",userCodeParentPath,SECURITY_MANAGER_PATH,SECURITY_MANAGER_CLASS_NAME,input);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //超时控制
                new Thread(()->{
                    try {
                        Thread.sleep(TIME_OUT);
                        //todo  缺少判断  是否主程序执行完毕
                        System.out.println("超时了，中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.handleProcessInteraction(runProcess,input, "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                getErrorResponse(e);
            }
        }

//      整理输出
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)){
                executeCodeResponse.setMessage(errorMessage);
                //执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            Long time = executeMessage.getTime();
            if (time != null){
                maxTime = Math.max(maxTime,executeMessage.getTime());
            }
            outputList.add(executeMessage.getMessage());
        }
        //正常运行
        if (outputList.size() == executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
//        judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);

        executeCodeResponse.setJudgeInfo(judgeInfo);

//        文件清理
        if(userCodeFile.getParentFile()!=null){
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println(del ? "删除成功" : "删除失败");
        }

        return executeCodeResponse;
    }

    /**
     * 获取错误响应信息
     * @param e
     * @return
     */

    private ExecuteCodeResponse getErrorResponse(Throwable e){
        ExecuteCodeResponse codeResponse = new ExecuteCodeResponse();
        codeResponse.setOutputList(new ArrayList<>());
        codeResponse.setMessage(e.getMessage());
        codeResponse.setStatus(2);
        codeResponse.setJudgeInfo(new JudgeInfo());

        return codeResponse;
    }
}

