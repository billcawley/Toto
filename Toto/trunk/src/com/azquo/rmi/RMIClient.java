package com.azquo.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Accessing the database server. Need to make this work for multiple DBs, going for a simple map initially
 * <p>
 * Moving to static, as with the daos I see little advantage in a singleton.
 */
public class RMIClient {

    private static Map<String, RMIInterface> rmiInterfaceMap = new ConcurrentHashMap<>();

    // this perhaps could be more robust if an unused database is tried for the first time concurrently?
    // is it really a big problem? Computeifabsent swallows the exceptions, I'd rather they were chucked up . . .

    public static RMIInterface getServerInterface(String ip) throws Exception {
        RMIInterface toReturn = rmiInterfaceMap.get(ip);
        if (toReturn == null) {
            // yes this could run a few times concurrently at the beginning, not sure it's a big problem
            Registry registry = LocateRegistry.getRegistry(ip, 12345);
            toReturn = (RMIInterface) registry.lookup(RMIInterface.serviceName);
            rmiInterfaceMap.put(ip, toReturn);
            System.out.println("Rmi client set up for " + ip);
        }
        return toReturn;
    }
}