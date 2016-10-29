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
import java.util.concurrent.TimeUnit;

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
import QueryEngine.BackgroundSourceQueryHandler.Source;


/** 
 * @author Sascha Ulbrich
 *
 */
public class JenaEngine implements QueryEngine {
	private QueryProperties qp;
	private static Model model;
	private static InfModel infModel;
	private static OntModel ontoModel;
	private static List<String> inCache;
	private static final String PREFIX = ":";
	private static Boolean modelChanged = false;
	
	//######################### Public methods: Interface ##########################################
	
	public JenaEngine() {		
		if(ontoModel == null){
			ontoModel = loadLocalOntology();
		}
		if(inCache == null){
			inCache = new ArrayList<String>();
		}		
		if(model == null){
			//That Memory Model doesn't work as expected -> only in memory during JVM lifetime -> restart: no model anymore
			model = ModelFactory.createMemModelMaker().openModel("LocalCache", false);
			System.out.println("Loaded model of size: " + model.size());
			modelChanged = true;
		}
//		if(!model.containsAll(ontoModel)){
//			model.add(ontoModel);
//			System.out.println("Added local Ontology, complete model size: " + model.size());
//		}
		initAvailableProperties();		
	}	
	

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#getAvailableProperties()
	 */
	@Override
	public QueryProperties getAvailableProperties() {
		//If properties manipulated later -> by reference is bad -> deep copy needed
		//http://stackoverflow.com/questions/31864727/how-do-i-make-a-copy-of-java-util-properties-object
		QueryProperties ext_qp = new QueryProperties();
		for (EntityType et : EntityType.values()) {
			ext_qp.put(et, getAvailableProperties(et));
		}
		return ext_qp;
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#getAvailableProperties(NEREngine.NamedEntity.EntityType)
	 */
	@Override
	public List<String> getAvailableProperties(EntityType type) {
		//same issue as above -> deep copy to avoid return by reference
		List<String> ext_list = new ArrayList<String>();
		ext_list.addAll(qp.get(type));
		return ext_list;
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
		
		//Query Sources to build model
		handleParallelSourceQueries(entities);
		
		//Query local model		
		return handleLocalQueries(entities, props);
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

	private void handleParallelSourceQueries(List<NamedEntity> entities) {
		//Initialize HashMaps
		HashMap<EntityType, List<String>> queryEntities = new HashMap<EntityType, List<String>> ();
		HashMap<EntityType, List<String>> inQuery = new HashMap<EntityType, List<String>> ();
		for (EntityType et : EntityType.values()) {
			queryEntities.put(et,new ArrayList<String>());
			inQuery.put(et,new ArrayList<String>());
		}
		
		//Determine which entities to query per entity type				
		for (NamedEntity entity : entities) {
			String cacheRef = entity.getType() + "_" + entity.getName();
			if(!inCache.contains(cacheRef) && !inQuery.get(entity.getType()).contains(cacheRef)){
				//Has to be add to query
				queryEntities.get(entity.getType()).add(entity.getName());
				//Keep track which entities are tracked
				inQuery.get(entity.getType()).add(cacheRef);
			}else{
				System.out.println("Found in cache: " + entity.getType() + " " + entity.getName());
			}			
		}
		
		//Query sources in parallel per entity type if requested
		//(without filter on entity type queries get to large -> aborted)
		System.out.println("Start load from sources...");
		Long start = System.nanoTime();
		ThreadGroup group = new ThreadGroup( entities.toString() );
		for (EntityType et : queryEntities.keySet()) {
			if(!queryEntities.get(et).isEmpty()){
				//DBPedia
				new BackgroundSourceQueryHandler(group, Source.DBPedia, et, queryEntities.get(et), inQuery.get(et)).start();
			}
		}
		
		//Wait till all are finished and derive model
		Model resModel;
		try {
			BackgroundSourceQueryHandler[] threads = new BackgroundSourceQueryHandler[group.activeCount()];
			group.enumerate(threads);
			for (int i = 0; i < threads.length; i++) {
				threads[i].join();
				resModel = threads[i].getResultModel(); 
				if(resModel != null && resModel.size() > 0){
					model.add(resModel);
					modelChanged = true;
					//Update Cache: TODO: implement Source specific cache
					for(String ref : threads[i].getCacheRef()){
						inCache.add(ref);
					}
				}
			}
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
		}
		System.out.println("Load of Sources finished. Model size: " + model.size()+ "; Time: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) + "ms");
	}

	private List<HashMap<String, HashMap<String, Integer>>> handleLocalQueries(List<NamedEntity> entities, QueryProperties props) {
		List<HashMap<String,HashMap<String, Integer>>> result = new ArrayList<HashMap<String,HashMap<String, Integer>>>();
		
		//Construct context model 
		Model cmodel = constructModel(entities);
		
		//query each entity separately on local model				
		for (NamedEntity e : entities) {
			//construct dictionary for entity type specific properties 
			Hashtable<String, String> propDic = prepareProperties(props.get(e.getType()));
			
			//Construct local query
			String lq = constructLocalQuery(e, propDic);		
		
			//Execute Query
			result.add(executeLocalQuery(lq,propDic,cmodel));	
		}
		return result;
		
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
		String type = deriveEntityClasses(entity.getType());
		
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
		// 3a) static part
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
		queryString += " FILTER(regex(?l,'" + name + "') && (LANG(?l) = '' || LANGMATCHES(LANG(?l), 'en')))";
		
		// 3d) String conversion
		queryString += " BIND( str(?l) as ?label )"
				+ "}"; 
		
		return queryString;
	}
	
	private String deriveEntityClasses(EntityType et) {
		String type = PREFIX;
		switch (et) {
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
		return type;
	}


	// ------- Handle local query execution
	private HashMap<String,HashMap<String, Integer>> executeLocalQuery(String query, Hashtable<String, String> propDic, Model m){
		//Result structure (PropertyKey,(Value,Count))
		HashMap<String,HashMap<String, Integer>> result = new HashMap<String,HashMap<String, Integer>>();
		
		//label is always present and has special logic -> enhance property dictionary just for reading them 
		Hashtable<String, String> enhDic = new Hashtable<String, String> ();
		enhDic.putAll(propDic);
		enhDic.put("label", "label");
		enhDic.put("uri", "e");
		
		
		//Parse Query
		//System.out.println(query);
		Query q = QueryFactory.create(query); 
		//System.out.println(q);
		
		//Execute Query
		Long start = System.nanoTime();
		QueryExecution qe = QueryExecutionFactory.create(q, m);
		ResultSet RS = qe.execSelect();
		
		
		//Parse Result of Query
		while (RS.hasNext()) {
			QuerySolution tuple = RS.next();
			handleQueryTuple(tuple, enhDic, result);
		}
		System.out.println("Queried local model in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) + "ms, size: " + RS.getRowNumber());
		qe.close();
		
		return result;	
	}
	
	
	// ------- Construct model: load own Ontology + queried model(s) and do some Inference
	private Model constructModel(List<NamedEntity> entities) {	
		//get the basic model, enhance with ontology, do inference
		//Reasoner takes to much time! -> OWLMicro is to small, according to https://jena.apache.org/documentation/inference
		//TODO: find alternative Reasoner which can deal with equality axioms
		//Test: does the reasoning is applied automatically if model is enhanced? 
		Long start = System.nanoTime();
		if(modelChanged){
			//it is much more efficient to reason on basic statement than do reasoning on a previously inferred model
			infModel = ModelFactory.createInfModel( ReasonerRegistry.getOWLReasoner(), ModelFactory.createUnion(model, ontoModel));
			modelChanged = false;
		}		

		System.out.println("Infered Model size: " + model.size() + "; Time: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) + "ms");
		
		//TODO: try to identify correct entities in context!
		// get relevant subspace of potential entities
		
		// count (indirect) relations between entities
		
		// choose most relevant entities 
		
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


	// ------- init available Properties via local Ontology
	private void initAvailableProperties(){
		this.qp = new QueryProperties();
		
		/*
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
		*/
		
		Model m = ontoModel;
		List<String> props;
		for (EntityType et : EntityType.values()) {
			props = new ArrayList<String>();
			
			String type = deriveEntityClasses(et);
			
			//Query available properties from local Ontology
			String queryString = " PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
					+ " PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
					+ " PREFIX " + PREFIX + " <http://webprotege.stanford.edu/>"
					+ " SELECT ?p ?label"
					+ " WHERE {"
					+ " ?p rdfs:domain "+ type +"."
					+ " ?p rdf:type rdf:Property."
					+ " ?p rdfs:label ?l."
					+ " FILTER(LANG(?l) = '' || LANGMATCHES(LANG(?l), 'en'))"
					+ " bind( str(?l) as ?label )"
							+ "}"
					; 
			Query query = QueryFactory.create(queryString); 
			//System.out.println(query);
			QueryExecution qe = QueryExecutionFactory.create(query, m); 
			ResultSet results = qe.execSelect(); 
			while(results.hasNext()) {  
				QuerySolution sol = results.next();  
				String s = sol.get("label").toString();
				props.add(s);
			}			
			qe.close();
			this.qp.put(et, props);
		}
	}
	
	
	// #################################### TEST SECTION #################################################
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//  ---- End-to-End Test
		JenaEngine je = new JenaEngine();
		
		// 1st simple test with all entity types
		String text = "This is a test to identify SAP in Walldorf with Hasso Plattner as founder.";
		runtest(text,je.getAvailableProperties());
		
		// 2nd TEST (just hit the cache)
		text = "Just testing how caching works Hasso Plattner from Walldorf.";
		runtest(text,je.getAvailableProperties());
		
		// 3rd TEST (Cache and remove property)
		text = "This is a test to identify if Walldorf is in cache but Heidelberg has to be queried";
		
		QueryProperties qp = je.getAvailableProperties();				
		qp.get(EntityType.LOCATION).remove("depiction");
		
		runtest(text,qp);
		
		/*
		// ----- Simple test without NER
		NamedEntity ne = new NamedEntity("SAP", EntityType.ORGANIZATION);
		List<NamedEntity> list = new ArrayList<NamedEntity>();
		list.add(ne);
		
		JenaEngine je = new JenaEngine();
		QueryProperties qp = je.getAvailableProperties();				
		qp.get(EntityType.LOCATION).remove("depiction");
		
		System.out.println(je.getAvailableProperties());
		System.out.println(qp);
		System.out.println(je.queryEntityProperties(list,qp));
		*/		
		
		/*
		// ----- Test property derivation
		JenaEngine je = new JenaEngine();
		System.out.println(je.getAvailableProperties(EntityType.LOCATION));
		*/
	}

	private static void runtest(String text, QueryProperties qp) {
		// 1) NER
		List<NamedEntity> list = CoreNLPEngine.getInstance().getEntitiesFromText(text);
		System.out.println("Result NER:");
		for (NamedEntity entity : list) {
	        System.out.println(entity.getType() + ": " + entity.getName());
		}
		
		
		// 2) Retrieve LOD information
		System.out.println("Result LOD:");
		JenaEngine je = new JenaEngine();
		for (HashMap<String,HashMap<String, Integer>> e : je.queryEntityProperties(list, qp)){
			for (String key : e.keySet()) {
				System.out.println(key + ": " + e.get(key));
			}
			
		}		
	}
}
