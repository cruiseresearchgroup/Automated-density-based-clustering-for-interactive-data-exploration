package thesis.algorithm.myhdbscanstar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.math3.ml.clustering.Cluster;

import thesis.algorithm.hdbscanstar.UndirectedGraph;
import thesis.algorithm.hdbscanstar.distance.DistanceCalculator;
import thesis.util.MyPoint;

public class MyHDBSCANStar
{
	private static List<int[]> hierarchy = new ArrayList<int[]>();
	
	public static List<Cluster<MyPoint>> cluster(List<MyPoint> dataSet,
			int minpts, int mincls, DistanceCalculator distanceFunction, double thresholdDistance, boolean useMode)
	{
		// To make sure only called once, to save time
		if(!useMode)
			calculateCoreDistances(dataSet, minpts, distanceFunction);
		UndirectedGraph mst = constructMST(dataSet, true, distanceFunction);
		mst.quicksortByEdgeWeight();
		
		// Draw the mst graph
//		JGraphAdapter.call(mst, thresholdDistance);
		
		int numPoints = dataSet.size();
		double[] pointNoiseLevels = new double[numPoints];
		int[] pointLastClusters = new int[numPoints];

		// Compute hierarchy and cluster tree:
		ArrayList<MyHDBSCANCluster> clusters = null;
		try
		{
			clusters = computeHierarchyAndClusterTree(mst, mincls, true,
					pointNoiseLevels, pointLastClusters, thresholdDistance);
		}
		catch(IOException ioe)
		{
			System.err
					.println("Error writing to hierarchy file or cluster tree file.");
			System.exit(-1);
		}

		// Remove references to unneeded objects:
		mst = null;

		// Propagate clusters:
		boolean infiniteStability = propagateTree(clusters);

		// Compute final flat partitioning:
		try
		{
			int[] flatPartitioning = findProminentClusters(clusters, numPoints,
					infiniteStability);
			return extractCluster(dataSet, flatPartitioning);
		}
		catch(IOException ioe)
		{
			System.err.println("Error writing to partitioning file.");
			System.exit(-1);
		}

		return null;

	}

	/**
	 * Calculates the core distances for each point in the data set, given some
	 * value for k.
	 * 
	 * @param dataSet
	 * @param k
	 *            Each point's core distance will be it's distance to the kth
	 *            nearest neighbor
	 * @param distanceFunction
	 *            A DistanceCalculator to compute distances between points
	 */
	public static Double[] calculateCoreDistances(List<MyPoint> dataSet, int k,
			DistanceCalculator distanceFunction)
	{
		Double[] nnDistances = new Double[dataSet.size()];
		int numNeighbors = k - 1;

		if (k == 1)
		{
			for (int point = 0; point < dataSet.size(); point++)
			{
				nnDistances[point] = 0.0;
			}
			return nnDistances;
		}

		for (int point = 0; point < dataSet.size(); point++)
		{
			double[] kNNDistances = new double[numNeighbors];

			for (int i = 0; i < numNeighbors; i++)
			{
				kNNDistances[i] = Double.MAX_VALUE;
			}

			for (int neighbor = 0; neighbor < dataSet.size(); neighbor++)
			{
				if (point == neighbor)
					continue;
				double distance = distanceFunction.computeDistance(
						dataSet.get(point).getPoint(), dataSet.get(neighbor)
								.getPoint());

				// Check at which position in the nearest distances the current
				// distance would fit:
				int neighborIndex = numNeighbors;
				while (neighborIndex >= 1
						&& distance < kNNDistances[neighborIndex - 1])
				{
					neighborIndex--;
				}

				// Shift elements in the array to make room for the current
				// distance:
				if (neighborIndex < numNeighbors)
				{
					for (int shiftIndex = numNeighbors - 1; shiftIndex > neighborIndex; shiftIndex--)
					{
						kNNDistances[shiftIndex] = kNNDistances[shiftIndex - 1];
					}
					kNNDistances[neighborIndex] = distance;
				}
			}
//			nnDistances[point] = kNNDistances[0]; // the real nearest neighbour distance
			nnDistances[point] = kNNDistances[numNeighbors - 1]; // k-nearest neighbour distance, with k=minpts
			dataSet.get(point).setCoreDistance(kNNDistances[numNeighbors - 1]);
		}
		Arrays.sort(nnDistances);
		return nnDistances;
	}

