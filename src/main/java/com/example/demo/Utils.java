package com.example.demo;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class Utils {
    private static AtomicInteger counter = new AtomicInteger(0);
    /**
     * 生成ID, time: 13位 + random: 3位
     * @return id
     */
    public static long generateID () {
        return 1000000000 + counter.getAndIncrement();
    }

    /**
     * 打印
     * @param s
     */
    public static void log(String s) {
        System.out.println("[" + new Date().toString() + "]  " + s);
    }
}
