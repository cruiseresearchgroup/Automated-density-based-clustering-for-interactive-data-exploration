package thesis.util;

public class IndexResult
{
	public static final String[] indexNames = { "c_index", "calinski_harabasz",
			"davies_bouldin", "dunn", "silhouette", "xie_beni" };

	private double[] indexValues;
	private double c_index; // min
	private double calinski_harabasz; // max
	private double davies_bouldin; // min
	private double dunn; // max
	private double silhouette; // max
	private double xie_beni; // min

	public IndexResult(double c_index, double calinski_harabasz,
			double davies_bouldin, double dunn, double silhouette,
			double xie_beni)
	{
		this.c_index = c_index;
		this.calinski_harabasz = calinski_harabasz;
		this.davies_bouldin = davies_bouldin;
		this.dunn = dunn;
		this.silhouette = silhouette;
		this.xie_beni = xie_beni;
	}

	public IndexResult(double[] indices)
	{
		indexValues = indices;
		this.c_index = indices[0];
		this.calinski_harabasz = indices[1];
		this.davies_bouldin = indices[2];
		this.dunn = indices[3];
		this.silhouette = indices[4];
		this.xie_beni = indices[5];
	}
	
	public static int getNumOfIndicesUsed()
	{
		return indexNames.length;
	}

	public double getC_index()
	{
		return c_index;
	}

	public double getCalinski_harabasz()
	{
		return calinski_harabasz;
	}

	public double getDavies_bouldin()
	{
		return davies_bouldin;
	}

	public double getDunn()
	{
		return dunn;
	}

	public double getSilhouette()
	{
		return silhouette;
	}

	public double getXie_beni()
	{
		return xie_beni;
	}

	public void printIndices()
	{
		System.out.printf("%10f %20f %15f %10f %12f %10f\n", c_index,
				calinski_harabasz, davies_bouldin, dunn, silhouette, xie_beni);
	}
}
