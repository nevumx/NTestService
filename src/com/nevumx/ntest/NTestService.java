package com.nevumx.ntest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.ws.rs.GET;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.Random;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

@Path("/nservice")
public class NTestService {
	private static final String dbName = "riot";

	@GET
	@Produces("text/plain")
	public String defaultService() {
		return fetchRiotUser("RiotSchmick");
	}

	@Path("{name}")
	@GET
	@Produces("text/plain")
	public String stringService(@PathParam("name") String name) {
		return fetchRiotUser(name);
	}

	@DELETE
	@Produces("text/plain")
	public String clearCache() {
		String responseStr = new String();

		Connection connection = null;
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			connection = DriverManager.getConnection(
			"jdbc:mysql://" + System.getenv("DB_IP") + ":3306/?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC","root",System.getenv("DB_PASS"));

			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("show databases");
			boolean destroy = dbIsPresent(resultSet);
			statement.close();

			if (destroy) {
				statement = connection.createStatement();
				statement.execute("drop database " + dbName);
				statement.close();
			}

			responseStr = "Cache cleared.";
		} catch (Exception e) {
			responseStr = "Cache clear Error: " + e.toString();
		} finally {
			if (connection != null)
			{
				try {
					connection.close();
				} catch (Exception e) {
					responseStr = "Cache clear Error: " + e.toString() + " Riot API Result: " + responseStr;
				}
			}
		}

		return responseStr + "\r\n";
	}

	private String fetchRiotUser(String userName) {
		String responseStr = new String();
		boolean httpError = false;

		// Simulate an API failure rate of 50%
		if (new Random().nextBoolean()) {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpGet httpget = new HttpGet("https://na1.api.riotgames.com/lol/summoner/v3/summoners/by-name/" + userName + "?api_key=" + System.getenv("RIOT_API_KEY"));
			CloseableHttpResponse response = null;

			try {
				response = httpclient.execute(httpget);
				HttpEntity entity = response.getEntity();

				if (entity != null) {
					responseStr = inputSteamToString(entity.getContent());
				} else {
					responseStr = "Invalid HttpEntity.";
					httpError = true;
				}
			} catch (Exception e) {
				responseStr = e.toString();
				httpError = true;
			} finally {
				if (response != null)
				{
					try {
						response.close();
					} catch (Exception e) {
						responseStr = e.toString();
						httpError = true;
					}
				}
			}
		}
		else
		{
			responseStr = "Simulated Riot API failure.";
			httpError = true;
		}

		Connection connection = null;
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			connection = DriverManager.getConnection(
			"jdbc:mysql://" + System.getenv("DB_IP") + ":3306/?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC","root",System.getenv("DB_PASS"));

			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("show databases");
			boolean init = !dbIsPresent(resultSet);
			statement.close();
			statement = connection.createStatement();
			if (init) {
				// db not initted. init before continuing.
				statement.execute("create database " + dbName);
				statement.close();
				statement = connection.createStatement();
				statement.execute("use " + dbName);
				statement.close();
				statement = connection.createStatement();
				statement.execute("create table RiotCache ( SummonerID varchar(127) not null unique, SummonerData varchar(255) not null unique, primary key (SummonerID) );");
				statement.close();
			} else {
				statement.execute("use " + dbName);
				statement.close();
			}

			statement = connection.createStatement();

			// If any errors, try to get cached data, otherwise, cache the data.
			if (httpError) {
				resultSet = statement.executeQuery("select SummonerData from RiotCache where SummonerID = '" + userName + "'");
				if (resultSet.next()) {
					responseStr = "Riot API Failure: " + responseStr + " Cached data:" + resultSet.getString(1);
				} else {
					responseStr = "Riot API Failure: " + responseStr + " and no cached data...";
				}
				statement.close();
			} else {
				statement.execute("delete from RiotCache where SummonerID = '" + userName + "'");
				statement.close();
				statement = connection.createStatement();  
				statement.execute("insert into RiotCache values ('" + userName + "', '" + responseStr + "')");
				statement.close();
			}
		} catch (Exception e) {
			if (httpError) {
				responseStr = "Riot API Failure: " + responseStr + " Cached data Error: " + e.toString();
			} else {
				responseStr = "Data caching Error: " + e.toString() + " Riot API Result: " + responseStr;
			}
		} finally {
			if (connection != null)
			{
				try {
					connection.close();
				} catch (Exception e) {
					if (httpError) {
						responseStr = "Riot API Failure: " + responseStr + " Cached data Error: " + e.toString();
					} else {
						responseStr = "Cached data Error: " + e.toString() + " Riot API Result: " + responseStr;
					}
				}
			}
		}

		return responseStr + "\r\n";
	}

	private String inputSteamToString(InputStream stream) throws IOException {
		String outStr = new String();
		if (stream != null) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			StringBuilder out = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				out.append(line);
			}
			outStr = out.toString();
			reader.close();
			stream.close();
		}
		return outStr;
	}

	private boolean dbIsPresent(ResultSet resultSet)
	{
		try {
			while (resultSet.next()) {
				if (resultSet.getString(1).equals(dbName)) {
					return true;
				}
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}
}
