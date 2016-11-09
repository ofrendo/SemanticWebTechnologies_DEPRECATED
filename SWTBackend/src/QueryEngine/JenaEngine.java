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


/** 
 * @author Sascha Ulbrich
 *
 */
public class JenaEngine implements QueryEngine {	
	private static Model model;
	private static InfModel infModel;
	private static OntModel ontoModel;
	private static List<NamedEntity> inCache;
	private static final String PREFIX = ":";
	private static Boolean modelChanged = false;
	private static QueryProperties availableProperties;
	
	private List<NamedEntity> entities;
	private QueryProperties qp;
	
	
	
	//######################### Public methods: Interface ##########################################
	
	public JenaEngine() {		
		if(ontoModel == null){
			ontoModel = loadLocalOntology();
		}
		if(inCache == null){
			inCache = new ArrayList<NamedEntity>();
		}		
		if(model == null){
			//That Memory Model doesn't work as expected -> only in memory during JVM lifetime -> restart: no model anymore
			model = ModelFactory.createMemModelMaker().openModel("LocalCache", false);
			System.out.println("Loaded model of size: " + model.size());
			modelChanged = true;
		}
		if(availableProperties == null){
			availableProperties = readAvailableProperties();
		}		
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
		ext_list.addAll(availableProperties.get(type));
		return ext_list;
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List)
	 * Query properties with full set of available properties
	 */
	@Override
	public List<NamedEntity> queryEntities(List<NamedEntity> entities) {
		return queryEntities(entities, availableProperties);
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List, java.util.Properties)
	 * Query properties with custom set of properties
	 */
	@Override
	public List<NamedEntity> queryEntities(List<NamedEntity> entities,
			QueryProperties props) {
		if(props == null){
			props = availableProperties;
		}
		
		
		this.entities = entities;
		this.qp = props;
		
		//Query Sources to build model
		handleParallelSourceQueries();
		
		//Query local model		
		handleLocalQueries();
		
		//TODO copy?
		return entities;
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

	private void handleParallelSourceQueries() {
		//Initialize HashMaps
		HashMap<EntityType, List<NamedEntity>> queryEntities = new HashMap<EntityType, List<NamedEntity>> ();
		for (EntityType et : EntityType.values()) {
			queryEntities.put(et,new ArrayList<NamedEntity>());
		}
		
		//Determine which entities to query per entity type				
		for (NamedEntity entity : entities) {
			if(!inCache.contains(entity) && !queryEntities.get(entity.getType()).contains(entity)){
				//Has to be add to query
				queryEntities.get(entity.getType()).add(entity);
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
				new BackgroundSourceQueryHandler(group, QuerySource.Source.DBPedia, et, queryEntities.get(et)).start();
				//LinkedMDB
				//new BackgroundSourceQueryHandler(group, QuerySource.Source.LinkedMDB, et, queryEntities.get(et)).start();
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
					//Update Cache: TODO: implement Source specific cache?
					for(NamedEntity e : threads[i].getEntities()){
						inCache.add(e);
					}
				}
			}
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
		}
		System.out.println("Load of Sources finished. Model size: " + model.size()+ "; Time: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) + "ms");
	}

	private void handleLocalQueries() {
		//List<HashMap<String,HashMap<String, Integer>>> result = new ArrayList<HashMap<String,HashMap<String, Integer>>>();
		
		//Construct context model 
		Model cmodel = constructModel();
		
		//Try to identify correct entities in context!
		//-> count (indirect) relations between entities and choose most relevant entities
		//HashMap<String,String>  relevantURIs = deriveRelevantURIs(entities, cmodel);
		//System.out.println("Relevant URIs in Context: " + relevantURIs);
		deriveRelevantURIs(cmodel);
		
		
				
		//query each entity separately on local model				
		for (NamedEntity e : entities) {
			//construct dictionary for entity type specific properties 
			Hashtable<String, String> propDic = prepareProperties(qp.get(e.getType()));
			
			//Construct local query
			String lq = constructLocalQuery(propDic, e.getURI());		
		
			//Execute Query
			//result.add(executeLocalQuery(lq,propDic,cmodel, e));
			executeLocalQuery(lq,propDic,cmodel, e);	
		}
		//return result;
		
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
	private String constructLocalQuery(Hashtable<String, String> props, String uri) {
		String queryString = "";
		String res = "<" + uri + ">";
		
		// ---- Construct Query ----------
		// 1) Prefix (only basics and the own prefix for local queries)
		
		queryString += "PREFIX owl: <http://www.w3.org/2002/07/owl#>"
				+ " PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
				+ " PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ " PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ " PREFIX " + PREFIX + " <http://webprotege.stanford.edu/>"
				;
		
		// 2) Select Clause
		queryString += " SELECT ?l";
		for (Entry<String,String> entry: props.entrySet()) {
			queryString += " ?" + entry.getValue();
		}	
		// 3) Where Clause	
		// 3a) static part
		queryString += " WHERE {"
				+ res + " rdfs:label ?l."
			;
		// 3b) dynamic part
		//queryString += " OPTIONAL { ";
		for (Entry<String,String> entry: props.entrySet()) {
			queryString += " OPTIONAL { " + res + " " + PREFIX + entry.getKey() + " ?" + entry.getValue() + ". }";
		}
		//queryString += " }";
		// 3c) Filter
		queryString += " FILTER(LANG(?l) = '' || LANGMATCHES(LANG(?l), 'en'))";
		
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
	private void executeLocalQuery(String query, Hashtable<String, String> propDic, Model m, NamedEntity ne){
		//Result structure (PropertyKey,(Value,Count))
		//HashMap<String,HashMap<String, Integer>> result = new HashMap<String,HashMap<String, Integer>>();
		
		//label is always present and has special logic -> enhance property dictionary just for reading them 
		Hashtable<String, String> enhDic = new Hashtable<String, String> ();
		enhDic.putAll(propDic);
		enhDic.put("label", "label");
		
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
			//handleQueryTuple(tuple, enhDic, result);
			handleQueryTuple(tuple, enhDic, ne);
		}
		System.out.println("Queried local model in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) + "ms, size: " + RS.getRowNumber());
		qe.close();
		
		//return result;	
	}
	
	
	// ------- Construct model: load own Ontology + queried model(s) and do some Inference
	private Model constructModel() {	
		//get the basic model, enhance with ontology, do inference
		//Reasoner takes to much time, but OWLMicro seems to work but could be to simple ... https://jena.apache.org/documentation/inference
		Long start = System.nanoTime();
		if(modelChanged){
			//it is much more efficient to reason on basic statement than do reasoning on a previously inferred model
			infModel = ModelFactory.createInfModel( ReasonerRegistry.getOWLMicroReasoner(), ModelFactory.createUnion(model, ontoModel));
			modelChanged = false;
		}		

		System.out.println("Infered Model size: " + model.size() + "; Time: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) + "ms");
				
		//TODO: Derive relevant subspace model based on identified URIs? -> Describe of URIs? 

		return infModel;
	}
	
	private void deriveRelevantURIs(Model m) {
		//HashMap<String,String> nameToResource = new HashMap<String, String>();
		
		for (NamedEntity e : entities) {	
			
			// ---- Derive values ----
			// rdf:type 
			String type = deriveEntityClasses(e.getType());
			
			// rdf:label Regex
			String name = e.getRegexName();
			
			//construct filter part (same for every part of the union)
			String filter = " LANGMATCHES(LANG(?l1), 'en')"
					+ " && LANGMATCHES(LANG(?l2), 'en')"
					+ " && regex(?l1,'" + name + "')"
					;
			
			//Add context info to filter, if context available
			if(entities.size() > 1){
				//derive the other entities in the context 
				String others = "";
				for (NamedEntity e2 : entities) {
					if(e2.getName() != e.getName()){
						if(others != ""){
							others += " || ";
						}
						others += "regex(?l2,'" + name + "')";
					}
				}
				//add to filter
				filter += " && ( " + others + " ) ";
			}
					
			
			// Union part 1: direct relations
			String part1 = "SELECT ?e1 ?p1  WHERE {"
					+ " ?e1 ?p1 ?e2."
					+ " ?e1 rdfs:label ?l1."
					+ " ?e2 rdfs:label ?l2."
					+ " ?e1 rdf:type " + type + "."
					+ " FILTER ( " + filter + " ) }";
			
			// Union part 2: indirect relations
			String part2 = "SELECT ?e1 ?p1  WHERE {"
					+ " ?e1 ?p1 ?o."
					+ " ?e2 ?p2 ?o."
					+ " ?e1 rdfs:label ?l1."
					+ " ?e2 rdfs:label ?l2."
					+ " ?e1 rdf:type " + type + "."
					+ " FILTER (" + filter + ")}";
			
			// Complete Query
			String queryString = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
					+ " PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
					+ " PREFIX " + PREFIX + " <http://webprotege.stanford.edu/>"
					+ " SELECT ?e1 (count(?p1) as ?pCount) { { "
					+ part1
					+ " } UNION { " 
					+ part2
					+ " } } GROUP BY ?e1"
					; 
			//System.out.println(queryString);
			Query query = QueryFactory.create(queryString); 
			//System.out.println(query);
			QueryExecution qe = QueryExecutionFactory.create(query, m); 
			ResultSet results = qe.execSelect(); 
			int max = 0;
			String value = "";
			while(results.hasNext()) {  
				QuerySolution sol = results.next(); 
				//System.out.println(sol);
				if( sol.contains("pCount") && sol.get("pCount").asLiteral().getInt() > max){
					max = sol.get("pCount").asLiteral().getInt();
					value = sol.get("e1").toString();
				}
			}
			//nameToResource.put(e.getName(), value);
			e.setURI(value);
			qe.close();
		}
		//return nameToResource;
	}


//	// ------- Parse Tuple of local query result: TODO refine output structure  
//	private void handleQueryTuple(QuerySolution tuple,
//		Hashtable<String, String> propDic, HashMap<String,HashMap<String, Integer>> result) {
//		String v = "";
//		String k = "";
//		HashMap<String, Integer> tempMap = new HashMap<String, Integer>();
//		
//		//handle dynamic properties
//		for (Entry<String,String> entry: propDic.entrySet()) {
//			tempMap = new HashMap<String, Integer>();
//			v = new String();
//			k = new String(); 
//			k = entry.getKey();
//			if(tuple.contains(entry.getValue())){
//				v = tuple.get(entry.getValue()).toString();
//				if(!result.containsKey(k)){
//					//key new -> add key, value with count 1
//					tempMap.put(v, 1);
//					result.put(k, tempMap);
//				}
//				else if(!result.get(k).containsKey(v)){
//					//key existing, but new value -> add new value with count 1
//					result.get(k).put(v, 1);					
//				}else{
//					//key and value existing -> increment counter					
//					result.get(k).replace(v, result.get(k).get(v)+1);
//				}
//				
//			}
//		}
//		
//	}
	
	// ------- Parse Tuple of local query result: based on Entity  
		private void handleQueryTuple(QuerySolution tuple,
			Hashtable<String, String> propDic, NamedEntity ne) {
			String v = "";
			String k = "";
			//HashMap<String, Integer> tempMap = new HashMap<String, Integer>();
			
			//handle dynamic properties
			for (Entry<String,String> entry: propDic.entrySet()) {
				//tempMap = new HashMap<String, Integer>();
				v = new String();
				k = new String(); 
				k = entry.getKey();
				if(tuple.contains(entry.getValue())){
					v = tuple.get(entry.getValue()).toString();
					ne.addPropertyValue(k, v, 1);
//					if(!result.containsKey(k)){
//						//key new -> add key, value with count 1
//						tempMap.put(v, 1);
//						result.put(k, tempMap);
//					}
//					else if(!result.get(k).containsKey(v)){
//						//key existing, but new value -> add new value with count 1
//						result.get(k).put(v, 1);					
//					}else{
//						//key and value existing -> increment counter					
//						result.get(k).replace(v, result.get(k).get(v)+1);
//					}
					
				}
			}
			
		}


	// ------- read available Properties via local Ontology
	private QueryProperties readAvailableProperties(){
		QueryProperties queryprops = new QueryProperties();
		
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
		
		Model m = ModelFactory.createRDFSModel(ontoModel);
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
			queryprops.put(et, props);
		}
		return queryprops;
	}
	
	
	// #################################### TEST SECTION #################################################
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		//  ---- End-to-End Test
		JenaEngine je = new JenaEngine();
		
		// 1st simple test with all entity types
		String text = "This is a test to identify SAP in Walldorf with H. Plattner as founder.";
			runtest(text,null);
		
		// 2nd TEST (just hit the cache)
		text = "Just testing how caching works for H. Plattner from Walldorf.";
		runtest(text,null);
		
		// 3rd TEST (Cache and remove property)
		text = "This is a test to identify if Walldorf is in cache but Heidelberg has to be queried";
		QueryProperties qp = je.getAvailableProperties();				
		qp.get(EntityType.LOCATION).remove("depiction");
		runtest(text,qp);
		
		/*
		// 4th TEST: Heikos example
		//some trouble with special characters
		text = "Zu den verdaechtigen gehört Walter K., ein ehemaliger Fußballprofi aus Stuttgart. "
				+ "K. spielte zweitweise sogar in der deutschen Nationalmannschaft, nach seiner Karrier "
				+ "betrieb er für die Allianz ein Versicherungsbüro.";
		runtest(text,je.getAvailableProperties());
		
		// 4th TEST: Heikos example in english
		//some trouble with special characters rertieved through stuttgart
		text = "The suspect Walter K. is a former soccer player from Stuttgart. "
				+ "After his carrer he had a insurance office for Allianz.";
		runtest(text,je.getAvailableProperties());
		*/

		
		// ----- Simple test without NER
//		List<NamedEntity> list = new ArrayList<NamedEntity>();
//		list.add(new NamedEntity("SAP", EntityType.ORGANIZATION));
//		list.add(new NamedEntity("Walldorf", EntityType.LOCATION));
//		
//		
//		JenaEngine je = new JenaEngine();
//		QueryProperties qp = je.getAvailableProperties();				
//		qp.get(EntityType.LOCATION).remove("depiction");
//		//qp.get(EntityType.ORGANIZATION).remove("distributerOf");
//
//		System.out.println(je.getAvailableProperties());
//		System.out.println(qp);
//		System.out.println(je.queryEntityProperties(list,qp));
				
		
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
		for (NamedEntity e : je.queryEntities(list, qp)){
			System.out.println(e);			
		}		
	}
}
