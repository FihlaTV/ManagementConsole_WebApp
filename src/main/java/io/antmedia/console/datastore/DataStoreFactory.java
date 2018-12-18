package io.antmedia.console.datastore;

public class DataStoreFactory {

	private IDataStore dataStore;
	private String appName;
	private String dbName;
	private String dbType;
	private String dbHost;
	private String dbUser;
	private String dbPassword;
	
	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	public String getDbName() {
		return dbName;
	}

	public void setDbName(String dbName) {
		this.dbName = dbName;
	}

	public String getDbType() {
		return dbType;
	}

	public void setDbType(String dbType) {
		this.dbType = dbType;
	}

	public String getDbHost() {
		return dbHost;
	}

	public void setDbHost(String dbHost) {
		this.dbHost = dbHost;
	}

	public String getDbUser() {
		return dbUser;
	}

	public void setDbUser(String dbUser) {
		this.dbUser = dbUser;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	public IDataStore getDataStore() {
		if (dataStore == null) {
			if(dbType .contentEquals("mongodb"))
			{
				
				dataStore = new MongoStore(dbHost, dbUser, dbPassword);
			}
			else if(dbType.contentEquals("mapdb"))
			{
				dataStore = new MapDBStore();
			}
		}
		return dataStore;
	}
}
	
