package com.azquo.rmi;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Created by cawley on 20/05/15.
 */
public class RMIClient {

    private RMIInterface serverInterface;

    public RMIClient() {
        try{
            Registry registry = LocateRegistry.getRegistry("localhost", 12345);
            this.serverInterface = (RMIInterface) registry.lookup(RMIInterface.serviceName);
            System.out.println("Rmi client set up");
        } catch (Exception e){
            System.out.println("problem setting up RMI client");
            e.printStackTrace();
        }
    }

    public void testRMI() throws RemoteException, NotBoundException {
        System.out.println(serverInterface.testRMI());
    }

}
