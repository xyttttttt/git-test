package com.xyt.sand.service.local;






import com.xyt.sand.model.ExecuteMessage;
import com.xyt.sand.model.JudgeConfig;
import com.xyt.sand.service.temple.DefaultCodeSandBoxTemplate;
import com.xyt.sand.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CjiajiaNativeCodeSandBoxImpl extends DefaultCodeSandBoxTemplate {

    String GLOBAL_CODE_DIR_NAME = "tmpCode";

    String GLOBAL_JAVA_CLASS_NAME = "Main.cpp";

    private static final Long TIME_OUT = 10000l;
    public CjiajiaNativeCodeSandBoxImpl() {
        super.GLOBAL_CODE_DIR_NAME = GLOBAL_CODE_DIR_NAME;
        super.GLOBAL_JAVA_CLASS_NAME = GLOBAL_JAVA_CLASS_NAME;
    }

    /**
     * 编译用户代码文件
     *
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileUserCode(File userCodeFile) {
        String userCodePath = userCodeFile.getAbsolutePath();
        //编译代码，得到class文件
        String compileCmd = String.format("g++ -finput-charset=UTF-8 -fexec-charset=UTF-8 -o %s %s",userCodePath.substring(0, userCodePath.length() - 4)+".exe",userCodePath);
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.handleProcessMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译代码异常");
            }
            return executeMessage;
        } catch (Exception e) {
            //getErrorResponse(e);
            throw new RuntimeException(e);
        }
    }


    public List<ExecuteMessage> runUserCode(File userCodeFile, List<String> inputList, JudgeConfig judgeConfig) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        String finallyUserCode = userCodeParentPath + File.separator + "Main.exe";
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        long memoryUsageUsedAll = heapMemoryUsage.getUsed();
        for (String input : inputList) {
            StopWatch stopWatch = new StopWatch();
            String runCmd = finallyUserCode;
            try {
                stopWatch.start();
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        //todo  缺少判断  是否主程序执行完毕
                        if (runProcess.isAlive()) {
                            log.info("超时了，中断");
                            runProcess.destroy();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                ExecuteMessage executeMessage = ProcessUtils.handleProcessInteraction(runProcess,input, "运行");
                stopWatch.stop();
                long taskTimeMillis = stopWatch.getLastTaskTimeMillis();
                executeMessage.setTime(taskTimeMillis);
                MemoryMXBean memoryMXBean2 = ManagementFactory.getMemoryMXBean();
                MemoryUsage heapMemoryUsage2 = memoryMXBean2.getHeapMemoryUsage();
                long memoryUsageUsed = heapMemoryUsage2.getUsed();
                executeMessage.setMemory(memoryUsageUsed-memoryUsageUsedAll);
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("程序执行异常", e);
            }
        }
        return executeMessageList;
    }

}

