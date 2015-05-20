package com.azquo.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
/**
 * Created by cawley on 20/05/15.
 */
public interface RMIInterface extends Remote {
    public final String serviceName = "AzquoRMI";

    public String testRMI() throws RemoteException;
}
