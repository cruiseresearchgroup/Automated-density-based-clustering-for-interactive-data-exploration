package thesis.util;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.postgis.PGgeometry;
import org.postgis.Point;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

public class DB
{
	private static final String url = "jdbc:postgresql://localhost:5432/riskmap";
	private static final String username = "postgres";
	private static final String password = "password";
	
	// try different number to see which one is faster
	private static final int batchSizeHDBSCAN = 900; 

	public static String geom = "geom_3857"; // geom or geom_3857
	
	public static Connection getConnection() throws ClassNotFoundException,
			SQLException
	{
		Connection conn;

		/*
		 * Load the JDBC driver and establish a connection.
		 */
		Class.forName("org.postgresql.Driver");
		conn = DriverManager.getConnection(url, username, password);

		/*
		 * Add the geometry types to the connection. Note that you must cast the
		 * connection to the pgsql-specific connection implementation before
		 * calling the addDataType() method.
		 */
		((org.postgresql.PGConnection) conn).addDataType("geometry",
				Class.forName("org.postgis.PGgeometry"));
		((org.postgresql.PGConnection) conn).addDataType("box3d",
				Class.forName("org.postgis.PGbox3d"));

		return conn;
	}

	public static List<MyPoint> getPointsFromDatabase()
			throws ClassNotFoundException, SQLException
	{
		List<MyPoint> points = new ArrayList<MyPoint>();

		Connection conn = getConnection();

		Statement s = conn.createStatement();
		String query = "SELECT node_id, " + geom + " from nodes_road_geoms";
		ResultSet r = s.executeQuery(query);

		while (r.next())
		{
			int node_id = r.getInt("node_id");
			PGgeometry geometry = (PGgeometry) r.getObject(geom);
			Point p = (Point) geometry.getGeometry();
			points.add(new MyPoint(node_id, p.getX(), p.getY()));
		}

		s.close();
		conn.close();

		return points;
	}
	
	public static void getPointsWithinBBOXFromDB(List<MyPoint> points, String extent,
			String whereClause) throws ClassNotFoundException, SQLException
	{
		Connection con = getConnection();

		Statement s = con.createStatement();
		ResultSet rs = null;

		try
		{
			String query = "SELECT node_id, " + geom
					+ " FROM nodes_road_geoms " + whereClause;

			rs = s.executeQuery(query);

			while (rs.next())
			{
				int node_id = rs.getInt("node_id");
				Point p = (Point) ((PGgeometry) rs.getObject(geom))
						.getGeometry();
				points.add(new MyPoint(node_id, p.getX(), p.getY()));
			}
		}
		finally
		{
			if (rs != null)
			{
				rs.close();
			}
			if (s != null)
			{
				s.close();
			}
			if (con != null)
			{
				con.close();
			}
		}
	}
	
	public static Integer[] getNumOfNeighbors(String whereClause, double distance) throws ClassNotFoundException, SQLException
	{
		Integer[] numOfNeighbors = null;
		Connection con = getConnection();

		Statement s = con.createStatement();
		ResultSet rs = null;

		try
		{
			String query = "with nn as (select (array("
					+ "select n2.node_id as n2_id from nodes_road_geoms n2 "
					+ whereClause
					+ " and n2.node_id != n1.node_id and "
					+ "st_dwithin(n1."
					+ geom
					+ ", n2."
					+ geom
					+ ", "
					+ distance
					+ "))) as n2_id "
					+ "from nodes_road_geoms n1 "
					+ whereClause
					+ ")"
					+ "select coalesce(array_length(n2_id, 1),0) as num_neighbors "
					+ "from nn";

			rs = s.executeQuery(query);

			List<Integer> temp = new ArrayList<Integer>();
			while (rs.next())
			{
				temp.add(rs.getInt("num_neighbors"));
			}
			numOfNeighbors = temp.toArray(new Integer[temp.size()]);
		}
		finally
		{
			if (rs != null)
			{
				rs.close();
			}
			if (s != null)
			{
				s.close();
			}
			if (con != null)
			{
				con.close();
			}
		}

		return numOfNeighbors;
	}

	public static Double[] getSortedKDistance(String extent, String whereClause, int k)
			throws ClassNotFoundException, SQLException
	{
		Connection con = getConnection();

		Statement s = con.createStatement();
		ResultSet rs = null;

		List<Double> kdist = new ArrayList<Double>();

		try
		{
			String query = "with knn as (select (select dist from ("
					+ "select st_distance(n1." + geom + ", n2." + geom
					+ ") as dist " + "from nodes_road_geoms n2 " + whereClause
					+ " order by n1." + geom + " <-> n2." + geom + " "
					+ "limit " + k + ") d order by dist desc "
					+ "limit 1) as kdist from nodes_road_geoms n1 "
					+ whereClause + ") "
					+ "select kdist from knn order by kdist";
			rs = s.executeQuery(query);

			while (rs.next())
			{
				kdist.add(rs.getDouble("kdist"));
			}
		}
		finally
		{
			if (rs != null)
				rs.close();

			if (s != null)
				s.close();

			if (con != null)
				con.close();
		}

		return kdist.toArray(new Double[kdist.size()]);
	}
	
