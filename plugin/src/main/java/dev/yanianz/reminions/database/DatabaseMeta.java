package dev.yanianz.reminions.database;

public class DatabaseMeta {
    private String fileName;
    private String host;
    private int port;
    private String password;
    private String databaseName;
    private String user;
    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private String mongoUri;

    public String getFileName()    { return this.fileName; }
    public String getHost()        { return this.host; }
    public int getPort()           { return this.port; }
    public String getPassword()    { return this.password; }
    public String getDatabaseName(){ return this.databaseName; }
    public String getUser()        { return this.user; }
    public String getRedisHost()   { return this.redisHost; }
    public int getRedisPort()      { return this.redisPort; }
    public String getRedisPassword(){ return this.redisPassword; }
    public String getMongoUri()    { return this.mongoUri; }

    public void setFileName(String fileName)        { this.fileName = fileName; }
    public void setHost(String host)                { this.host = host; }
    public void setPort(int port)                   { this.port = port; }
    public void setPassword(String password)        { this.password = password; }
    public void setDatabaseName(String name)        { this.databaseName = name; }
    public void setUser(String user)                { this.user = user; }
    public void setRedisHost(String host)           { this.redisHost = host; }
    public void setRedisPort(int port)              { this.redisPort = port; }
    public void setRedisPassword(String password)   { this.redisPassword = password; }
    public void setMongoUri(String uri)              { this.mongoUri = uri; }
}
