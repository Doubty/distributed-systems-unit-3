package operator;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import superserver.CRUD;
import superserver.Operation;

public class Operator implements iMath {

	/* array para controlar o log */
	private List<Operation> logOperations = new ArrayList<>();

	public static void main(String[] args) {
		try {
			Registry registry = LocateRegistry.getRegistry("localhost");
			String name = "op";// id do operador
			iMath imath = new Operator();
			iMath stub = (iMath) UnicastRemoteObject.exportObject(imath, 0);
			// ========== BUSCA DE ID ===============
			CRUD stubServer = (CRUD) registry.lookup("superserver");
			// pega id
			long id = stubServer.getIdOperator();
			// op0, op1, op2, op3, op4, ..., opx
			// adiciona um no contador
			stubServer.addOperator();
			// ========== REGISTRO DE NOVO OPERADOR ===============
			registry.rebind(name + String.valueOf(id), stub);
			System.out.println("Operator " + id + " is ON!");
			if (id == 0) {
				System.out.println("apagando 0");
				stubServer.delOperator(registry, name + String.valueOf(id));
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		} // pega rmi registry do superserver localhost
		catch (NotBoundException e) {
			e.printStackTrace();
		}
	}

	@Override
	public double sum(double x, double y) throws RemoteException {
		System.out.println("realizando soma");
		Registry registry = LocateRegistry.getRegistry("localhost");
		CRUD stubServer = null;
		try {
			stubServer = (CRUD) registry.lookup("superserver");
			int numOperation = stubServer.getNumOperation();
			String op = String.valueOf(x) + " + " + String.valueOf(y);
			Operation operation = new Operation(numOperation, op);
			// atualiza o log das réplicas
			Random random = new Random();
			int offset = random.nextInt(6) + 1;//1 a 5 segundos
			Thread.sleep(offset*1000);
			for (String idOperator : registry.list()) {
				System.out.println("xxxxxxx "+idOperator);
				if (!"superserver".equals(idOperator)) {
					iMath imath = (iMath) registry.lookup(idOperator);
					System.out.println("Mandando operação para réplica " + idOperator);
					imath.setOperation(operation);
				}
			}
		} catch (RemoteException | NotBoundException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return x + y;
	}

	@Override
	public double diff(double x, double y) throws RemoteException {
		System.out.println("realizando menos");
		Registry registry = LocateRegistry.getRegistry("localhost");
		CRUD stubServer = null;
		try {
			stubServer = (CRUD) registry.lookup("superserver");
			int numOperation = stubServer.getNumOperation();
			String op = String.valueOf(x) + " - " + String.valueOf(y);
			Operation operation = new Operation(numOperation, op);
			// atualiza o log das réplicas
			Random random = new Random();
			int offset = random.nextInt(6) + 1;//1 a 5 segundos
			Thread.sleep(offset*1000);
			for (String idOperator : registry.list()) {
				System.out.println("xxxxxxx "+idOperator);
				if (!"superserver".equals(idOperator)) {
					iMath imath = (iMath) registry.lookup(idOperator);
					System.out.println("Mandando operação para réplica " + idOperator);
					imath.setOperation(operation);
				}
			}
		} catch (RemoteException | NotBoundException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return x - y;
	}

	@Override
	public double mult(double x, double y) throws RemoteException {
		System.out.println("realizando multiplicação");
		Registry registry = LocateRegistry.getRegistry("localhost");
		CRUD stubServer = null;
		try {
			stubServer = (CRUD) registry.lookup("superserver");
			int numOperation = stubServer.getNumOperation();
			String op = String.valueOf(x) + " * " + String.valueOf(y);
			Operation operation = new Operation(numOperation, op);
			// atualiza o log das réplicas
			Random random = new Random();
			int offset = random.nextInt(6) + 1;//1 a 5 segundos
			Thread.sleep(offset*1000);
			for (String idOperator : registry.list()) {
				System.out.println("xxxxxxx "+idOperator);
				if (!"superserver".equals(idOperator)) {
					iMath imath = (iMath) registry.lookup(idOperator);
					System.out.println("Mandando operação para réplica " + idOperator);
					imath.setOperation(operation);
				}
			}
		} catch (RemoteException | NotBoundException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return x * y;
	}

	@Override
	public double div(double x, double y) throws RemoteException {
		System.out.println("realizando divisão");
		Registry registry = LocateRegistry.getRegistry("localhost");
		CRUD stubServer = null;
		try {
			stubServer = (CRUD) registry.lookup("superserver");
			int numOperation = stubServer.getNumOperation();
			String op = String.valueOf(x) + " / " + String.valueOf(y);
			Operation operation = new Operation(numOperation, op);
			// atualiza o log das réplicas
			Random random = new Random();
			int offset = random.nextInt(6) + 1;//1 a 6 segundos
			Thread.sleep(offset*1000);
			for (String idOperator : registry.list()) {
				if (!"superserver".equals(idOperator)) {
					iMath imath = (iMath) registry.lookup(idOperator);
					System.out.println("Mandando operação para réplica " + idOperator);
					imath.setOperation(operation);
				}
			}
			System.out.println("=======================================");
		} catch (RemoteException | NotBoundException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return x / y;
	}

	/* ordena o log baseado no número de operação */
	public void sortLog() {
		Collections.sort(logOperations, new Comparator<Operation>() {
			@Override
			public int compare(Operation o1, Operation o2) {
				return o1.getNumOperation() - o2.getNumOperation();
			}
		});
	}

	@Override
	public void setOperation(Operation op) throws RemoteException {
		logOperations.add(op);
		System.out.println("Listando log");
		for (Operation o : logOperations) {
			System.out.println(o.getNumOperation() + " - " + o.getDesc());
		}
		System.out.println("ordenando log");
		sortLog();
		System.out.println("Listando log ordenado");
		for (Operation o : logOperations) {
			System.out.println(o.getNumOperation() + " - " + o.getDesc());
		}
	}
}