	public static int[] updateCertainDatabase(List<Cluster<MyPoint>> cluster, String tableNameExtension) 
			throws SQLException, ClassNotFoundException, IOException
	{
		int totalNodesInCluster = 0;
		int maxCluster = 0;
		String tableName = "cluster_" + tableNameExtension;

		Connection conn = getConnection();
		try
		{
			// Remove all rows from table
			PreparedStatement trunc = conn
					.prepareStatement("TRUNCATE " + tableName);
			try
			{
				trunc.executeUpdate();
			}
			finally
			{
				trunc.close();
			}

			// Do bulk insertion using COPY

			int i = 0;

			StringBuilder sb = new StringBuilder();
			CopyManager cm = new CopyManager((BaseConnection) conn);
			PushbackReader pr = new PushbackReader(new StringReader(""), 10000);
			
			try
			{
				int clusterId = 0;
				for (Cluster<MyPoint> c : cluster)
				{
					clusterId++;

					int clusterSize = c.getPoints().size();
					totalNodesInCluster += clusterSize;
					if (maxCluster < clusterSize)
						maxCluster = clusterSize;
					
					for (MyPoint p : c.getPoints())
					{
						/* Format: node_id,clusterId */
						sb.append(p.getId()).append(",").append(clusterId)
								.append("\n");

						i++;
						if (i % batchSizeHDBSCAN == 0)
						{
							pr.unread(sb.toString().toCharArray());
							cm.copyIn("COPY " + tableName + " FROM STDIN WITH CSV",
									pr);
							sb.delete(0, sb.length());
						}
					}
				}
				pr.unread(sb.toString().toCharArray());
				cm.copyIn("COPY " + tableName + " FROM STDIN WITH CSV", pr);
			}
			finally
			{
				pr.close();
			}
		}
		finally
		{
			conn.close();
		}

		return new int[] { maxCluster, totalNodesInCluster };
	
	}
	
	public static int[] updateDatabase(List<Cluster<MyPoint>> cluster)
			throws SQLException, ClassNotFoundException, IOException
	{
		int totalNodesInCluster = 0;
		int maxCluster = 0;

		Connection conn = getConnection();
		try
		{
			// Remove all rows from table
			PreparedStatement trunc = conn
					.prepareStatement("TRUNCATE nodes_cluster");
			try
			{
				trunc.executeUpdate();
			}
			finally
			{
				trunc.close();
			}

			// Do bulk insertion using COPY

			int batchSize = 900; // try different number to see which one is
									// faster
			int i = 0;

			StringBuilder sb = new StringBuilder();
			CopyManager cm = new CopyManager((BaseConnection) conn);
			PushbackReader pr = new PushbackReader(new StringReader(""), 10000);
			try
			{
				int clusterId = 0;
				for (Cluster<MyPoint> c : cluster)
				{
					clusterId++;

					int clusterSize = c.getPoints().size();
					totalNodesInCluster += clusterSize;
					if (maxCluster < clusterSize)
						maxCluster = clusterSize;

					for (MyPoint p : c.getPoints())
					{
						sb.append(p.getId()).append(",").append(clusterId)
								.append("\n");

						i++;
						if (i % batchSize == 0)
						{
							pr.unread(sb.toString().toCharArray());
							cm.copyIn("COPY nodes_cluster FROM STDIN WITH CSV",
									pr);
							sb.delete(0, sb.length());
						}
					}
				}
				pr.unread(sb.toString().toCharArray());
				cm.copyIn("COPY nodes_cluster FROM STDIN WITH CSV", pr);
			}
			finally
			{
				pr.close();
			}
		}
		finally
		{
			conn.close();
		}

		return new int[] { maxCluster, totalNodesInCluster };
	}
	
	public static double[] getRangeArray(int numOfClass) throws SQLException, ClassNotFoundException
	{
		Connection con = getConnection();

		Statement s = con.createStatement();
		ResultSet rs = null;

		double[] rangeArray =  new double[numOfClass];
		int i = 0;
		
		try
		{
			String query = "SELECT unnest(CDB_JenksBins(array_agg(density), " + numOfClass + ", 5, false)) as range "
					+ "from (select ((count(c.node_id)::integer)/(st_area(st_convexhull(st_collect(" + geom + ")))/1000000))::numeric as density "
							+ "from nodes_cluster c join nodes_road_geoms n on c.node_id = n.node_id group by c.cluster) a";
			rs = s.executeQuery(query);

			while (rs.next())
			{
				rangeArray[i++] = rs.getDouble("range");
			}
		}
		finally
		{
			if (rs != null)
				rs.close();

			if (s != null)
				s.close();

			if (con != null)
				con.close();
		}

		return rangeArray;

	}
}
