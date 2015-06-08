package com.azquo.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Created by cawley on 20/05/15.
 *
 * it occurs that maybe the server interface itself could be exposed?
 *
 */
public class RMIClient {

    // I'm not sure if this is best practice but I was writing functions that were just passing through to this so I'll make it availiable here
    // todo - check singleton pattern which I never use properly

    private RMIInterface serverInterface = null;

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

    public RMIInterface getServerInterface() throws Exception{
        if (serverInterface == null){
            Registry registry = LocateRegistry.getRegistry("localhost", 12345);
            this.serverInterface = (RMIInterface) registry.lookup(RMIInterface.serviceName);
            System.out.println("Rmi client set up");
        }
        return serverInterface;
    }
}
