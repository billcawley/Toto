package com.azquo.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 20/05/15.
 *
 * Accessing the database server. Need to make this work for multiple DBs, going for a simple map initialls
 *
 */
public class RMIClient {

    // I'm not sure if this is best practice but I was writing functions that were just passing through to this so I'll make it available here
    // todo - check singleton pattern which I never use properly

    private Map<String, RMIInterface> rmiInterfaceMap = new ConcurrentHashMap<>();

    // this perhaps could be more robust if an unused database is tried for the first time concurrently?
    // is it really a big problem?

    public RMIInterface getServerInterface(String ip) throws Exception{
        if (rmiInterfaceMap.get(ip) == null){
            Registry registry = LocateRegistry.getRegistry(ip, 12345);
            rmiInterfaceMap.put(ip,(RMIInterface) registry.lookup(RMIInterface.serviceName));
            System.out.println("Rmi client set up for " + ip);
        }
        return rmiInterfaceMap.get(ip);
    }
}