	/**
	 * Constructs the minimum spanning tree of mutual reachability distances for
	 * the data set, given the core distances for each point.
	 * 
	 * @param dataSet
	 *            A double[][] where index [i][j] indicates the jth attribute of
	 *            data point i
	 * @param coreDistances
	 *            An array of core distances for each data point
	 * @param selfEdges
	 *            If each point should have an edge to itself with weight equal
	 *            to core distance
	 * @param distanceFunction
	 *            A DistanceCalculator to compute distances between points
	 * @return An MST for the data set using the mutual reachability distances
	 */
	public static UndirectedGraph constructMST(List<MyPoint> dataSet,
			boolean selfEdges, DistanceCalculator distanceFunction)
	{

		int selfEdgeCapacity = 0;
		if (selfEdges)
			selfEdgeCapacity = dataSet.size();

		// One bit is set (true) for each attached point, or unset (false) for
		// unattached points:
		BitSet attachedPoints = new BitSet(dataSet.size());

		// Each point has a current neighbor point in the tree, and a current
		// nearest distance:
		int[] nearestMRDNeighbors = new int[dataSet.size() - 1
				+ selfEdgeCapacity];
		double[] nearestMRDDistances = new double[dataSet.size() - 1
				+ selfEdgeCapacity];

		for (int i = 0; i < dataSet.size() - 1; i++)
		{
			nearestMRDDistances[i] = Double.MAX_VALUE;
		}

		// The MST is expanded starting with the last point in the data set:
		int currentPoint = dataSet.size() - 1;
		int numAttachedPoints = 1;
		attachedPoints.set(dataSet.size() - 1);

		// Continue attaching points to the MST until all points are attached:
		while (numAttachedPoints < dataSet.size())
		{
			int nearestMRDPoint = -1;
			double nearestMRDDistance = Double.MAX_VALUE;

			// Iterate through all unattached points, updating distances using
			// the current point:
			for (int neighbor = 0; neighbor < dataSet.size(); neighbor++)
			{
				if (currentPoint == neighbor)
					continue;
				if (attachedPoints.get(neighbor) == true)
					continue;

				double distance = distanceFunction.computeDistance(
						dataSet.get(currentPoint).getPoint(),
						dataSet.get(neighbor).getPoint());

				double mutualReachabiltiyDistance = distance;
				if (dataSet.get(currentPoint).getCoreDistance() > mutualReachabiltiyDistance)
					mutualReachabiltiyDistance = dataSet.get(currentPoint)
							.getCoreDistance();
				if (dataSet.get(neighbor).getCoreDistance() > mutualReachabiltiyDistance)
					mutualReachabiltiyDistance = dataSet.get(neighbor)
							.getCoreDistance();

				if (mutualReachabiltiyDistance < nearestMRDDistances[neighbor])
				{
					nearestMRDDistances[neighbor] = mutualReachabiltiyDistance;
					nearestMRDNeighbors[neighbor] = currentPoint;
				}

				// Check if the unattached point being updated is the closest to
				// the tree:
				if (nearestMRDDistances[neighbor] <= nearestMRDDistance)
				{
					nearestMRDDistance = nearestMRDDistances[neighbor];
					nearestMRDPoint = neighbor;
				}
			}

			// Attach the closest point found in this iteration to the tree:
			attachedPoints.set(nearestMRDPoint);
			numAttachedPoints++;
			currentPoint = nearestMRDPoint;
		}

		// Create an array for vertices in the tree that each point attached to:
		int[] otherVertexIndices = new int[dataSet.size() - 1
				+ selfEdgeCapacity];
		for (int i = 0; i < dataSet.size() - 1; i++)
		{
			otherVertexIndices[i] = i;
		}

		// If necessary, attach self edges:
		if (selfEdges)
		{
			for (int i = dataSet.size() - 1; i < dataSet.size() * 2 - 1; i++)
			{
				int vertex = i - (dataSet.size() - 1);
				nearestMRDNeighbors[i] = vertex;
				otherVertexIndices[i] = vertex;
				nearestMRDDistances[i] = dataSet.get(vertex).getCoreDistance();
			}
		}
		
//		for(int i = 0 ; i < nearestMRDDistances.length ; i++)
//		{
//			System.out.println(nearestMRDDistances[i]);
//		}

		return new UndirectedGraph(dataSet.size(), nearestMRDNeighbors,
				otherVertexIndices, nearestMRDDistances);
	}

