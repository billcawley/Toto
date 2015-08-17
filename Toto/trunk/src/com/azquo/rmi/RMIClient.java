package com.azquo.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by cawley on 20/05/15.
 *
 * Accessing the database server. Need to make this work for multiple DBs, going for a simple map initialls
 *
 */
public class RMIClient {

    // I'm not sure if this is best practice but I was writing functions that were just passing through to this so I'll make it available here
    // todo - check singleton pattern which I never use properly

    private Map<String, RMIInterface> rmiInterfaceMap = new ConcurrentHashMap<>();

/*    public RMIClient() throws Exception{
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", 12345);
        for (int i = 0; i < 5; i++){
            try{
                this.serverInterface = (RMIInterface) registry.lookup(RMIInterface.serviceName);
                break;
            } catch (Exception e){
                if (i == 4){
                    throw e;
                }
                Thread.sleep(3000); // try again in 3 seconds
            }
        }
        System.out.println("Rmi client set up");
    }*/

    // this perhaps could be more robust if an unused database is tried fpr the first time concurrently?
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
