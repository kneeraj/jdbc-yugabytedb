package com.yugabyte.examples;

import com.yugabyte.ysql.LoadBalanceProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class UniformLoadBalance {
  private static String controlUrl = "";
  private static int debug_flag=0;
  private static HikariDataSource hikariDataSource;
  static Scanner scanner = new Scanner(System.in);

  public static void main(String[] args) {
    try {
      if(debug_flag==1)
        args = new String[] {"1", "1", "6"};

      String VERBOSE = args[0];
      String INTERACTIVE = args[1];
      String numConnections = "6";
      String controlHost = "127.0.0.1";
      String controlPort = "5433";

      controlUrl = "jdbc:postgresql://" + controlHost
        + ":" + controlPort + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true";

      if(VERBOSE.equals("1")) {
        System.out.println("VERBOSE = " + (VERBOSE.equals("1") ? "True" : "False") +
          " and INTERACTIVE = " + (INTERACTIVE.equals("1") ? "True" : "False"));
        System.out.println("Setting up the connection pool.......");
      }
      Thread.sleep(2000);

      testUsingHikariPool("uniform_load_balance", "true", "simple",
        controlHost, controlPort, numConnections, VERBOSE, INTERACTIVE);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void testUsingHikariPool(String poolName, String lbpropvalue, String lookupKey,
                                         String hostName, String port, String numConnections, String VERBOSE, String INTERACTIVE)  {
    try {
      String ds_yb = "com.yugabyte.ysql.YBClusterAwareDataSource";

      Properties poolProperties = new Properties();
      poolProperties.setProperty("poolName", poolName);
      poolProperties.setProperty("dataSourceClassName", ds_yb);
      poolProperties.setProperty("maximumPoolSize", numConnections);
      poolProperties.setProperty("connectionTimeout", "1000000");
      poolProperties.setProperty("autoCommit", "true");
      poolProperties.setProperty("dataSource.serverName", hostName);
      poolProperties.setProperty("dataSource.portNumber", port);
      poolProperties.setProperty("dataSource.databaseName", "yugabyte");
      poolProperties.setProperty("dataSource.user", "yugabyte");
      poolProperties.setProperty("dataSource.password", "yugabyte");
      poolProperties.setProperty("dataSource.loadBalance", "true");
      if (!lbpropvalue.equals("true")) {
        poolProperties.setProperty("dataSource.topologyKeys", lookupKey);
      }

      HikariConfig hikariConfig = new HikariConfig(poolProperties);
      hikariConfig.validate();
      hikariDataSource = new HikariDataSource(hikariConfig);

      //creating a table
      Connection connection = hikariDataSource.getConnection();
      createTableStatements(connection);
      connection.close();

      //running multiple threads concurrently
      runSqlQueriesOnMultipleThreads();
      Thread.sleep(10000);

      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();

      if(VERBOSE.equals("1")) {
        System.out.println("Verify the connections on the server side using your browser");
        System.out.println("For example, you can \"127.0.0.1:13000/rpcz\"" + " and similarly others...");
      }

      if(INTERACTIVE.equals("1")) {
        interact();
      }

//      System.out.println("=====================first file finding================");

      continueScript("flag1");
//      File file;
      pauseApp(".jdbc_example_app_checker");
//      System.out.println("=====================first file found================");

      makeSomeNewConnections(7);
      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();


      continueScript("flag2");
//      System.out.println("================second file finding=======================");
      pauseApp(".jdbc_example_app_checker2");

//      System.out.println("================second file found=======================");

      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap(); //for debugging

      makeSomeNewConnections(4);
      Thread.sleep(5000);
      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();

      if(VERBOSE.equals("1")) {
        System.out.println("Verify the connections on the server side using your browser");
        System.out.println("For example, you can \"127.0.0.1:13000/rpcz\"" + " and similarly others...");
      }
      if(INTERACTIVE.equals("1")) {
        interact();
      }

      continueScript("flag3");

      pauseApp(".jdbc_example_app_checker3");
      System.out.println("=====================Java app closed================");

//      hikariDataSource.close();
    }catch (SQLException throwables) {
      throwables.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void createTableStatements(Connection connection) {
    try {
      Statement statement = connection.createStatement();
      statement.execute("DROP TABLE IF EXISTS AGENTS");
      String query = "CREATE TABLE AGENTS  ( AGENT_CODE VARCHAR(6) NOT NULL PRIMARY KEY, AGENT_NAME VARCHAR(40), " +
        "WORKING_AREA VARCHAR(35), COMMISSION numeric(10,2), PHONE_NO VARCHAR(15))";
      statement.execute(query);

      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A007', 'Ramasundar', 'Bangalore', '0.15', '077-25814763')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A003', 'Alex ', 'London', '0.13', '075-12458969')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A008', 'Alford', 'New York', '0.12', '044-25874365')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A011', 'Ravi Kumar', 'Bangalore', '0.15', '077-45625874')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A010', 'Santakumar', 'Chennai', '0.14', '007-22388644')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A012', 'Lucida', 'San Jose', '0.12', '044-52981425')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A005', 'Anderson', 'Brisban', '0.13', '045-21447739' )");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A001', 'Subbarao', 'Bangalore', '0.14', '077-12346674')");
    }catch (SQLException throwables) {
      System.out.println("Exception occured at createTable function");
      throwables.printStackTrace();
    }
  }

  private static void runSqlQueriesOnMultipleThreads() throws InterruptedException {
    Thread[] threads = new Thread[4];
//    System.out.println("==============================Threads started running=========================================");
    for (int i = 0; i < 4; i++)
      threads[i] = new Thread(new ConcurrentQueriesClass());

    for (int i = 0; i < 4; i++) {
      threads[i].start();
    }

    for (int i = 0; i < 4; i++) {
      threads[i].join();
    }
//    System.out.println("==============================Threads execution completed====================================");
  }

  private static String [] sqlQueries = new String[] {
    "Select AGENT_NAME, COMMISSION from AGENTS",
    "Select max(COMMISSION) from AGENTS",
    "Select PHONE_NO from AGENTS",
    "Select WORKING_AREA from AGENTS"
  };

  private static void runSqlQueriesUsingHikariPoolConnections(Connection connection) {
    try {
      for(int i=0; i<sqlQueries.length; i++) {
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sqlQueries[i]);
        int cnt =0;
        while (rs.next()) {
          cnt += 1;
        }
      }
      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get("simple").refresh(connection);
    }catch (SQLException throwables) {
      System.out.println("Exception occured at runSqlQueries function");
      throwables.printStackTrace();
    }
  }

  static class ConcurrentQueriesClass implements  Runnable {
    @Override
    public void run() {
      try {
        for (int i = 1; i <= 1000; i++) {
          Connection connection = hikariDataSource.getConnection();
          runSqlQueriesUsingHikariPoolConnections(connection);
          connection.close();
        }
      }
      catch (SQLException throwables) {
        throwables.printStackTrace();
      }
    }
  }


  static void makeSomeNewConnections(int new_connections) {
    System.out.println("ADDING " + new_connections + " New Connections..........");
    try {
      for(int i=1; i<=new_connections; i++) {
        Connection connection = DriverManager.getConnection(controlUrl);
        runSqlQueriesUsingHikariPoolConnections(connection);
      }

    } catch (SQLException throwables) {
      throwables.printStackTrace();
    }


  }

  private static void continueScript(String flagValue) {
    FileWriter fileWriter = null;
    try {
      fileWriter = new FileWriter(".notify_shell_script");
      fileWriter.write(flagValue);
      fileWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static void interact() throws InterruptedException {
    System.out.println("Press any key to continue...");
    Thread.sleep(5000);
  }

  static void pauseApp(String s) {
    File file = new File(s);
    while (file.exists()==false) ;
  }

}