	/**
	 * Computes the hierarchy and cluster tree from the minimum spanning tree,
	 * writing both to file, and returns the cluster tree. Additionally, the
	 * level at which each point becomes noise is computed. Note that the
	 * minimum spanning tree may also have self edges (meaning it is not a true
	 * MST).
	 * 
	 * @param mst
	 *            A minimum spanning tree which has been sorted by edge weight
	 *            in descending order
	 * @param minClusterSize
	 *            The minimum number of points which a cluster needs to be a
	 *            valid cluster
	 * @param compactHierarchy
	 *            Indicates if hierarchy should include all levels or only
	 *            levels at which clusters first appear
	 * @param constraints
	 *            An optional ArrayList of Constraints to calculate cluster
	 *            constraint satisfaction
	 * @param pointNoiseLevels
	 *            A double[] to be filled with the levels at which each point
	 *            becomes noise
	 * @param pointLastClusters
	 *            An int[] to be filled with the last label each point had
	 *            before becoming noise
	 * @return The cluster tree
	 * @throws IOException
	 *             If any errors occur opening or writing to the files
	 */
	public static ArrayList<MyHDBSCANCluster> computeHierarchyAndClusterTree(
			UndirectedGraph mst, int minClusterSize, boolean compactHierarchy,
			double[] pointNoiseLevels, int[] pointLastClusters, double thresholdDistance)
			throws IOException
	{
		// The current edge being removed from the MST:
		int currentEdgeIndex = mst.getNumEdges() - 1;

		int nextClusterLabel = 2;
		boolean nextLevelSignificant = true;

		// The previous and current cluster numbers of each point in the data
		// set:
		int[] previousClusterLabels = new int[mst.getNumVertices()];
		int[] currentClusterLabels = new int[mst.getNumVertices()];
		for (int i = 0; i < currentClusterLabels.length; i++)
		{
			currentClusterLabels[i] = 1;
			previousClusterLabels[i] = 1;
		}

		// A list of clusters in the cluster tree, with the 0th cluster (noise)
		// null:
		ArrayList<MyHDBSCANCluster> clusters = new ArrayList<MyHDBSCANCluster>();
		clusters.add(null);
		clusters.add(new MyHDBSCANCluster(1, null, Double.NaN, mst
				.getNumVertices()));

		// Calculate number of constraints satisfied for cluster 1:
		TreeSet<Integer> clusterOne = new TreeSet<Integer>();
		clusterOne.add(1);

		// Sets for the clusters and vertices that are affected by the edge(s)
		// being removed:
		TreeSet<Integer> affectedClusterLabels = new TreeSet<Integer>();
		TreeSet<Integer> affectedVertices = new TreeSet<Integer>();

		while (currentEdgeIndex >= 0)
		{
			double currentEdgeWeight = mst
					.getEdgeWeightAtIndex(currentEdgeIndex);
			
			/*TODO Testing only! REMOVE IF NOT WORKING */
			// set a threshold distance, when this threshold is met, no need to find smaller clusters
			if (currentEdgeWeight <= thresholdDistance)
				break;
			
			ArrayList<MyHDBSCANCluster> newClusters = new ArrayList<MyHDBSCANCluster>();

			// Remove all edges tied with the current edge weight, and store
			// relevant clusters and vertices:
			while (currentEdgeIndex >= 0
					&& mst.getEdgeWeightAtIndex(currentEdgeIndex) == currentEdgeWeight)
			{
				int firstVertex = mst.getFirstVertexAtIndex(currentEdgeIndex);
				int secondVertex = mst.getSecondVertexAtIndex(currentEdgeIndex);
				mst.getEdgeListForVertex(firstVertex).remove(
						(Integer) secondVertex);
				mst.getEdgeListForVertex(secondVertex).remove(
						(Integer) firstVertex);

				if (currentClusterLabels[firstVertex] == 0)
				{
					currentEdgeIndex--;
					continue;
				}

				affectedVertices.add(firstVertex);
				affectedVertices.add(secondVertex);
				affectedClusterLabels.add(currentClusterLabels[firstVertex]);
				currentEdgeIndex--;
			}

			if (affectedClusterLabels.isEmpty())
				continue;

			// Check each cluster affected for a possible split:
			while (!affectedClusterLabels.isEmpty())
			{
				int examinedClusterLabel = affectedClusterLabels.last();
				affectedClusterLabels.remove(examinedClusterLabel);
				TreeSet<Integer> examinedVertices = new TreeSet<Integer>();

				// Get all affected vertices that are members of the cluster
				// currently being examined:
				Iterator<Integer> vertexIterator = affectedVertices.iterator();
				while (vertexIterator.hasNext())
				{
					int vertex = vertexIterator.next();

					if (currentClusterLabels[vertex] == examinedClusterLabel)
					{
						examinedVertices.add(vertex);
						vertexIterator.remove();
					}
				}

				TreeSet<Integer> firstChildCluster = null;
				LinkedList<Integer> unexploredFirstChildClusterPoints = null;
				int numChildClusters = 0;

				/*
				 * Check if the cluster has split or shrunk by exploring the
				 * graph from each affected vertex. If there are two or more
				 * valid child clusters (each has >= minClusterSize points), the
				 * cluster has split. Note that firstChildCluster will only be
				 * fully explored if there is a cluster split, otherwise, only
				 * spurious components are fully explored, in order to label
				 * them noise.
				 */
				while (!examinedVertices.isEmpty())
				{
					TreeSet<Integer> constructingSubCluster = new TreeSet<Integer>();
					LinkedList<Integer> unexploredSubClusterPoints = new LinkedList<Integer>();
					boolean anyEdges = false;
					boolean incrementedChildCount = false;

					int rootVertex = examinedVertices.last();
					constructingSubCluster.add(rootVertex);
					unexploredSubClusterPoints.add(rootVertex);
					examinedVertices.remove(rootVertex);

					// Explore this potential child cluster as long as there are
					// unexplored points:
					while (!unexploredSubClusterPoints.isEmpty())
					{
						int vertexToExplore = unexploredSubClusterPoints.poll();

						for (int neighbor : mst
								.getEdgeListForVertex(vertexToExplore))
						{
							anyEdges = true;
							if (constructingSubCluster.add(neighbor))
							{
								unexploredSubClusterPoints.add(neighbor);
								examinedVertices.remove(neighbor);
							}
						}

						// Check if this potential child cluster is a valid
						// cluster:
						if (!incrementedChildCount
								&& constructingSubCluster.size() >= minClusterSize
								&& anyEdges)
						{
							incrementedChildCount = true;
							numChildClusters++;

							// If this is the first valid child cluster, stop
							// exploring it:
							if (firstChildCluster == null)
							{
								firstChildCluster = constructingSubCluster;
								unexploredFirstChildClusterPoints = unexploredSubClusterPoints;
								break;
							}
						}
					}

					// If there could be a split, and this child cluster is
					// valid:
					if (numChildClusters >= 2
							&& constructingSubCluster.size() >= minClusterSize
							&& anyEdges)
					{

						// Check this child cluster is not equal to the
						// unexplored first child cluster:
						int firstChildClusterMember = firstChildCluster.last();
						if (constructingSubCluster
								.contains(firstChildClusterMember))
							numChildClusters--;

						// Otherwise, create a new cluster:
						else
						{
							MyHDBSCANCluster newCluster = createNewCluster(
									constructingSubCluster,
									currentClusterLabels,
									clusters.get(examinedClusterLabel),
									nextClusterLabel, currentEdgeWeight);
							newClusters.add(newCluster);
							clusters.add(newCluster);
							nextClusterLabel++;
						}
					}

					// If this child cluster is not valid cluster, assign it to
					// noise:
					else if (constructingSubCluster.size() < minClusterSize
							|| !anyEdges)
					{
						createNewCluster(constructingSubCluster,
								currentClusterLabels,
								clusters.get(examinedClusterLabel), 0,
								currentEdgeWeight);

						for (int point : constructingSubCluster)
						{
							pointNoiseLevels[point] = currentEdgeWeight;
							pointLastClusters[point] = examinedClusterLabel;
						}
					}
				}

				// Finish exploring and cluster the first child cluster if there
				// was a split and it was not already clustered:
				if (numChildClusters >= 2
						&& currentClusterLabels[firstChildCluster.first()] == examinedClusterLabel)
				{

					while (!unexploredFirstChildClusterPoints.isEmpty())
					{
						int vertexToExplore = unexploredFirstChildClusterPoints
								.poll();

						for (int neighbor : mst
								.getEdgeListForVertex(vertexToExplore))
						{
							if (firstChildCluster.add(neighbor))
								unexploredFirstChildClusterPoints.add(neighbor);
						}
					}

					MyHDBSCANCluster newCluster = createNewCluster(
							firstChildCluster, currentClusterLabels,
							clusters.get(examinedClusterLabel),
							nextClusterLabel, currentEdgeWeight);
					newClusters.add(newCluster);
					clusters.add(newCluster);
					nextClusterLabel++;
				}
			}

			boolean writeHierarchy = false;

			// Write out the current level of the hierarchy:
			if (!compactHierarchy || nextLevelSignificant
					|| !newClusters.isEmpty())
			{
				hierarchy.add(previousClusterLabels.clone());
				writeHierarchy = true;
			}

			// Assign file offsets and calculate the number of constraints
			// satisfied:
			TreeSet<Integer> newClusterLabels = new TreeSet<Integer>();
			for (MyHDBSCANCluster newCluster : newClusters)
			{
				if (writeHierarchy)
				{
					newCluster.setHierarchyIndex(hierarchy.size());
				}

				newClusterLabels.add(newCluster.getLabel());
			}

			// Replace the old labels with the new one
			for (int i = 0; i < previousClusterLabels.length; i++)
			{
				previousClusterLabels[i] = currentClusterLabels[i];
			}

			if (newClusters.isEmpty())
				nextLevelSignificant = false;
			else
				nextLevelSignificant = true;
		}

		return clusters;
	}

