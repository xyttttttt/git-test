package com.xyt.sand.service.docker;



import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.xyt.sand.model.ExecuteMessage;
import com.xyt.sand.model.JudgeConfig;
import com.xyt.sand.service.temple.DefaultCodeSandBoxTemplate;
import com.xyt.sand.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;



import javax.annotation.PostConstruct;
import javax.ws.rs.ProcessingException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * java代码模版方法实现
 */
@Slf4j
@Component
public class PythonDockerCodeSandBoxImpl extends DefaultCodeSandBoxTemplate {


    private static final Long TIME_OUT = 5000L;

    private static volatile Boolean FIRST_INIT = false;

    private static DockerClient dockerClient;

    String GLOBAL_CODE_DIR_NAME = "tmpCode";

    String GLOBAL_JAVA_CLASS_NAME = "Main.py";

    public PythonDockerCodeSandBoxImpl() {
        super.GLOBAL_CODE_DIR_NAME = GLOBAL_CODE_DIR_NAME;
        super.GLOBAL_JAVA_CLASS_NAME = GLOBAL_JAVA_CLASS_NAME;
    }
    /**
     * 初始化dockerClient
     * @return
     */
    @PostConstruct
    public DockerClient dockerClientInit(){
        //获取默认的DockerClient
        dockerClient = DockerClientBuilder.getInstance().build();
        return dockerClient;
    }


    /**
     * 编译用户代码文件
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileUserCode(File userCodeFile){
        //编译代码，得到class文件
        String compileCmd = String.format("python -m py_compile %s",userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.handleProcessMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0){
                throw new RuntimeException("编译代码异常");
            }
            return executeMessage;
        } catch (Exception e) {
            //getErrorResponse(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ExecuteMessage> runUserCode(File userCodeFile, List<String> inputList, JudgeConfig judgeConfig) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        PingCmd pingCmd = dockerClient.pingCmd();
        pingCmd.exec();

        //下载镜像
        String image = "python:3.8-alpine";

        boolean dockerImageExists = dockerImageExistsLocally(image);
        if (!dockerImageExists){
            downloadDockerImage(image);
        }

        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //创建容器时 指定文件映射 ， 把文件同步到容器中，可以让容器访问      容器挂载目录
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(1024 * 1024 * 1024L);
        hostConfig.withCpuCount(1L);
        hostConfig.withMemorySwap(0L);
        // hostConfig.withSecurityOpts(Arrays.asList("seccomp=''"))   //Linux 安全管理配置 扩展

        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));

        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)  //创建交互式容器
                .exec();
        System.out.println(Arrays.toString(hostConfig.getBinds()));
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();


        //执行命令并获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>(inputList.size());
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            long time = 0;

            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArr = ArrayUtil.append(new String[]{"python","/app/Main.py",}, inputArgsArray);
            // String cmdArr = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp /app;%s -Djava.security.manager=%s Main %s"
            //          ,SECURITY_MANAGER_PATH,SECURITY_MANAGER_CLASS_NAME,inputArgsArray);
            ExecCreateCmdResponse createCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArr)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .exec();
            final String[] message = {null};
            final String[] errorMessage = {null};
            ExecuteMessage executeMessage = new ExecuteMessage();
            System.out.println("创建执行命令:" + createCmdResponse);
            String execId = createCmdResponse.getId();
            final boolean[] isTimeout = {true};   //判断程序是否超时
            ExecStartResultCallback resultCallback = new ExecStartResultCallback() {

                @Override
                public void onComplete() {  //程序正常完成时
                    isTimeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误信息:" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出信息:" + message[0]);
                    }
                    super.onNext(frame);
                }
            };
            //获取占用内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);



            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }
                @Override
                public void onNext(Statistics statistics) {
                    Long usageMemory = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用" +usageMemory);
                    if (usageMemory != null &&
                            (executeMessage.getMessage() == null  ||
                                    executeMessage.getMemory() <usageMemory)){
                        executeMessage.setMemory(usageMemory);
                    }
                }
                @Override
                public void onError(Throwable throwable) {
                }
                @Override
                public void onComplete() {
                }
                @Override
                public void close() throws IOException {
                }
            });

            statsCmd.exec(statisticsResultCallback);

            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId).exec(resultCallback).awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statisticsResultCallback.close();
                statsCmd.close();
                // Thread.sleep(1000);
            } catch (InterruptedException | IOException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }

            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessageList.add(executeMessage);
        }
        removeDockerContainer(containerId);
        return executeMessageList;
    }

    /**
     * docker镜像是否本地已存在
     *
     * @param image
     * @return
     * @throws ProcessingException
     */
    public boolean dockerImageExistsLocally(String image) throws ProcessingException {
        boolean imageExists = false;
        try {
            log.info("判断镜像是否存在");
            List<Image> imageList = dockerClient.listImagesCmd().exec();
            for (Image img : imageList){
                if (img.getRepoTags() != null && Arrays.asList(img.getRepoTags()).contains(image)) {
                    imageExists = true;// 镜像存在
                }
            }
//            dockerClient.inspectImageCmd(image).exec();
        } /*catch (NotFoundException nfe) {
            System.out.println("镜像已存在");
            imageExists = false;
        }*/  catch (ProcessingException e) {
            throw e;
        }
        return imageExists;
    }

    /**
     * 下载镜像
     * @param image
     */
    public void downloadDockerImage(String image){
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
                System.out.printf("下载镜像：" + item.getStatus());
                super.onNext(item);
            }
        };
        try {
            pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        } catch (InterruptedException e) {
            System.out.println("拉取镜像异常");
            throw new RuntimeException(e);
        }
        FIRST_INIT = false;
        System.out.printf("下载完成");
    }

    /**
     * 删除容器
     */
    public void removeDockerContainer(String containerId){
        RemoveContainerCmd removeContainerCmd = dockerClient.removeContainerCmd(containerId).withForce(true);
        removeContainerCmd.exec();
    }
}

