package skipGraph;

public class NodeConfig {
	
	private int maxLevels;
	private int port;
	private int numID;
	private int shardID;
	private int maxShards;
	private String nameID;
	
	public NodeConfig(int maxLevels, int maxShards, int port, int numID, String nameID) {
		this.maxLevels = maxLevels;
		this.port = port;
		this.numID = numID;
		this.nameID = nameID;
		this.maxShards = maxShards;
	}

	public int getMaxLevels() {
		return maxLevels;
	}

	public void setMaxLevels(int maxLevels) {
		this.maxLevels = maxLevels;
	}

	public void setShardID (int shardID) {
		this.shardID = shardID;
	}

	public int getShardID () {
		return this.shardID;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getNumID() {
		return numID;
	}

	public void setNumID(int numID) {
		this.numID = numID;
	}

	public String getNameID() {
		return nameID;
	}

	public void setNameID(String nameID) {
		this.nameID = nameID;
	}

	public void setMaxShards(int maxShards) {
		this.maxShards = maxShards;
	}

	public int getMaxShards() {
		return this.maxShards;
	}
	
}
