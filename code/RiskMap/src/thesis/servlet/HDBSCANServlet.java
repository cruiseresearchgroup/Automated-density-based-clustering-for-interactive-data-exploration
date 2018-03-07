package thesis.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.json.JSONArray;
import org.json.JSONObject;

import thesis.algorithm.hdbscanstar.distance.DistanceCalculator;
import thesis.algorithm.hdbscanstar.distance.EuclideanDistance;
import thesis.algorithm.myhdbscanstar.MyHDBSCANStar;
import thesis.util.DB;
import thesis.util.MyPoint;
import thesis.util.Others;

@WebServlet("/HDBSCANServlet")
public class HDBSCANServlet extends HttpServlet
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
		double resolution = Double.NaN;
		int pixel = 0, small_roads_threshold = 0;
		boolean useMode = false;

		try
		{
			if (jo.has("extent"))
				bbox = jo.getJSONArray("extent");

			if (jo.has("res"))
				resolution = jo.getDouble("res");

			if (jo.has("pixel") && !jo.get("pixel").toString().isEmpty())
				pixel = jo.getInt("pixel");

			if (jo.has("usemode"))
				useMode = jo.getBoolean("usemode");

			if (jo.has("small_roads_threshold")
					&& !jo.get("small_roads_threshold").toString().isEmpty())
				small_roads_threshold = jo.getInt("small_roads_threshold");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		response.setContentType("application/json");

		try
		{
			List<MyPoint> points = new ArrayList<MyPoint>();
			String whereClause = "";

			String extent = bbox.getDouble(0) + "," + bbox.getDouble(1) + ","
					+ bbox.getDouble(2) + "," + bbox.getDouble(3);

			if (DB.geom.equals("geom"))
			{
				whereClause = "where " + DB.geom
						+ " && st_transform(st_makeenvelope(" + extent
						+ ", 3857), 4283)";
			}
			else
			{
				whereClause = "where " + DB.geom + " && st_makeenvelope("
						+ extent + ", 3857)";
			}

			if (small_roads_threshold > 0)
			{
				whereClause += " and ((ufi in (select distinct ufi from large_roads_mornington)) ";
				// the maximum count value for small roads is 17, so if it is more than 17, it means ignore small roads completely
				if (small_roads_threshold < 18)
					whereClause += "OR (ufi in (select distinct unnest(ufis) as ufi from combined_small_roads_mornington_sub where count >= "
							+ small_roads_threshold + "))";
				whereClause += ")";
			}

			DB.getPointsWithinBBOXFromDB(points, extent, whereClause);

			int minpts, mincls;

			if (!jo.has("minpts") || jo.get("minpts").toString().isEmpty())
				minpts = (int) Math.log(points.size());
			else
				minpts = jo.getInt("minpts");

			DistanceCalculator distanceMetric = new EuclideanDistance();
			if (!jo.has("mincls") || jo.get("mincls").toString().isEmpty())
			{
				mincls = minpts;
				if (useMode)
				{
					double kneeNNDistance = Others
							.findKnee(MyHDBSCANStar.calculateCoreDistances(points,
									minpts, distanceMetric));
					Integer[] numNeighbors = DB.getNumOfNeighbors(whereClause,
							kneeNNDistance);
					Arrays.sort(numNeighbors);
					int mode = Others.mode(numNeighbors);
					mincls = mode;
				}
			}
			else
				mincls = jo.getInt("mincls");

			double thresholdDistance = pixel * resolution;
			List<Cluster<MyPoint>> cluster = MyHDBSCANStar.cluster(points,
					minpts, mincls, distanceMetric, thresholdDistance, useMode);
			int[] clusterRes = DB.updateDatabase(cluster);

			int numOfClass = 4;
			double[] rangeArray = DB.getRangeArray(numOfClass);

			int totalPoints = points.size();

			JSONObject result = new JSONObject();
			result.put("resolution", resolution);
			result.put("pixel", pixel);
			result.put("useMode", useMode);
			result.put("small_roads_threshold", small_roads_threshold);
			result.put("minpts", minpts);
			result.put("mincls", mincls);
			result.put("cluster", cluster.size());
			result.put("rangeArray", rangeArray);
			result.put("noise", totalPoints - clusterRes[1]);
			result.put("size", totalPoints);

			/**
			 * TODO Must uncomment the following lines before deploying to
			 * nectar
			 * 
			 */
			// File f = new File(getServletContext().getRealPath("/conf.json"));
			// f.createNewFile();
			// FileWriter file = new FileWriter(f);
			// try
			// {
			// file.write(result.toString());
			// result.put("path", f.getAbsolutePath());
			// }
			// catch(IOException e)
			// {
			// e.printStackTrace();
			// }
			// finally
			// {
			// file.flush();
			// file.close();
			// }
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
}