	/**
	 * Propagates constraint satisfaction, stability, and lowest child death
	 * level from each child cluster to each parent cluster in the tree. This
	 * method must be called before calling findProminentClusters() or
	 * calculateOutlierScores().
	 * 
	 * @param clusters
	 *            A list of Clusters forming a cluster tree
	 * @return true if there are any clusters with infinite stability, false
	 *         otherwise
	 */
	public static boolean propagateTree(ArrayList<MyHDBSCANCluster> clusters)
	{
		TreeMap<Integer, MyHDBSCANCluster> clustersToExamine = new TreeMap<Integer, MyHDBSCANCluster>();
		BitSet addedToExaminationList = new BitSet(clusters.size());
		boolean infiniteStability = false;

		// Find all leaf clusters (clusters with no children; clusters that can't be split into smaller clusters) in the cluster tree:
		for (MyHDBSCANCluster cluster : clusters)
		{
			if (cluster != null && !cluster.hasChildren())
			{
				clustersToExamine.put(cluster.getLabel(), cluster);
				addedToExaminationList.set(cluster.getLabel());
			}
		}

		// Iterate through every cluster, propagating stability from children to
		// parents:
		while (!clustersToExamine.isEmpty())
		{
			MyHDBSCANCluster currentCluster = clustersToExamine.pollLastEntry()
					.getValue();
			currentCluster.propagate();

			if (currentCluster.getStability() == Double.POSITIVE_INFINITY)
				infiniteStability = true;

			if (currentCluster.getParent() != null)
			{
				MyHDBSCANCluster parent = currentCluster.getParent();

				if (!addedToExaminationList.get(parent.getLabel()))
				{
					clustersToExamine.put(parent.getLabel(), parent);
					addedToExaminationList.set(parent.getLabel());
				}
			}
		}

		return infiniteStability;
	}

