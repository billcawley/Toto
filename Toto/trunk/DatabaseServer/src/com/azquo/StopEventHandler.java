package com.azquo;

import com.azquo.memorydb.core.AzquoMemoryDB;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Created by edward on 15/01/16.
 *
 * Created simply to clean up the new thread pools in AzquoMemoryDB
 */
@Component
public class StopEventHandler implements ApplicationListener<ContextClosedEvent> {

    @Override
    public void onApplicationEvent(final ContextClosedEvent event) {
        System.out.println("Stopped, closing Executor services : " + AzquoMemoryDB.mainThreadPool.shutdownNow() + " " + AzquoMemoryDB.sqlThreadPool.shutdownNow());
    }
}