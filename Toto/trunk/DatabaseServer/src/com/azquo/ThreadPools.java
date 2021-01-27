package com.azquo;

import com.azquo.memorydb.core.AzquoMemoryDB;
import org.apache.commons.lang.math.NumberUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by edward on 29/09/16.
 *
 * Pools were in AzquoMemoryDB. No real reason for this and the class was getting bloated.
 */
public class ThreadPools {

    // it is true that having these two as separate pools could make more threads than processors (as visible to the OS) but it's not the end of the world and it's still
    // an improvement over the old situation. Not only for thread management but also not creating and destroying the thread pools is better for garbage.

    private static final ExecutorService mainThreadPool = initMainThreadPool();
    private static final ExecutorService sqlThreadPool = initSQLThreadPool();

    // may need to tweak this - since SQL is IO Bound then ramping it up more may not be the best idea. Also persistence will be using processors of course.
    private static ExecutorService initSQLThreadPool() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int possibleLoadingThreads = availableProcessors < 4 ? availableProcessors : (availableProcessors / 2);
        if (possibleLoadingThreads > 8) { // I think more than this asks for trouble - processors isn't really the prob with persistence it's IO! I should be asking : is the disk SSD?
            possibleLoadingThreads = 8;
        }
        System.out.println("memory db transport threads : " + possibleLoadingThreads);
        return Executors.newFixedThreadPool(possibleLoadingThreads);
    }

    private static ExecutorService initMainThreadPool() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxThreads = 15;
        if (AzquoMemoryDB.getMaxthreads() != null && NumberUtils.isDigits(AzquoMemoryDB.getMaxthreads())){
            maxThreads = Integer.parseInt(AzquoMemoryDB.getMaxthreads());
        }
        if (availableProcessors == 1) {
            return Executors.newFixedThreadPool(availableProcessors);
        } else {
            if (availableProcessors > maxThreads) {
                System.out.println("reportFillerThreads : " + maxThreads);
                return Executors.newFixedThreadPool(maxThreads);
            } else {
                System.out.println("reportFillerThreads : " + (availableProcessors - 1));
                return Executors.newFixedThreadPool(availableProcessors - 1);
            }
        }
    }

    public static ExecutorService getSqlThreadPool() {
        return sqlThreadPool;
    }

    public static ExecutorService getMainThreadPool() {
        return mainThreadPool;
    }
}
