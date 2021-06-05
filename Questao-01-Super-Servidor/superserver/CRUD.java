package superserver;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;

public interface CRUD extends Remote{
	public void addOperator() throws RemoteException;//adiciona uma unidade no contador de operadores
	public long getIdOperator() throws RemoteException;//retorna id para o operator cadastra no rmi registry
	public void delOperator(Registry registry, String canonicalId) throws RemoteException;//método para deletar operator do rmi registry
	public String getCanonicalId() throws RemoteException;//retorna id completo. //op1, op2, op3, op4, ..., opx
	public int getNumOperation() throws RemoteException;//retorna id completo. //op1, op2, op3, op4, ..., opx
}
