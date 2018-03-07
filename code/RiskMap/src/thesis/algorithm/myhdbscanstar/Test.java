package thesis.algorithm.myhdbscanstar;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;

import com.github.rcaller.rStuff.RCaller;
import com.github.rcaller.rStuff.RCode;

import thesis.algorithm.hdbscanstar.distance.DistanceCalculator;
import thesis.algorithm.hdbscanstar.distance.EuclideanDistance;
import thesis.util.DB;
import thesis.util.IndexResult;
import thesis.util.MyPoint;
import thesis.util.Others;

public class Test
{
	/* Starts from default and keep zooming in */
	public final static String[] EXTENTS = {
			"16021418.360610595, -4682722.382093325, 16355906.796386527, -4513949.423639657",
			"16105040.469554579, -4640529.142479909, 16272284.687442543, -4556142.663253074",
			"16146851.524026569, -4619432.5226732, 16230473.632970553, -4577239.283059782",
			"16151361.308695398, -4606973.287062717, 16193172.36316739, -4585876.667256008",
			"16153883.730628809, -4602004.8802241795, 16174789.257864805, -4591456.570320826",
			"16154154.44510397, -4601441.157140572, 16164607.208721966, -4596167.002188895" };
	public final static double[] RESOLUTIONS = { 305.748113140705,
			152.8740565703525, 76.43702828517625, 38.21851414258813,
			19.109257071294063, 9.554628535647032 };

	public static final int PIXEL = 0;

	public static final double CRASH_RATE_THRESHOLD = 20;

	public static final String OUTPUT_PATH = "C:\\Users\\Erica\\Desktop\\HDBSCAN Experiments\\Test Results CSV\\";

	public static void main(String[] args)
	{
		// long start = Others.getTimeMillis();
		// DB.geom = "geom_3857";
		// runNormal(true); // use mode; nodes not snapped to lines
		// long end = Others.getTimeMillis();
		// System.out.println(end-start);

		// start = Others.getTimeMillis();
		// DB.geom = "new_geom_3857";
		// System.out.println("\n>>>>>>>>>>>>>>>>> MinCls = mode >>>>>>>>>>>>>>>>>\n");
		// run(true); // run with MinCls = mode
		// end = Others.getTimeMillis();
		// System.out.println(end-start);

		// System.out.println("\n>>>>>>>>>>>>>>>>> MinCls = MinPts >>>>>>>>>>>>>>>>>\n");
		// run(false); // run with MinCls = MinPts

		sensitivityAnalysis();
	}

	public static IndexResult getBestResult(List<IndexResult> results)
	{
		double[] bestIndicesValues = new double[IndexResult.getNumOfIndicesUsed()];
		int[] bestIndex = new int[IndexResult.getNumOfIndicesUsed()];

		bestIndicesValues[0] = results.get(0).getC_index();
		bestIndicesValues[1] = results.get(0).getCalinski_harabasz();
		bestIndicesValues[2] = results.get(0).getDavies_bouldin();
		bestIndicesValues[3] = results.get(0).getDunn();
		bestIndicesValues[4] = results.get(0).getSilhouette();
		bestIndicesValues[5] = results.get(0).getXie_beni();

		for (int i = 1; i < results.size(); i++)
		{
			if (results.get(i).getC_index() < bestIndicesValues[0])
			{
				bestIndicesValues[0] = results.get(i).getC_index();
				bestIndex[0] = i;
			}

			if (results.get(i).getCalinski_harabasz() > bestIndicesValues[1])
			{
				bestIndicesValues[1] = results.get(i).getCalinski_harabasz();
				bestIndex[1] = i;
			}

			if (results.get(i).getDavies_bouldin() < bestIndicesValues[2])
			{
				bestIndicesValues[2] = results.get(i).getDavies_bouldin();
				bestIndex[2] = i;
			}

			if (results.get(i).getDunn() > bestIndicesValues[3])
			{
				bestIndicesValues[3] = results.get(i).getDunn();
				bestIndex[3] = i;
			}

			if (results.get(i).getSilhouette() > bestIndicesValues[4])
			{
				bestIndicesValues[4] = results.get(i).getSilhouette();
				bestIndex[4] = i;
			}

			if (results.get(i).getXie_beni() < bestIndicesValues[5])
			{
				bestIndicesValues[5] = results.get(i).getXie_beni();
				bestIndex[5] = i;
			}
		}
		
		for (int j = 0; j < IndexResult.getNumOfIndicesUsed(); j++)
		{
			System.out.println(IndexResult.indexNames[j] + " : " + bestIndicesValues[j] + " ("
					+ bestIndex[j] + ")");
		}
		
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();
		for (int i : bestIndex)
		{
			Integer count = map.get(i);
			map.put(i, count!=null?count+1:0);
		}
		
		Integer popular = Collections.max(map.entrySet(),
				new Comparator<Map.Entry<Integer, Integer>>()
				{
					@Override
					public int compare(Entry<Integer, Integer> o1,
							Entry<Integer, Integer> o2)
					{
						return o1.getValue().compareTo(o2.getValue());
					}
				}).getKey();
		
		System.out.println(popular);
		
		return new IndexResult(bestIndicesValues);
	}

