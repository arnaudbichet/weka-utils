/*
 *    WekaUtils.java
 *
 *    Some simple methods to make working with the Weka API a little easier
 *
 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Random;

import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.RBFKernel;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.Bagging;
import weka.classifiers.meta.CVParameterSelection;
import weka.classifiers.meta.LogitBoost;
import weka.classifiers.meta.ThresholdSelector;
import weka.classifiers.trees.BFTree;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.LMT;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.unsupervised.attribute.PrincipalComponents;


public class WekaUtils {

	/**
	 * Load instances from .arff file
	 * @param filename the name of the .arff file
	 * @return the set of instances
	 * @throws Exception
	 */
	public static Instances loadInstances(String filename) throws Exception {
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		Instances data = new Instances(reader);
		reader.close();
		data.setClassIndex(data.numAttributes() - 1);
		return data;
	}
	
	/**
	 * Use CfsSubsetEval to find a good subset of features using 
	 * correlation-based feature selection before classifying
	 * @param data the set of instances to be passed through the filter
	 * @return the dataset with only the selected features
	 * @throws Exception if the dataset is null
	 */
	public static Instances selectFeaturesCFS(Instances data) throws Exception {
		AttributeSelection filter = new AttributeSelection();
		CfsSubsetEval cfsEval = new CfsSubsetEval();
		BestFirst search = new BestFirst();
		search.setSearchTermination(50);

		filter.setEvaluator(cfsEval);
		filter.setSearch(search);
		filter.setInputFormat(data);
		return Filter.useFilter(data, filter);
	}
	
	/**
	 * Run PCA on the dataset, using cross-validation to decide the number of components
	 * Useful for quick dimension reduction
	 * @param data
	 * @return the principal components
	 * @throws Exception if data is null
	 */
	public static Instances PCA(Instances data) throws Exception{
		PrincipalComponents pca = new PrincipalComponents();
		pca.setInputFormat(data);
		return Filter.useFilter(data, pca);
	}
	
	/**
	 * Evaluate a model on a dataset with cross-validation
	 * @param classifer the classifier to be used
	 * @param data the set of instances
	 * @param folds the number of cross-validation folds
	 * @return the evaluation result
	 * @throws Exception
	 */
	public static Evaluation crossValidate(Classifier classifer, Instances data, int folds) throws Exception {
		Evaluation eval = new Evaluation(data);
		eval.crossValidateModel(classifer, data, folds, new Random(1)); 
		return eval;
	}

	/**
	 * Cross-validate a set of models on a dataset 
	 * @param classifiers the array of classifiers to use
	 * @param data the dataset
	 * @param folds the number of folds to use in cross-validation
	 * @return an Evaluation object with the results
	 * @throws Exception
	 */
	public static Evaluation[] evaluateClassifiers(Classifier[] classifiers, Instances data, int folds) throws Exception {
		Evaluation[] results = new Evaluation[classifiers.length];
		for(int i = 0; i < results.length; i++) {
			results[i] = crossValidate(classifiers[i], data, folds);
		}
		return results;
	}
	
	/**
	 * Find best result from list of cross-validation results based on f-measure
	 * @param results the Evalation objects to search
	 * @return the best Evaluation object
	 */
	public static int bestResult(Evaluation[] results) throws Exception {
		int best = 0;
		for(int i = 0; i < results.length; i++){
			if (results[i].weightedFMeasure() > results[best].weightedFMeasure())
				best = i;
		}
		return best;
	}
	
	/**
	 * Basic parameter tuning for some common classifiers
	 * @param classifiers the classifiers to optimize
	 * @param data the dataset
	 * @return and array of classifiers with better parameter values
	 * @throws Exception
	 */
	public static Classifier[] optimizeClassifiers(Classifier[] classifiers, Instances data) throws Exception{
		for(int i = 0; i < classifiers.length; i++){
			System.out.println("Optimizing: " + classifiers[i].getClass().toString());
			CVParameterSelection optimizedClassifier = new CVParameterSelection();
			optimizedClassifier.setClassifier(classifiers[i]);
			if (classifiers[i] instanceof AdaBoostM1) {
				optimizedClassifier.addCVParameter("P 50 250 5");
			}
			else if (classifiers[i] instanceof Bagging){
				optimizedClassifier.addCVParameter("P 50 100 3");
				optimizedClassifier.addCVParameter("I 10 50 5");
			}
			else if (classifiers[i] instanceof BFTree){
				optimizedClassifier.addCVParameter("M 2 10 9");
			}
			else if (classifiers[i] instanceof J48){
				optimizedClassifier.addCVParameter("C 0.1 0.5 5");
				optimizedClassifier.addCVParameter("M 1 20 1");
			}
			else if (classifiers[i] instanceof IBk){
				optimizedClassifier.addCVParameter("K 1 20 1");
			}
			else if (classifiers[i] instanceof LMT){
				optimizedClassifier.addCVParameter("M 5 35 7");
			}
			else if (classifiers[i] instanceof LogitBoost){
				optimizedClassifier.addCVParameter("I 10 50 5");
				optimizedClassifier.addCVParameter("H .1 1 10");
			}
			else if(classifiers[i] instanceof MultilayerPerceptron){
				optimizedClassifier.addCVParameter("L 0.1 0.5 5");
				optimizedClassifier.addCVParameter("M 0.1 0.5 5");
			}
			else if (classifiers[i] instanceof RandomForest) {
				optimizedClassifier.addCVParameter("I 5 20 4");
				optimizedClassifier.addCVParameter("K 0 " + Math.max(20, data.numAttributes()-1) + " 1");
			}
			else if (classifiers[i] instanceof SMO){
				optimizedClassifier.addCVParameter("C .1 10.1 10");
				if(((SMO)classifiers[i]).getKernel() instanceof RBFKernel){
					optimizedClassifier.addCVParameter("G .01 1.01 10");
				}
			}

			optimizedClassifier.buildClassifier(data); 
			classifiers[i].setOptions(optimizedClassifier.getClassifier().getOptions());
		}
		return classifiers;
	}
	
	/**
	 * Create a ThresholdSelector classifier given a threshold value
	 * @param classifier the base classifier
	 * @param data the set of instances
	 * @param threshold the probability threshold
	 * @return
	 * @throws Exception
	 */
	public static ThresholdSelector createThresholdSelector(Classifier classifier, Instances data, double threshold) throws Exception {
		ThresholdSelector ts = new ThresholdSelector();
		ts.setClassifier(classifier);
		ts.setManualThresholdValue(threshold);
		ts.buildClassifier(data);
		return ts;
	}
	
	/**
	 * Select the probability threshold that maximizes the F-measure when classifying
	 * @param classifier the base classifier
	 * @param data the set of instances
	 * @param folds the number of cross-validation folds to use when optimizing
	 * @return the best threshold classifer
	 * @throws Exception
	 */
	public static ThresholdSelector optimizeThreshold(Classifier classifier, Instances data, double[] probabilities,
			double alpha, double beta) throws Exception{
		ThresholdSelector bestClassifier = new ThresholdSelector();
		double bestResult = 0; 
		double resultValue; 
		//optimize based on result f-measure
		for(double probability : probabilities) {
			ThresholdSelector thresholdClassifier = createThresholdSelector(classifier, data, probability);
			Evaluation result = crossValidate(thresholdClassifier, data, 10);
			resultValue = result.weightedFMeasure();
			if (resultValue > bestResult) {
				bestClassifier = thresholdClassifier;
				bestResult = resultValue;
			}
		}
		return bestClassifier;
	}
	
	/**
	 * Remove a percentage of a dataset for easy testing
	 * @param data the set of instances
	 * @param percentage the percent of the data to remove
	 * @return the dataset without x% of its instances
	 */
	public static Instances removePercentage(Instances data, double percentage) throws Exception{
		RemovePercentage rp = new RemovePercentage();
		rp.setInputFormat(data);
		rp.setPercentage(percentage);
		return Filter.useFilter(data, rp);
	}
	
}
