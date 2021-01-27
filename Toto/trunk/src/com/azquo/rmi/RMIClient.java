package com.azquo.rmi;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Accessing the database server. Need to make this work for multiple DBs, going for a simple map initially
 * <p>
 * Moving to static, as with the daos I see little advantage in a singleton.
 *
 * Note : 27/01/2021 now this is being used in anger on a different machine I need to add exception catching code if the database server restarted
 *
 * funnily enough this may take some cues from Feefo v1 code!
 */
public class RMIClient {

    private static final Map<String, RMIInterface> rmiInterfaceMap = new ConcurrentHashMap<>();

    // this perhaps could be more robust if an unused database is tried for the first time concurrently?
    // is it really a big problem? Computeifabsent swallows the exceptions, I'd rather they were chucked up . . .

    public static RMIInterface getServerInterface(String ip) {
        return rmiInterfaceMap.computeIfAbsent(ip, s -> {
            RMIInterface toReturn = null;
            try {
                Registry registry = LocateRegistry.getRegistry(ip, 12345);
                toReturn = (RMIInterface) registry.lookup(RMIInterface.serviceName);
                System.out.println("Rmi client set up for " + ip);
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
            return toReturn;
        });

        // test and try to reconnect? Not as efficient as it might be but otherwise I have a serious mess of error catching.
    }

    /*

        public static void setUp() throws Exception {
            // this will be the same as
        }

        public static void remoteResetVendorsCheckedByCron() {
            try {
                checkSetup();
                serv.remoteResetVendorsCheckedByCron();
            } catch (Exception e) {
                System.err.println("Remoteservice exception:" + e.getMessage());
                try {
                    // try again in case something dodgy happened
                    setUp();
                    serv.remoteResetVendorsCheckedByCron();
                } catch (Exception e2) {
                    System.err.println("Remoteservice exception after seetting up again:" + e2.getMessage());
                }
                //e.printStackTrace();
            }
        }*/

}