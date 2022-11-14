package com.azquo;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * Copyright (C) 2016 Azquo Ltd.
 *
 * Created by edward on 15/01/16.
 *
 * Created simply to clean up the new thread pools in AzquoMemoryDB
 */
@Component
public class StopEventHandler implements ApplicationListener<ContextClosedEvent> {

    @Override
    public void onApplicationEvent(final ContextClosedEvent event) {
        System.out.println("Stopped, closing Executor services : " +  ThreadPools.getMainThreadPool().shutdownNow() + " " + ThreadPools.getSqlThreadPool().shutdownNow());
    }
}