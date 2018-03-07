package thesis.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.ml.clustering.Cluster;

import thesis.algorithm.hdbscanstar.distance.DistanceCalculator;

public class S_Dbw
{
	private List<Cluster<MyPoint>> clusters;
	private List<MyPoint> dataset;
	private DistanceCalculator distanceMetric;

	private int numberOfDimensions; // always 2 in our case (only x & y)
	private int numberOfInstancesInDataset; // total number of objects / points
											// in dataset (incl. noise)
	private int numberOfClusters;

	private double[] datasetVariance;

	// centroids of every clusters
	private double[][] representativeInstancesOfClusters;
	int[] densityOfPrototypes = new int[numberOfClusters];

	private double avgStdDevOfClusters = 0.0;

	private double Scat;
	private double Dens_bw;
	private double S_Dbw;

	public S_Dbw(List<Cluster<MyPoint>> clusters, List<MyPoint> dataset,
			DistanceCalculator distanceMetric)
	{
		this.clusters = clusters;
		this.dataset = dataset;
		this.distanceMetric = distanceMetric;

		numberOfDimensions = 2;
		numberOfInstancesInDataset = dataset.size();
		numberOfClusters = clusters.size();

		datasetVariance = variance(dataset);

		Scat = Scat();

		representativeInstancesOfClusters = calculateClustersCentroid();
		densityOfPrototypes = new int[numberOfClusters];

		Dens_bw = Dens_bw();
		S_Dbw = Scat + Dens_bw;
	}

	public double getScat()
	{
		return Scat;
	}

	public double getDens_bw()
	{
		return Dens_bw;
	}

	public double getS_Dbw()
	{
		return S_Dbw;
	}

	private double Scat()
	{
		double[] l2NormStdDevOfClusters = new double[numberOfClusters];
		double l2NormStdDevOfClustering = Math
				.sqrt((datasetVariance[0] * datasetVariance[0])
						+ (datasetVariance[1] * datasetVariance[1]));

		for (int i = 0; i < numberOfClusters; i++)
		{
			double[] clusterVariance = variance(clusters.get(i).getPoints());
			l2NormStdDevOfClusters[i] = Math
					.sqrt((clusterVariance[0] * clusterVariance[0])
							+ (clusterVariance[1] * clusterVariance[1]));
			avgStdDevOfClusters += l2NormStdDevOfClusters[i];
		}

		avgStdDevOfClusters = Math.sqrt(avgStdDevOfClusters) / numberOfClusters;

		return avgStdDevOfClusters / l2NormStdDevOfClustering;
	}

	private double Dens_bw()
	{
		double interClusterDensity = 0.0;

		int densityUij = 0;

		for (int j = 0; j < numberOfClusters; j++)
		{
			// TODO check whether this one use the points in a cluster only or
			// from whole dataset
			densityOfPrototypes[j] = computeDensity(
					clusters.get(j).getPoints(),
					representativeInstancesOfClusters[j]);
		}

		for (int i = 0; i < numberOfClusters; i++)
		{
			for (int j = 0; j < numberOfClusters; j++)
			{
				if (i != j)
				{
					// compute u_ij that is the middle point of the line segment
					// defined by the prototype instances of clusters i and j
					double uij_x = 0.5 * (representativeInstancesOfClusters[i][0] + representativeInstancesOfClusters[j][0]);
					double uij_y = 0.5 * (representativeInstancesOfClusters[i][1] + representativeInstancesOfClusters[j][1]);
					double[] uij = new double[] { uij_x, uij_y };
					List<MyPoint> pointsInClusterIandJ = new ArrayList<MyPoint>(
							clusters.get(i).getPoints());
					pointsInClusterIandJ.addAll(clusters.get(j).getPoints());
					densityUij = computeDensity(pointsInClusterIandJ, uij);

					double maxDensity = Math.max(densityOfPrototypes[i],
							densityOfPrototypes[j]);
					if (maxDensity != 0)
						interClusterDensity += (double) densityUij / maxDensity;
				}
			}
		}

		return interClusterDensity
				/ (numberOfClusters * (numberOfClusters - 1));
	}

	private int computeDensity(List<MyPoint> points, double[] u)
	{
		int density = 0;
		for (int j = 0; j < points.size(); j++)
		{
			double[] curr = points.get(j).getPoint();
			double dist = distanceMetric.computeDistance(curr, u);
			if (dist <= avgStdDevOfClusters)
			{
				density++;
			}
		}
		return density;
	}

	private double[][] calculateClustersCentroid()
	{
		double[][] centroids = new double[numberOfClusters][numberOfDimensions];
		for (int i = 0; i < numberOfClusters; i++)
		{
			List<MyPoint> clusterPoints = clusters.get(i).getPoints();
			centroids[i] = centroid(clusterPoints);
		}
		return centroids;
	}

	// find centroid of a cluster
	private double[] centroid(List<MyPoint> points)
	{
		double sum_x = 0, sum_y = 0;
		for (int i = 0; i < points.size(); i++)
		{
			MyPoint curr = points.get(i);
			double value_x = curr.getPoint()[0];
			double value_y = curr.getPoint()[1];
			sum_x += value_x;
			sum_y += value_y;
		}
		double x = sum_x / points.size();
		double y = sum_y / points.size();
		return new double[] { x, y };
	}

	private double[] variance(List<MyPoint> points)
	{
		double var_x = Double.POSITIVE_INFINITY, var_y = Double.POSITIVE_INFINITY;
		double sum_x = 0, sum_y = 0;
		double ss_x = 0, ss_y = 0; // sum of squared

		for (int i = 0; i < points.size(); i++)
		{
			MyPoint curr = points.get(i);
			double value_x = curr.getPoint()[0];
			double value_y = curr.getPoint()[1];
			sum_x += value_x;
			ss_x += value_x * value_x;

			sum_y += value_y;
			ss_y += value_y * value_y;
		}

		var_x = (ss_x - (sum_x * sum_x) / points.size()) / (points.size() - 1);
		var_y = (ss_y - (sum_y * sum_y) / points.size()) / (points.size() - 1);

		return new double[] { var_x, var_y };
	}
}
