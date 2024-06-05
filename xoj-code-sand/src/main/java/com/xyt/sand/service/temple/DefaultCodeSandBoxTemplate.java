package com.xyt.sand.service.temple;



import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import com.xyt.sand.model.*;
import com.xyt.sand.service.CodeSandBox;
import com.xyt.sand.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * java代码模版方法实现
 */
@Slf4j
public abstract class DefaultCodeSandBoxTemplate implements CodeSandBox {

    public String GLOBAL_CODE_DIR_NAME;

    public String GLOBAL_JAVA_CLASS_NAME;

    public String PREFIX;
    private static Long TIME_OUT  = 10000l;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        JudgeConfig judgeConfig = executeCodeRequest.getJudgeConfig();



        //1. 保存用户代码
        File codeToFile = saveCodeToFile(code);

        //2. 编译代码 得到class文件
        ExecuteMessage compileUserCodeMessage = compileUserCode(codeToFile);
        System.out.println(compileUserCodeMessage);

        //3.执行代码 得到输出结果
        List<ExecuteMessage> executeMessageList = runUserCode(codeToFile, inputList,judgeConfig);


        //4. 整理输出
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        //5. 文件清理
        boolean del = deleteUserCodeFile(codeToFile);
        if (!del){
            log.error("deleteUserCodeFile error,userCodeFilePath = {}",codeToFile.getAbsolutePath());
        }
        return executeCodeResponse;
    }

    /**
     * 保存用户代码
     * @param code
     * @return
     */
    public File saveCodeToFile(String code){
        String userDir = System.getProperty("user.dir");
       // String userDir = "/code/xoj/backend";
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //判断全局代码目录是否存在 没有则新建
        if (!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return  userCodeFile;
    }

    /**
     * 编译用户代码文件
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileUserCode(File userCodeFile){
        //编译代码，得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s",userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.handleProcessMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0){
                throw new RuntimeException("编译代码异常");
            }
            log.info("11111111111111111111用户代码编译成功了");
            return executeMessage;
        } catch (Exception e) {
            //getErrorResponse(e);
            throw new RuntimeException(e);
        }
    }


    public  List<ExecuteMessage> runUserCode(File userCodeFile,List<String> inputList, JudgeConfig judgeConfig){
        Long timeLimit = judgeConfig.getTimeLimit();
        Long memoryLimit = judgeConfig.getMemoryLimit();
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
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
                        Thread.sleep(timeLimit);
                        //todo  缺少判断  是否主程序执行完毕
                        if (runProcess.isAlive()){
                            System.out.println("超时了，中断");
                            runProcess.destroy();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.handleProcessInteraction(runProcess,input,"运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw  new RuntimeException("程序执行异常",e);
            }
        }
        return executeMessageList;
    }

    /**
     * 获取输出结果
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxMemory = 0;
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
            Long memory = executeMessage.getMemory();
            if (memory != null){
                maxMemory = Math.max(maxMemory,executeMessage.getMemory());
            }
            outputList.add(executeMessage.getMessage());
        }
        //正常运行       输出结果list 和 运行结果list大小一致
        if (outputList.size() == executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMemory(maxMemory);
        judgeInfo.setTime(maxTime);

        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 删除文件
     * @param userCodeFile
     * @return
     */
    public boolean deleteUserCodeFile(File userCodeFile){
        if(userCodeFile.getParentFile()!=null){
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println(del ? "删除成功" : "删除失败");
            return del;
        }
        return true;
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
