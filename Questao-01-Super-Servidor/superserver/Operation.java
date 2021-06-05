package superserver;

import java.io.Serializable;

public class Operation implements Serializable{
	private int numOperation;
	private String desc;
	public Operation(int numOperation, String desc) {
		super();
		this.numOperation = numOperation;
		this.desc = desc;
	}
	public int getNumOperation() {
		return numOperation;
	}
	public void setNumOperation(int numOperation) {
		this.numOperation = numOperation;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	
}
