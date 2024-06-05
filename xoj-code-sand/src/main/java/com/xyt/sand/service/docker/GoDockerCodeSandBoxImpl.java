package com.xyt.sand.service.docker;




import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.xyt.sand.model.ExecuteMessage;
import com.xyt.sand.model.JudgeConfig;
import com.xyt.sand.service.temple.DefaultCodeSandBoxTemplate;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;


import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * java代码模版方法实现
 */
@Slf4j
@Component
public class GoDockerCodeSandBoxImpl extends DefaultCodeSandBoxTemplate {


    private static final Long TIME_OUT = 5000L;

    private static volatile Boolean FIRST_INIT = false;

    private static DockerClient dockerClient;

    /**
     * 初始化dockerClient
     *
     * @return
     */
    @PostConstruct
    public DockerClient dockerClientInit() {
        //获取默认的DockerClient
        dockerClient = DockerClientBuilder.getInstance().build();
        return dockerClient;
    }


    @Override
    public List<ExecuteMessage>  runUserCode(File userCodeFile, List<String> inputList, JudgeConfig judgeConfig) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        //下载镜像
        String image = "golang-local:1.0";

        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //创建容器时 指定文件映射 ， 把文件同步到容器中，可以让容器访问      容器挂载目录
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(1024 * 1024 * 1024L);
        hostConfig.withCpuCount(1L);
        hostConfig.withMemorySwap(0L);

        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));

        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)  //创建交互式容器
                .exec();

        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();

        String userCodePath = userCodeFile.getAbsolutePath();

        //执行命令并获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>(inputList.size());
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            long time = 0;
            String runCodeCmd = String.format("go run %s %s", userCodePath, inputArgs);
            ExecCreateCmdResponse createCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(runCodeCmd)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withTty(true)
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
                    System.out.println("内存占用" + usageMemory);
                    if (usageMemory != null &&
                            (executeMessage.getMessage() == null ||
                                    executeMessage.getMemory() < usageMemory)) {
                        executeMessage.setMemory(usageMemory);
                        System.out.println("让我们更新好吗？？？？？？？？？？？？？？？？？" + usageMemory);
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
     * 删除容器
     */
    public void removeDockerContainer(String containerId) {
        RemoveContainerCmd removeContainerCmd = dockerClient.removeContainerCmd(containerId).withForce(true);
        removeContainerCmd.exec();
    }
}
