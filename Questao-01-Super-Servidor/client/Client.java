package client;

import java.net.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

import operator.iMath;

import superserver.CRUD;

public class Client {
	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		System.out.println("Digite ok para conectar a um operador aleat�rio");
		String line = scan.nextLine();
		while (line.equalsIgnoreCase("ok")) {
			try {
				Registry registry = LocateRegistry.getRegistry("localhost");
				CRUD stubServer = (CRUD) registry.lookup("superserver");
				String canonicalId = stubServer.getCanonicalId();
				/* TENTATIVA DE CONECTAR A UM OPERATOR */
				boolean connectedToOperator = false;
				iMath stubOperator = null;
				while (!connectedToOperator) {
					System.out.print("tentando conectar a " + canonicalId + " ");
					stubOperator = (iMath) registry.lookup(canonicalId);
					connectedToOperator = true;
					System.out.println("[OK]");
				}
				while (line.equalsIgnoreCase("ok")) {// fica na mesma r�plica
					System.out.println("====== MENU =======");
					System.out.println("1 - Soma");
					System.out.println("2 - Subtra��o");
					System.out.println("3 - Multiplica��o");
					System.out.println("4 - Divis�o");
					System.out.println("Digite a op��o desejada: ");
					String op = scan.nextLine();
					double x, y, res = 0;
					System.out.println("Digite x e y: ");
					x = scan.nextDouble();
					y = scan.nextDouble();
					scan.nextLine();
					switch (op) {
					case "1":
						res = stubOperator.sum(x, y);
						break;
					case "2":
						res = stubOperator.diff(x, y);
						break;
					case "3":
						res = stubOperator.mult(x, y);
						break;
					case "4":
						res = stubOperator.div(x, y);
						break;
					}
					System.out.println("Resultado = " + res);
					System.out.println("Deseja outra rodada de opera��es?");
					line = scan.nextLine();
				}
			} catch (RemoteException e) {
				System.err.println("Erro: " + e.getCause());
			} catch (NotBoundException e) {
				System.err.println("N�o existe mais esse operador... Falha detectada!");
			}
			// p�s falha pergunta se quer tentar em outro operador
			System.out.println("Digite ok para conectar a outro operador aleat�rio");
			line = scan.nextLine();
		}
		System.out.println("Fim do processo cliente...");
	}
}
