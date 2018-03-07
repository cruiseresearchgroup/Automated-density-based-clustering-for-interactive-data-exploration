package thesis.util;

import java.util.Arrays;

import org.apache.commons.math3.ml.clustering.Clusterable;

public class MyPoint implements Clusterable
{
	private int id;
	private double[] point;
	private double coreDistance;

	public MyPoint(int id, double x, double y)
	{
		this.id = id;
		this.point = new double[2];
		point[0] = x;
		point[1] = y;
		coreDistance = 0;
	}
	
	public MyPoint(int id, double[] point)
	{
		this.id = id;
		this.point = point;
		coreDistance = 0;
	}

	public int getId()
	{
		return id;
	}

	@Override
	public double[] getPoint()
	{
		return point;
	}

	public double getCoreDistance()
	{
		return coreDistance;
	}

	public void setCoreDistance(double coreDistance)
	{
		this.coreDistance = coreDistance;
	}

	@Override
    public String toString() {
		String a = Arrays.toString(point); 
        return a.substring(1, a.length()-1);
    }
	
	/*
	 * matrix operation other must be the same dimension with point (member by
	 * member)
	 */
	public double calcS(double[] other)
	{
		double res = 0;
		for (int i = 0; i < point.length; i++)
		{
			res += Math.pow((point[i] - other[i]), 2);
		}
		return Math.pow(Math.sqrt(res), 2);
	}
}
