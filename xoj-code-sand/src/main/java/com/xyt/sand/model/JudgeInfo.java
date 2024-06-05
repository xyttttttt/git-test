package com.xyt.sand.model;

import lombok.Data;

/**
 * 判题信息
 * */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息
     * */
    private String message;

    /**
     * 内层占用
     * */
    private Long memory;


    /**
     * 执行时间
     * */
    private Long time;

}
