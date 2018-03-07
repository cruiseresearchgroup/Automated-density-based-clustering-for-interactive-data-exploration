package thesis.util;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.ml.clustering.Cluster;

import thesis.algorithm.hdbscanstar.distance.DistanceCalculator;

public class Others
{
	public static int[] toArray(List<Integer> list)
	{
		int[] ret = new int[list.size()];
		int i = 0;
		for (Iterator<Integer> it = list.iterator(); it.hasNext(); ret[i++] = it
				.next())
			;
		return ret;
	}
	
	public static double[] toDoubleArray(List<Double> list)
	{
		double[] ret = new double[list.size()];
		int i = 0;
		for (Iterator<Double> it = list.iterator(); it.hasNext(); ret[i++] = it
				.next())
			;
		return ret;
	}

	public static long getTimeMillis()
	{
		Date d = new Date();
		return d.getTime();
	}

	public static double findKnee(Double[] doubles)
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

	/**
	 * Find the most frequent element in an array
	 * 
	 * @param n
	 * @return
	 */
	public static int mode(final Integer[] n)
	{
		int maxKey = 0;
		int maxCounts = 0;

		int[] counts = new int[n.length];

		for (int i = 0; i < n.length; i++)
		{
			counts[n[i]]++;
			if (maxCounts < counts[n[i]])
			{
				maxCounts = counts[n[i]];
				maxKey = n[i];
			}
		}
		return maxKey;
	}

	/**
	 * Calculates Davies-Bouldin's index Based on R (index.DB {clusterSim}) A
	 * lower value will mean that the clustering is better
	 * 
	 * @param cluster
	 * @return double
	 */
	public static double dbi(List<Cluster<MyPoint>> cluster,
			DistanceCalculator distanceFunction)
	{
		int k = cluster.size(); // number of clusters
		int ncol_x = 2; // number of dimensions: in this case, it's 2 (lat,long)

		double[][] centers = new double[k][ncol_x];

		int clusterId = 0;
		double[] S = new double[k];
		for (Cluster<MyPoint> c : cluster)
		{
			List<MyPoint> pointsInCluster = c.getPoints();
			int clusterSize = pointsInCluster.size();

			for (MyPoint p : pointsInCluster)
			{
				double[] dim = p.getPoint();

				// Total the members of clusters for each dimension (for mean
				// calculation)
				// e.g. total the longitude
				for (int i = 0; i < dim.length; i++)
				{
					centers[clusterId][i] += dim[i];
				}
			}

			for (int j = 0; j < ncol_x; j++)
			{
				// Calculate the mean of each dimension value per cluster
				centers[clusterId][j] = centers[clusterId][j] / clusterSize;
			}

			// For S
			if (clusterSize > 1)
			{
				double tempTotal = 0;
				for (MyPoint p : pointsInCluster)
				{
					tempTotal += p.calcS(centers[clusterId]);
				}
				S[clusterId] = Math.pow((tempTotal / clusterSize), 0.5);
			}
			else
				S[clusterId] = 0;

			clusterId++;
		}

		double[][] M = new double[k][k]; // distance matrix between centers
		double[][] R = new double[k][k];
		double[] r = new double[k];

		double rTotal = 0;
		for (int row = 0; row < k; row++)
		{
			for (int column = 0; column < k; column++)
			{
				if (row == column)
					M[row][column] = 0;
				else
					M[row][column] = distanceFunction.computeDistance(
							centers[row], centers[column]);

				if (M[row][column] != 0)
				{
					R[row][column] = (S[row] + S[column]) / M[row][column];

					// get the maximum
					if (column == 0 || R[row][column] > r[row])
						r[row] = R[row][column];
				}
				// System.out.printf("%10.8f ", M[row][column]);
			}
			rTotal += r[row];
			// System.out.print("\n");
		}
		double dbi = rTotal / k;
		return dbi;
	}
}
