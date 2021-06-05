package superserver;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Random;

public class SuperServer implements CRUD {

	private long numOperators = 0;
	private int numOperation = -1;//para a primeira ser 0
	public static String name = "superserver";

	public static void main(String[] args) {
		CRUD superserver = new SuperServer();
		CRUD stub;
		try {
			stub = (CRUD) UnicastRemoteObject.exportObject(superserver, 0);
			// cria registro
			Registry registry = LocateRegistry.createRegistry(1099);
			registry.rebind(name, stub);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		System.out.println("Super server is ON!");
	}

	@Override
	public void addOperator() {
		++numOperators;
	}

	@Override
	public void delOperator(Registry registry, String canonicalId) {
		try {
			registry.unbind(canonicalId);
		} catch (RemoteException | NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public long getIdOperator() throws RemoteException {
		return numOperators;
	}

	/* retorna id canônico aleatório */
	@Override
	public String getCanonicalId() {
		Random random = new Random();
		// gera números aleatórios de 0 ... numOperators - 1
		long id = random.nextLong();
		System.out.println("gerou long " + id);
		id = Math.abs(id) % numOperators;
		System.out.println("id canonico = " + id);
		return "op" + String.valueOf(id);// id canônico que fica no rmi registry
	}

	@Override
	public int getNumOperation() throws RemoteException {
		return ++numOperation;
	}
}
