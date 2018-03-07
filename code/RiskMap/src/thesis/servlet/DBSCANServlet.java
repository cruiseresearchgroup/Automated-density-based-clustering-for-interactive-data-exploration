package thesis.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.json.JSONArray;
import org.json.JSONObject;

import thesis.algorithm.hdbscanstar.distance.EuclideanDistance;
import thesis.util.DB;
import thesis.util.MyPoint;
import thesis.util.Others;

/**
 * Servlet implementation class ExtentServlet
 */
@WebServlet("/ExtentServlet")
public class DBSCANServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;

	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException
	{
		BufferedReader br = new BufferedReader(new InputStreamReader(
				request.getInputStream()));
		String json = "";
		if (br != null)
			json = br.readLine();

		JSONObject jo = new JSONObject(json);
		JSONArray bbox = new JSONArray();

		try
		{
			if (jo.has("extent"))
				bbox = jo.getJSONArray("extent");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		response.setContentType("application/json");

		try
		{
			List<MyPoint> points = new ArrayList<MyPoint>();
			String extent = "";
			String whereClause = "";

			// Wwhen "bbox" is not specified in the parameter, get all points from database
			if (bbox.length() == 0)
			{
				// Get the points from PostgreSQL
				points = DB.getPointsFromDatabase();
			}
			else
			{
				extent = bbox.getDouble(0) + "," + bbox.getDouble(1) + ","
						+ bbox.getDouble(2) + "," + bbox.getDouble(3);

				if (DB.geom.equals("geom"))
				{
					whereClause = "where " + DB.geom + " && st_transform(st_makeenvelope(" + extent
							+ ", 3857), 4283)";
				}
				else
				{
					whereClause = "where " + DB.geom + " && st_makeenvelope(" + extent + ", 3857)";
				}

				DB.getPointsWithinBBOXFromDB(points, extent, whereClause);
			}

			int minpts;
			double eps;

			if (!jo.has("minpts") || jo.get("minpts").toString().isEmpty())
				minpts = (int) Math.log(points.size());
			else
				minpts = jo.getInt("minpts");

			JSONObject result = new JSONObject();
			result.put("minpts", minpts);

			if (minpts > 1)
			{
				/*
				 * If the user doesn't specify eps, find the knee of sorted k
				 * distance with k = minpts. eps is rounded up to 3 decimal
				 * places
				 */
				if (!jo.has("eps") || jo.get("eps").toString().isEmpty())
					eps = (double) Math
							.round(findKnee(DB.getSortedKDistance(extent, whereClause, minpts)) * 1000) / 1000;
				else
					eps = jo.getDouble("eps");

				DBSCANClusterer<MyPoint> dbscan = new DBSCANClusterer<MyPoint>(
						eps, minpts);
				List<Cluster<MyPoint>> cluster = dbscan.cluster(points);
				int[] clusterRes = DB.updateDatabase(cluster);
				
				int numOfClass = 4;
				double[] rangeArray = DB.getRangeArray(numOfClass);

				int totalPoints = points.size();

				double dbi = Others.dbi(cluster, new EuclideanDistance());

				result.put("eps", eps);
				result.put("cluster", cluster.size());
				result.put("rangeArray", rangeArray);
				result.put("noise", totalPoints - clusterRes[1]);
				result.put("size", totalPoints);
				result.put("dbi", dbi);

				File f = new File(getServletContext().getRealPath("/")
						+ "conf.json");
				f.createNewFile();
				FileWriter file = new FileWriter(f);
				try
				{
					file.write(result.toString());
					result.put("path", f.getAbsolutePath());
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
				finally
				{
					file.flush();
					file.close();
				}
			}
			PrintWriter out = response.getWriter();
			out.print(result);
			out.flush();
			out.close();
		}
		catch(ClassNotFoundException e)
		{
			throw new ServletException(e);
		}
		catch(SQLException e)
		{
			throw new ServletException(e);
		}
		catch(Exception e)
		{
			throw new ServletException(e);
		}
	}

	private static double findKnee(Double[] doubles)
	{
		int nPoints = doubles.length;
		int end = nPoints - 1;

		/* get vector between first and last point - this is the line */
		double lineVec = doubles[end] - doubles[0];

		double sqrt = Math.sqrt((end * end) + (lineVec * lineVec));

		/* normalize the line vector */
		double lineVecN0 = end / sqrt;
		double lineVecN1 = lineVec / sqrt;

		double vecFromFirst, scalarProduct, vecToLine0, vecToLine1, distToLine;
		double tempMax = -1;
		int tempIndex = 0;
		for (int i = 0; i < nPoints; i++)
		{
			vecFromFirst = doubles[i] - doubles[0];
			scalarProduct = (i * lineVecN0) + (vecFromFirst * lineVecN1);
			vecToLine0 = i - scalarProduct * lineVecN0;
			vecToLine1 = vecFromFirst - scalarProduct * lineVecN1;
			distToLine = Math.sqrt((vecToLine0 * vecToLine0)
					+ (vecToLine1 * vecToLine1));
			if (i == 0 || distToLine > tempMax)
			{
				tempMax = distToLine;
				tempIndex = i;
			}
		}

		return doubles[tempIndex];
	}
}