	public static void sensitivityAnalysis()
	{
		DistanceCalculator distanceMetric = new EuclideanDistance();
		String highRes = "16080172.578498561, -4642985.902866491, 16288032.578498561, -4538105.902866491"; // default
		String lowRes = "16154958.14204257, -4598938.789474782, 16175863.669278566, -4588390.479571428"; // 2 1 1 1 1 1
		String lowRes19 = "16155017.905660568, -4598908.634523105, 16175803.905660568, -4588420.634523105"; // 2 2 1 1 1 1
		String lowRes19bi = "16154143.9056605, -4601302.6345231, 16174929.9056605, -4590814.6345231";
		String lowRes19c = "16154352.905660568, -4599174.634523105, 16175138.905660568, -4588686.634523105"; // 2 1 1 1 1 1
		String extent = highRes;

		try
		{
			// MinCls = MinPts
			List<IndexResult> overallResults = new ArrayList<IndexResult>();
			int i = 0;
			
			DB.geom = "geom_3857";
			String whereClause = "where " + DB.geom + " && st_makeenvelope("
					+ extent + ", 3857)";
			List<MyPoint> points = new ArrayList<MyPoint>();
			DB.getPointsWithinBBOXFromDB(points, extent, whereClause);
			
			System.out.printf(
					"No  MinPts  Eps/MinCls  %10s %20s %15s %10s %12s %10s\n",
					"c_index", "calinski_harabasz", "davies_bouldin", "dunn",
					"silhouette", "xie_beni");

			List<IndexResult> dbscanResults = new ArrayList<IndexResult>();
			
			// DBSCAN
			for (int minpts = ((int) Math.log(points.size())); minpts < 16; minpts++)
			{
				double eps = Others.findKnee(DB.getSortedKDistance(extent,
						whereClause, minpts));

				DBSCANClusterer<MyPoint> dbscan = new DBSCANClusterer<MyPoint>(
						eps, minpts);
				List<Cluster<MyPoint>> cluster = dbscan.cluster(points);
				String filename = "result";
				writeResultsToCsv(cluster, filename);
				IndexResult ir = runR(filename);
				dbscanResults.add(ir);
				System.out.printf("%-2d  %6d  %10f  ", i++, minpts, eps);
				ir.printIndices();
			}
			
			overallResults.add(getBestResult(dbscanResults));
			
			List<IndexResult> hdbscanNormResults = new ArrayList<IndexResult>();
			i = 0;
			
			// HDBSCAN - MinCls = MinPts
			for (int minpts = ((int) Math.log(points.size())); minpts < 16; minpts++)
			{
				int mincls = minpts;
				List<Cluster<MyPoint>> cluster = MyHDBSCANStar.cluster(points,
						minpts, mincls, distanceMetric, 0, false);
				String filename = "result";
				writeResultsToCsv(cluster, filename);
				IndexResult ir = runR(filename);
				hdbscanNormResults.add(ir);
				System.out.printf("%-2d  %6d  %10d  ", i++, minpts, mincls);
				ir.printIndices();
			}
			
			overallResults.add(getBestResult(hdbscanNormResults));
			
			List<IndexResult> hdbscanModeResults = new ArrayList<IndexResult>();
			i = 0;

			// MinCls = mode
			for (int minpts = ((int) Math.log(points.size())); minpts < 16; minpts++)
			{
				int mincls = minpts;

				double kneeNNDistance = Others
						.findKnee(MyHDBSCANStar.calculateCoreDistances(points,
								minpts, distanceMetric));
				Integer[] numNeighbors = DB.getNumOfNeighbors(whereClause,
						kneeNNDistance);
				Arrays.sort(numNeighbors);

				int mode = Others.mode(numNeighbors);
				if (mode != 0)
					mincls = mode;

				List<Cluster<MyPoint>> cluster = MyHDBSCANStar.cluster(points,
						minpts, mincls, distanceMetric, 0, false);
				String filename = "result";
				writeResultsToCsv(cluster, filename);
				IndexResult ir = runR(filename);
				hdbscanModeResults.add(ir);
				System.out.printf("%-2d  %6d  %10d  ", i++, minpts, mincls);
				ir.printIndices();
			}
			
			overallResults.add(getBestResult(hdbscanModeResults));
			
			System.out.println();
			getBestResult(overallResults);
		}
		catch(ClassNotFoundException e)
		{
			e.printStackTrace();
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}

	public static void runOldR(List<Cluster<MyPoint>> cluster)
	{
		List<double[]> matrixList = new ArrayList<double[]>();
		List<Integer> clusterMembership = new ArrayList<Integer>();

		int clusterId = 0;
		for (Cluster<MyPoint> c : cluster)
		{
			clusterId++;

			for (MyPoint p : c.getPoints())
			{
				matrixList.add(p.getPoint());
				clusterMembership.add(clusterId);
			}
		}

		double[][] matrix = matrixList
				.toArray(new double[matrixList.size()][2]);

		int[] clusters = Others.toArray(clusterMembership);

		try
		{
			RCaller r = new RCaller();
			r.setRscriptExecutable("C:\\Program Files\\R\\R-3.1.2\\bin\\x64\\Rscript.exe");
			RCode code = new RCode();
			code.addRCode("library(clusterCrit)");

			code.addDoubleMatrix("traj", matrix);
			code.addIntArray("part", clusters);
			code.addRCode("result <- intCriteria(traj,part,'all')");

			r.setRCode(code);
			r.runAndReturnResult("result");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}

	public static IndexResult runR(String filename)
	{
		try
		{
			RCaller r = new RCaller();
			r.setRscriptExecutable("C:\\Program Files\\R\\R-3.1.2\\bin\\x64\\Rscript.exe");
			RCode code = new RCode();
			code.addRCode("library(clusterCrit)");

			code.addRCode("traj <- read.csv('C:/Users/Erica/Desktop/HDBSCAN Experiments/Test Results CSV/"
					+ filename + ".csv')");
			code.addRCode("part <- scan('C:/Users/Erica/Desktop/HDBSCAN Experiments/Test Results CSV/"
					+ filename + "_cluster.csv')");
			code.addRCode("result <- intCriteria(data.matrix(traj),as.integer(part),c('c_index','calinski_harabasz','davies_bouldin', 'dunn', 'silhouette','xie_beni'))");
			r.setRCode(code);
			r.runAndReturnResult("result");

			return new IndexResult(
					r.getParser().getAsDoubleArray("c_index")[0], r.getParser()
							.getAsDoubleArray("calinski_harabasz")[0], r
							.getParser().getAsDoubleArray("davies_bouldin")[0],
					r.getParser().getAsDoubleArray("dunn")[0], r.getParser()
							.getAsDoubleArray("silhouette")[0], r.getParser()
							.getAsDoubleArray("xie_beni")[0]);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public static void runNormal(boolean useMode)
	{
		int n = 0;

		List<MyPoint> points = new ArrayList<MyPoint>();
		String extent = "";
		String whereClause = "";
		try
		{
			extent = EXTENTS[n];
			if (DB.geom.equals("geom") || DB.geom.equals("new_geom"))
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

			DB.getPointsWithinBBOXFromDB(points, extent, whereClause);

			int minpts = -1;
			int mincls = -1;

			// User does not specify MinPts
			if (minpts == -1)
				minpts = (int) Math.log(points.size());

			if (mincls == -1)
				mincls = minpts;

			DistanceCalculator distanceFunc = new EuclideanDistance();

			if (useMode)
			{
				double kneeNNDistance = Others.findKnee(MyHDBSCANStar
						.calculateCoreDistances(points, minpts, distanceFunc));
				Integer[] numNeighbors = DB.getNumOfNeighbors(whereClause,
						kneeNNDistance);
				Arrays.sort(numNeighbors);

				int mode = Others.mode(numNeighbors);
				if (mode != 0)
					mincls = mode;
			}

			double thresholdDistance = PIXEL * RESOLUTIONS[n];
			List<Cluster<MyPoint>> cluster = MyHDBSCANStar.cluster(points,
					minpts, mincls, distanceFunc, thresholdDistance, useMode);

			int[] clusterRes = DB.updateDatabase(cluster);

			int totalPoints = points.size();

			System.out.println("================ " + n + " ================");
			System.out.printf("%-20s : %f\n", "Resolution", RESOLUTIONS[n]);
			System.out.printf("%-20s : %d\n", "Pixel", PIXEL);
			System.out.printf("%-20s : %d\n", "MinPts", minpts);
			System.out.printf("%-20s : %d\n", "MinCls", mincls);
			System.out.printf("%-20s : %d\n", "Cluster", cluster.size());
			System.out.printf("%-20s : %d\n", "MaxCluster", clusterRes[0]);
			System.out.printf("%-20s : %d\n", "Noise",
					(totalPoints - clusterRes[1]));
			System.out.printf("%-20s : %d\n", "Size", totalPoints);
			System.out.println();
		}
		catch(ClassNotFoundException e)
		{
			e.printStackTrace();
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

	}

	public static void run(boolean useMode)
	{
		for (int n = 0; n < EXTENTS.length; n++)
		{
			List<MyPoint> points = new ArrayList<MyPoint>();
			String extent = "";
			String whereClause = "";
			try
			{
				extent = EXTENTS[n];

				if (DB.geom.equals("geom") || DB.geom.equals("new_geom"))
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

				whereClause += " and ((ufi in (select distinct ufi from large_roads_mornington)) ";
				whereClause += ")";

				DB.getPointsWithinBBOXFromDB(points, extent, whereClause);

				int minpts = -1;
				int mincls = -1;

				// User does not specify MinPts
				if (minpts == -1)
					minpts = (int) Math.log(points.size());

				if (mincls == -1)
					mincls = minpts;

				DistanceCalculator distanceFunc = new EuclideanDistance();

				if (useMode)
				{
					double kneeNNDistance = Others.findKnee(MyHDBSCANStar
							.calculateCoreDistances(points, minpts,
									distanceFunc));
					Integer[] numNeighbors = DB.getNumOfNeighbors(whereClause,
							kneeNNDistance);
					Arrays.sort(numNeighbors);

					int mode = Others.mode(numNeighbors);
					if (mode != 0)
						mincls = mode;
				}

				double thresholdDistance = PIXEL * RESOLUTIONS[n];
				List<Cluster<MyPoint>> cluster = MyHDBSCANStar.cluster(points,
						minpts, mincls, distanceFunc, thresholdDistance,
						useMode);
				String tableNameExtension = String.valueOf(n);

				if (useMode)
					tableNameExtension += "_mode";

				int[] clusterRes = DB.updateCertainDatabase(cluster,
						tableNameExtension);

				writeResultsToCsv(cluster, tableNameExtension + "_geom");

				int totalPoints = points.size();

				System.out.println("================ " + n
						+ " ================");
				System.out.printf("%-20s : %f\n", "Resolution", RESOLUTIONS[n]);
				System.out.printf("%-20s : %d\n", "Pixel", PIXEL);
				System.out.printf("%-20s : %d\n", "MinPts", minpts);
				System.out.printf("%-20s : %d\n", "MinCls", mincls);
				System.out.printf("%-20s : %d\n", "Cluster", cluster.size());
				System.out.printf("%-20s : %d\n", "MaxCluster", clusterRes[0]);
				System.out.printf("%-20s : %d\n", "Noise",
						(totalPoints - clusterRes[1]));
				System.out.printf("%-20s : %d\n", "Size", totalPoints);
				System.out.println();
			}
			catch(ClassNotFoundException e)
			{
				e.printStackTrace();
			}
			catch(SQLException e)
			{
				e.printStackTrace();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public static void writeResultsToCsv(List<Cluster<MyPoint>> cluster,
			String filename)
	{
		FileWriter writer = null;
		FileWriter clusterWriter = null;
		try
		{
			writer = new FileWriter(OUTPUT_PATH + filename + ".csv");
			clusterWriter = new FileWriter(OUTPUT_PATH + filename
					+ "_cluster.csv");

			writer.append("x");
			writer.append(',');
			writer.append("y");
			writer.append('\n');

			int clusterId = 0;

			for (Cluster<MyPoint> c : cluster)
			{
				clusterId++;

				for (MyPoint p : c.getPoints())
				{
					writer.append(String.valueOf(p.getPoint()[0]));
					writer.append(',');
					writer.append(String.valueOf(p.getPoint()[1]));
					writer.append('\n');

					clusterWriter.append(String.valueOf(clusterId));
					clusterWriter.append('\n');
				}
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (writer != null)
			{
				try
				{
					writer.flush();
					writer.close();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}

			if (clusterWriter != null)
			{
				try
				{
					clusterWriter.flush();
					clusterWriter.close();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}
