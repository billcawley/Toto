package com.azquo.rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by cawley on 20/05/15.
 */
public class RMIImplementation implements RMIInterface {
    @Override
    public String testRMI() {
        return "here is the test!";
    }
}
