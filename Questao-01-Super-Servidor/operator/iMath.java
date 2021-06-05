package operator;

import java.rmi.Remote;
import java.rmi.RemoteException;

import superserver.Operation;

public interface iMath extends Remote{
	public double sum(double x, double y) throws RemoteException;
	public double diff(double x, double y) throws RemoteException;
	public double mult(double x, double y) throws RemoteException;
	public double div(double x, double y) throws RemoteException;
	public void setOperation(Operation o) throws RemoteException;
}