	/**
	 * Produces a flat clustering result using constraint satisfaction and
	 * cluster stability, and returns an array of labels. propagateTree() must
	 * be called before calling this method.
	 * 
	 * @param clusters
	 *            A list of Clusters forming a cluster tree which has already
	 *            been propagated
	 * @param numPoints
	 *            The number of points in the original data set
	 * @param infiniteStability
	 *            true if there are any clusters with infinite stability, false
	 *            otherwise
	 * @return An array of labels for the flat clustering result
	 * @throws IOException
	 *             If any errors occur opening, reading, or writing to the files
	 * @throws NumberFormatException
	 *             If illegal number values are found in the hierarchyFile
	 */
	public static int[] findProminentClusters(
			ArrayList<MyHDBSCANCluster> clusters, int numPoints,
			boolean infiniteStability) throws IOException,
			NumberFormatException
	{
		// Take the list of propagated clusters from the root cluster:
		ArrayList<MyHDBSCANCluster> solution = clusters.get(1)
				.getPropagatedDescendants();

		int[] flatPartitioning = new int[numPoints];

		// Store all the file offsets at which to find the birth points for the
		// flat clustering:
		TreeMap<Integer, ArrayList<Integer>> significantHierarchyIndex = new TreeMap<Integer, ArrayList<Integer>>();
		for (MyHDBSCANCluster cluster : solution)
		{
			ArrayList<Integer> clusterList = significantHierarchyIndex
					.get(cluster.getHierarchyIndex());

			if (clusterList == null)
			{
				clusterList = new ArrayList<Integer>();
				significantHierarchyIndex.put(cluster.getHierarchyIndex(),
						clusterList);
			}

			clusterList.add(cluster.getLabel());
		}

		// Go through the hierarchy file, setting labels for the flat
		// clustering:
		while (!significantHierarchyIndex.isEmpty())
		{
			Map.Entry<Integer, ArrayList<Integer>> entry = significantHierarchyIndex
					.pollFirstEntry();
			ArrayList<Integer> clusterList = entry.getValue();
			int hierarchyIndex = entry.getKey();
			int[] contents = hierarchy.get(hierarchyIndex);

			for (int i = 0; i < contents.length; i++)
			{
				int label = contents[i];
				if (clusterList.contains(label))
					flatPartitioning[i] = label;
			}
		}

		return flatPartitioning;
	}

