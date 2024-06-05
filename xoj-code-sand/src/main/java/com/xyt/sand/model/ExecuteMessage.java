package com.xyt.sand.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 进程执行信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteMessage {

    private Integer exitValue;

    private String message;

    private String errorMessage;

    private long time;

    private long memory;
}
