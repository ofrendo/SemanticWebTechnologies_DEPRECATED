/**
 * 
 */
package QueryEngine;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.util.FileManager;

import NEREngine.CoreNLPEngine;
import NEREngine.NamedEntity;
import NEREngine.NamedEntity.EntityType;


/** 
 * @author Sascha Ulbrich
 *
 */
public class JenaEngine implements QueryEngine {
	private QueryProperties qp;
	private static Model model;
	private static OntModel ontoModel;
	private static List<String> inCache;
	private static final String PREFIX = ":";
	
	//######################### Public methods: Interface ##########################################
	
	public JenaEngine() {
		initAvailableProperties();
		if(ontoModel == null){
			ontoModel = loadLocalOntology();
		}
		if(inCache == null){
			inCache = new ArrayList<String>();
		}		
		if(model == null){
			model = ModelFactory.createMemModelMaker().openModel("Local_Cache", false);
			System.out.println("Loaded model of size: " + model.size());
		}
		if(!model.containsAll(ontoModel)){
			model.add(ontoModel);
			System.out.println("Added local Ontology, complete model size: " + model.size());
		}
		
	}	
	

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#getAvailableProperties()
	 */
	@Override
	public QueryProperties getAvailableProperties() {
		return qp;
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#getAvailableProperties(NEREngine.NamedEntity.EntityType)
	 */
	@Override
	public List<String> getAvailableProperties(EntityType type) {		
		return qp.get(type);
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List)
	 * Query properties with full set of available properties
	 */
	@Override
	public List<HashMap<String,HashMap<String, Integer>>> queryEntityProperties(List<NamedEntity> entities) {
		return queryEntityProperties(entities, qp);
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List, java.util.Properties)
	 * Query properties with custom set of properties
	 */
	@Override
	public List<HashMap<String,HashMap<String, Integer>>> queryEntityProperties(List<NamedEntity> entities,
			QueryProperties props) {
				
		List<HashMap<String,HashMap<String, Integer>>> result = new ArrayList<HashMap<String,HashMap<String, Integer>>>();
		
		for (NamedEntity namedEntity : entities) {
			result.add(queryEntity(namedEntity, props.get(namedEntity.getType())));
		}
		return result;
	}
	
	
	
	//######################### Private methods doing actual work ##########################################
	
	private OntModel loadLocalOntology() {
		OntModel m = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
		
		//Load local Ontology from file
		InputStream in = FileManager.get().open("data/UMA-SWT-HWS16.owl");
		try {
			m.read(in,null);
		} catch (Exception e) {
			System.out.println("Error during ontology import: " + e.getMessage());
		}		
		System.out.println("Loaded local Ontology of size: " + m.size());
		return m;
	}
	
	// ------- Query Entity: Top method orchastrating the query of a single entity
	private HashMap<String,HashMap<String, Integer>> queryEntity(NamedEntity entity, List<String> props){
		
		//Query Sources to build model
		handleSourceQueries(entity);
				
		//construct dictionary for properties
		Hashtable<String, String> propDic = prepareProperties(props);
		
		//Construct local query
		String lq = constructLocalQuery(entity, propDic);		
		
		//Execute Query
		return executeLocalQuery(lq,propDic);		
	}
	
	// ------- Handle queries for source: if entity not in cache -> query source(s) for this entity and add to local model (cache)
	private void handleSourceQueries(NamedEntity entity) {
		String cacheRef = entity.getType() + "_" + entity.getName();
		
		if(!inCache.contains(cacheRef)){
			//Query source
			System.out.println("Query DBPedia for " + entity.getType() + " " + entity.getName());
			model.add(new QueryDBPedia(entity).getModel());
			
			
			System.out.println("Complete model size: " + model.size());
			//System.out.println(model);
			//Update list of cached entities 
			inCache.add(entity.getType() + "_" + entity.getName());
		}else{
			System.out.println("Found in cache: " + entity.getType() + " " + entity.getName());
		}
		
	}
	
	// ------- Prepare properties -> assign indices for variable to technical property names
	private Hashtable<String, String> prepareProperties(List<String> props) {
		Hashtable<String, String> properties = new Hashtable<String, String>();
		int i = 1;
		for (String prop : props) {
			properties.put(prop, Integer.toString(i));
			i++;
		}
		return properties;
	}
		
	// ------- Construct local query: incl. dynamic list of properties
	private String constructLocalQuery(NamedEntity entity, Hashtable<String, String> props) {
		String queryString = "";
		String name = "";
		
	
		// ---- Derive values ----
		// rdf:type 
		String type = PREFIX;
		switch (entity.getType()) {
		case ORGANIZATION:
			type += "Organisation";
			break;
		case PERSON:
			type += "Person";
			break;
		case LOCATION:
			type += "Location";
			break;
		}
		// rdf:label Regex
		name = "(^.{0,5}\\\\s+|^)" + entity.getName() + "((\\\\s+.{0,5}$)|$)";
		
		
		// ---- Construct Query ----------
		// 1) Prefix (only basics and the own prefix for local queries)
		
		queryString += "PREFIX owl: <http://www.w3.org/2002/07/owl#>"
				+ " PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
				+ " PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ " PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ " PREFIX " + PREFIX + " <http://webprotege.stanford.edu/>"
		/*		+ " PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
				+ " PREFIX dc: <http://purl.org/dc/elements/1.1/>"
				+ " PREFIX dbo: <http://dbpedia.org/ontology/>"
				+ " PREFIX dbr: <http://dbpedia.org/resource/>"
				+ " PREFIX dbp: <http://dbpedia.org/property/>"
				+ " PREFIX dbpedia: <http://dbpedia.org/>"
				+ " PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"				
			*/;
		
		// 2) Select Clause
		queryString += " SELECT ?e ?l";
		for (Entry<String,String> entry: props.entrySet()) {
			queryString += " ?" + entry.getValue();
		}	
		// 3) Where Clause	
		queryString += " WHERE {"
				+ "?e rdf:type " + type + ". "
				+ "?e rdfs:label ?l."
			;
		// 3b) dynamic part
		queryString += " OPTIONAL { ";
		for (Entry<String,String> entry: props.entrySet()) {
			queryString += " ?e " + PREFIX + entry.getKey() + " ?" + entry.getValue() + ".";
		}
		queryString += " }";
		// 3c) Filter
		queryString += " FILTER(regex(?l,'" + name + "') && LANGMATCHES(LANG(?l), 'en'))";
		queryString += "}"; 
		
		return queryString;
	}
	
	// ------- Handle local query execution
	private HashMap<String,HashMap<String, Integer>> executeLocalQuery(String query, Hashtable<String, String> propDic){
		//Result structure (PropertyKey,(Value,Count))
		HashMap<String,HashMap<String, Integer>> result = new HashMap<String,HashMap<String, Integer>>();
		
		//label is always present and has special logic -> enhance property dictionary just for reading them 
		Hashtable<String, String> enhDic = new Hashtable<String, String> ();
		enhDic.putAll(propDic);
		enhDic.put("label", "l");
		enhDic.put("uri", "e");
		
		//Construct model
		Model cmodel = constructModel();		
		
		//Parse Query
		//System.out.println(query);
		Query q = QueryFactory.create(query); 
		//System.out.println(q);
		
		//Execute Query
		QueryExecution qe = QueryExecutionFactory.create(q, cmodel);
		ResultSet RS = qe.execSelect();
		
		//Parse Result of Query
		while (RS.hasNext()) {
			QuerySolution tuple = RS.next();
			handleQueryTuple(tuple, enhDic, result);
		}
		System.out.println("Result rows (local): " + RS.getRowNumber());
		qe.close();
		
		return result;	
	}
	
	
	// ------- Construct model: load own Ontology + queried model(s) -> Inference
	private Model constructModel() {		
		
		//Combine model(s)
		//ontoModel.add(model); -> done in constructor
		
		//Apply Inference on combination
		InfModel infModel = ModelFactory.createInfModel( ReasonerRegistry.getOWLReasoner(), model);
		
		return infModel;
	}
	
	// ------- Parse Tuple of local query result: TODO refine output structure  
	private void handleQueryTuple(QuerySolution tuple,
			Hashtable<String, String> propDic, HashMap<String,HashMap<String, Integer>> result) {
		String v = "";
		String k = "";
		HashMap<String, Integer> tempMap = new HashMap<String, Integer>();
		
		//handle dynamic properties
		for (Entry<String,String> entry: propDic.entrySet()) {
			tempMap = new HashMap<String, Integer>();
			v = new String();
			k = new String(); 
			k = entry.getKey();
			if(tuple.contains(entry.getValue())){
				v = tuple.get(entry.getValue()).toString();
				if(!result.containsKey(k)){
					//key new -> add key, value with count 1
					tempMap.put(v, 1);
					result.put(k, tempMap);
				}
				else if(!result.get(k).containsKey(v)){
					//key existing, but new value -> add new value with count 1
					result.get(k).put(v, 1);					
				}else{
					//key and value existing -> increment counter					
					result.get(k).replace(v, result.get(k).get(v)+1);
				}
				
			}
		}
		
	}


	// ------- init available Properties: TODO: derive from Ontology instead of fixed list
	@SuppressWarnings("serial")
	private void initAvailableProperties(){
		this.qp = new QueryProperties();
		//Test:
		List<String> props = new ArrayList<String>() {{
		    add("homepage");
		    add("foundedBy");
		    add("depiction");
		    add("isPrimaryTopicOf");
		}};				
		this.qp.put(EntityType.LOCATION, props);
		this.qp.put(EntityType.ORGANIZATION, props);
		this.qp.put(EntityType.PERSON, props);
	}
	
	
	// #################################### TEST SECTION #################################################
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		//TEST
		String text = "This is a test to identify SAP in Walldorf with Hasso Plattner as founder.";
		runtest(text);
		
		//2nd TEST (Cache)
		text = "This is a test to identify if Walldorf is in cache but Heidelberg has to be queried";
		runtest(text);
		
		
		/*
		NamedEntity ne = new NamedEntity("SAP", EntityType.ORGANIZATION);
		List<NamedEntity> list = new ArrayList<NamedEntity>();
		list.add(ne);
		
		JenaEngine je = new JenaEngine();
		System.out.println(je.queryEntityProperties(list));
		*/
	}

	private static void runtest(String text) {
		// 1) NER
		List<NamedEntity> list = CoreNLPEngine.getInstance().getEntitiesFromText(text);
		System.out.println("Result NER:");
		for (NamedEntity entity : list) {
	        System.out.println(entity.getType() + ": " + entity.getName());
		}
		
		
		// 2) Retrieve LOD information
		System.out.println("Result LOD:");
		JenaEngine je = new JenaEngine();
		for (HashMap<String,HashMap<String, Integer>> e : je.queryEntityProperties(list)){
			for (String key : e.keySet()) {
				System.out.println(key + ": " + e.get(key));
			}
			
		}		
	}
}