	public static List<Cluster<MyPoint>> extractCluster(List<MyPoint> dataSet,
			int[] flatPartitioning)
	{
		/*
		 * key: cluster label
		 * value: collection of points
		 */
		TreeMap<Integer, Cluster<MyPoint>> result = new TreeMap<Integer, Cluster<MyPoint>>();

		for (int i = 0; i < flatPartitioning.length; i++)
		{
			if (flatPartitioning[i] != 0)
			{
				Cluster<MyPoint> currentCluster = result
						.get(flatPartitioning[i]);

				if (currentCluster == null)
				{
					currentCluster = new Cluster<MyPoint>();
					result.put(flatPartitioning[i], currentCluster);
				}

				currentCluster.addPoint(dataSet.get(i));
			}
		}

		List<Cluster<MyPoint>> clusters = new ArrayList<Cluster<MyPoint>>();
		while (!result.isEmpty())
		{
			Map.Entry<Integer, Cluster<MyPoint>> entry = result
					.pollFirstEntry();
			Cluster<MyPoint> cluster = entry.getValue();
			clusters.add(cluster);
		}

		return clusters;
	}

	/**
	 * Removes the set of points from their parent Cluster, and creates a new
	 * Cluster, provided the clusterId is not 0 (noise).
	 * 
	 * @param points
	 *            The set of points to be in the new Cluster
	 * @param clusterLabels
	 *            An array of cluster labels, which will be modified
	 * @param parentCluster
	 *            The parent Cluster of the new Cluster being created
	 * @param clusterLabel
	 *            The label of the new Cluster
	 * @param edgeWeight
	 *            The edge weight at which to remove the points from their
	 *            previous Cluster
	 * @return The new Cluster, or null if the clusterId was 0
	 */
	private static MyHDBSCANCluster createNewCluster(TreeSet<Integer> points,
			int[] clusterLabels, MyHDBSCANCluster parentCluster,
			int clusterLabel, double edgeWeight)
	{
		for (int point : points)
		{
			clusterLabels[point] = clusterLabel;
		}
		parentCluster.detachPoints(points.size(), edgeWeight);

		if (clusterLabel != 0)
			return new MyHDBSCANCluster(clusterLabel, parentCluster,
					edgeWeight, points.size());

		else
		{
			parentCluster.addPointsToVirtualChildCluster(points);
			return null;
		}
	}
}