> **If you use the resources (algorithm, code and dataset) presented in this repository, please cite our paper.**  
*The BibTeX entry is provided at the bottom of this page. 

# Automated density-based clustering of spatial urban data for interactive data exploration
This paper presents a method to automatically estimate parameters for density-based clustering based on data distribution. It also includes several techniques for visualizing the clusters over a map, useful for interactive data exploration. The proposed method enables parameter estimation to automatically adapt to multiple resolutions, allowing the clusters to be recomputed and visualized interactively at query time with the changes of zoom levels and panning of the map. We apply a voting scheme with existing cluster indices to rank the clustering results. The framework of multi-resolution density-based clustering and visualization is implemented and evaluated using a real-world road crash datasets.

This repository contains resources developed within the following paper:

	Rosalina, E., Salim, F. D., & Sellis, T. (2017). Automated Density-Based Clustering of Spatial Urban Data for Interactive Data Exploration. 
	In IEEE Conference on Computer Communications Workshops (INFOCOM WKSHPS), Atlanta, GA, pp. 295-300.

You can find the [paper](https://github.com/cruiseresearchgroup/Automated-density-based-clustering-for-interactive-data-exploration/blob/master/paper/Rosalina2017Automated.pdf) and [presentation](https://github.com/cruiseresearchgroup/Automated-density-based-clustering-for-interactive-data-exploration/blob/master/presentation/INFOCOMM-Erica2017.pdf) in this repository. 

Alternative link: http://ieeexplore.ieee.org/document/8116392/

## Contents of the repository
This repository contains resources used and described in the paper.

The repository is structured as follows:

- `code/`: The folder containing "RiskMap" web app project (maven). In particular, the code for automatic parameter estimation can be found in `code/RiskMap/src/thesis/servlet` folder. 
   * Automatic parameter estimation of DBSCAN can be found in `DBSCANServlet.java`. 
   * Automatic parameter estimation of HDBSCAN can be found in `HDBSCANServlet.java`.
- `paper/`: Formal description of the algorithm and evaluation result. 
- `presentation/`: PDF of paper presentation in INFOCOM Workshops.

## Citation
If you use the resources presented in this repository, please cite (using the following BibTeX entry):
```
@inproceedings{rosalina2017automated,
  title={Automated density-based clustering of spatial urban data for interactive data exploration}, 
  author={E. Rosalina and F. D. Salim and T. Sellis}, 
  booktitle={2017 IEEE Conference on Computer Communications Workshops (INFOCOM WKSHPS)}, 
  pages={295-300}, 
  keywords={data visualisation;parameter estimation;pattern clustering;automated density;cluster indices;data distribution;interactive data exploration;multiresolution density;parameter estimation;real-world road crash datasets;spatial urban data;voting scheme;Australia;Clustering algorithms;Conferences;Data visualization;Electronic mail;Roads;Smart cities}, 
  doi={10.1109/INFCOMW.2017.8116392}, 
  month={May}, 
  year={2017}
}
```

